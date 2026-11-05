@file:OptIn(ExperimentalTestApi::class)

package eu.depau.loak.desktopapp

import androidx.compose.ui.test.MouseInjectionScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.uitest.click
import eu.depau.loak.uitest.description
import eu.depau.loak.uitest.logIn
import eu.depau.loak.uitest.openPlayer
import eu.depau.loak.uitest.openTab
import eu.depau.loak.uitest.pauseIfPlaying
import eu.depau.loak.uitest.tab
import eu.depau.loak.uitest.waitFor
import eu.depau.loak.uitest.waitForText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.rules.TestName
import org.junit.rules.Timeout
import org.koin.core.context.GlobalContext
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Drags rows of the real queue with a mouse, several moves a frame as a Mac's mouse or trackpad
 * sends them, in a queue of 50 scrolled to the playing song: the Up next sheet of a phone-sized
 * window and the side pane of a wide one. Every frame, the held row stays under the pointer and
 * every other row stays put or steps exactly one row: nothing else may move the list.
 */
abstract class QueueReorderTest(private val width: Int, private val height: Int) {
	@get:Rule
	val name = TestName()

	@get:Rule
	val timeout: Timeout = Timeout.seconds(180)

	@Test
	fun slowDragDown() = drag(row = 2, moves = List(120) { 2f })

	@Test
	fun fastDragDown() = drag(row = 2, moves = List(30) { 10f }, perFrame = 4)

	@Test
	fun firstVisibleRowDown() = drag(row = 0, moves = List(90) { 3f })

	@Test
	fun intoTheFirstVisibleRow() = drag(row = 1, moves = List(30) { -2f })

	private val queueList = hasScrollToNodeAction() and hasAnyDescendant(description("Reorder"))

	/** The queue's rows, titled "Qnn …" by their place in the queue as built. */
	private val queueTitle = SemanticsMatcher("a queue row's title") { node ->
		node.config.getOrNull(SemanticsProperties.Text)?.any { TITLE.matches(it.text) } == true
	} and hasAnyAncestor(queueList)

	/** Visible rows, top to bottom: (number in the queue as built, top). */
	private fun ComposeUiTest.rows(): List<Pair<Int, Float>> =
		onAllNodes(queueTitle).fetchSemanticsNodes().map { node ->
			node.config[SemanticsProperties.Text].first().text.number() to node.boundsInRoot.top
		}.distinctBy { it.first }.sortedBy { it.second }

	/**
	 * On the UI thread, where the app's coroutines and recomposition run, as a real mouse's
	 * events do: from the test's thread, the gesture would write the drag's state off that thread.
	 */
	private fun ComposeUiTest.mouse(block: MouseInjectionScope.() -> Unit) =
		runOnUiThread { onRoot().performMouseInput(block) }

	private fun drag(row: Int, moves: List<Float>, perFrame: Int = 3) =
		runDesktopApp(width, height, "${this::class.simpleName}.${name.methodName}") {
			openQueue()
			val start = rows()
			val (heldNumber, heldTop) = start[row]
			// one row and the gap after it
			val pitch = start.zipWithNext { a, b -> b.second - a.second }.groupingBy { it }.eachCount().maxBy { it.value }.key
			val handle = onAllNodes(description("Reorder")).fetchSemanticsNodes().map { it.boundsInRoot }
				.first { heldTop + pitch / 2 in it.top..it.bottom }
			var pointer = handle.center
			val problems = mutableListOf<String>()
			mainClock.autoAdvance = false
			mouse { moveTo(pointer); press() }
			mainClock.advanceTimeByFrame()
			var before = rows().toMap()
			var grip: Float? = null
			moves.chunked(perFrame).forEachIndexed { frame, chunk ->
				for (dy in chunk) {
					pointer += Offset(0f, dy)
					mouse { moveTo(pointer) }
				}
				// real time too, for the player's update of the queue to reach the screen
				withContext(Dispatchers.Default) { delay(16) }
				mainClock.advanceTimeByFrame()
				val now = rows().toMap()
				val top = now[heldNumber]
				if (grip == null && top != null && abs(top - heldTop) > 1) grip = top - pointer.y
				if (grip != null && (top == null || abs(top - pointer.y - grip!!) > 4)) {
					problems += "frame $frame: held row at $top, pointer at ${pointer.y}"
				}
				val jumped = now.filterKeys { it != heldNumber && it in before }.filter { (number, y) ->
					val step = abs(y - before.getValue(number))
					step > 1 && abs(step - pitch) > 2
				}
				if (jumped.isNotEmpty()) problems += "frame $frame: rows moved off their slots: " +
					jumped.map { (number, y) -> "Q$number ${before.getValue(number)} → $y" }
				before = now
			}
			mouse { release() }
			repeat(30) { mainClock.advanceTimeByFrame() }
			mainClock.autoAdvance = true
			if (problems.isNotEmpty()) fail("The queue jumped while dragging Q$heldNumber:\n" + problems.joinToString("\n"))
			val order = GlobalContext.get().get<MediaPlayerViewModel>().uiState.value.queue.map { it.title.number() }
			assertTrue(order.indexOf(heldNumber) != heldNumber, "Q$heldNumber never moved: $order")
			val others = order.filter { it != heldNumber }
			assertTrue(others == others.sorted(), "the other songs changed order: $order")
		}

	/** Plays a 50-song queue from its 21st song and opens it, scrolled to that song as it opens. */
	private suspend fun ComposeUiTest.openQueue() {
		logIn()
		openTab("Library")
		click("Songs")
		waitForText("Signal Fire")
		val koin = GlobalContext.get()
		// the fake songs below must never reach the fixture server's saved queue
		koin.get<PreferenceManager>().queueSyncEnabled = false
		val library = withContext(Dispatchers.Default) { koin.get<SongRepository>().getAllSongs() }
			.sortedBy { it.title }
		// no covers: in this harness Coil sets its painters' state from a background thread, which
		// races recomposition into "Unsupported concurrent change during composition" mid-drag
		val queue = List(QUEUE_SIZE) { i ->
			val song = library[i % library.size]
			song.copy(
				id = if (i == PLAYING) song.id else "${song.id}~$i",
				title = "Q%02d %s".format(i, song.title),
				coverArtId = null
			)
		}
		withContext(Dispatchers.Main) { koin.get<MediaPlayerViewModel>().playNow(queue, PLAYING) }
		openPlayer(queue[PLAYING].title)
		pauseIfPlaying()
		click(tab("Up next")) // the phone's sheet, or the wide player's side pane
		waitFor(description("Reorder"))
		// the queue scrolls to the playing song once it's laid out
		withContext(Dispatchers.Default) { delay(2000) }
		waitForIdle()
	}

	private fun String.number() = substring(1, 3).toInt()

	private companion object {
		const val QUEUE_SIZE = 50
		const val PLAYING = 20
		val TITLE = Regex("Q\\d\\d .*")
	}
}

class QueueReorderTestPhone : QueueReorderTest(412, 892)

class QueueReorderTestWide : QueueReorderTest(1280, 800)
