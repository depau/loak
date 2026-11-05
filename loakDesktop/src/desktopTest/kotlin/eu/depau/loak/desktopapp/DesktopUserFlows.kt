@file:OptIn(ExperimentalTestApi::class)

package eu.depau.loak.desktopapp

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import eu.depau.loak.App
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.data.database.DownloadDatabase
import eu.depau.loak.di.desktopDataDir
import eu.depau.loak.di.initKoin
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.uitest.LargeLibraryFlows
import eu.depau.loak.uitest.UserFlows
import dev.nucleusframework.rodio.RodioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.koin.core.context.stopKoin
import org.junit.rules.TestName
import org.junit.rules.Timeout
import java.io.File
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/** The user flows on the desktop app, at a window size of [width]×[height]. */
abstract class DesktopUserFlows(private val width: Int, private val height: Int) : UserFlows() {
	@get:Rule
	val name = TestName()

	private var player: MediaPlayerViewModel? = null

	// CI runners get a virtual one (build.yml); Windows on ARM has none
	override fun playbackProgress() = if (hasAudioOutput) player?.uiState?.value?.progress else null

	// a hung event thread blocks the test API itself, so fail from outside it
	@get:Rule
	val timeout: Timeout = Timeout.seconds(180)

	override fun runApp(flow: suspend ComposeUiTest.() -> Unit) =
		runDesktopApp(width, height, "${this::class.simpleName}.${name.methodName}", { player = it }, flow)
}

/**
 * Starts the real desktop app as a fresh install in a [width]×[height] window, muted, runs [flow]
 * against it and tears it down. A failed assertion leaves screenshots named [failureName].
 */
fun runDesktopApp(
	width: Int,
	height: Int,
	failureName: String,
	onPlayer: (MediaPlayerViewModel) -> Unit = {},
	flow: suspend ComposeUiTest.() -> Unit,
) {
	// Nucleus registers its own Main dispatcher (the Tao main thread, or a fallback
	// thread outside a Tao window); the test drives Compose on the EDT, so Main must be
	// it, from before the app starts until the test has disposed the composition
	Dispatchers.setMain(Dispatchers.Swing)
	stopKoin() // after a flow that timed out, its app is still around
	val koin = initKoin()
	val player = koin.koin.get<MediaPlayerViewModel>().also(onPlayer)
	player.setVolume(0f) // flows play songs: keep them silent
	try {
		runDesktopComposeUiTest(width, height) {
			setContent { App() }
			try {
				flow()
			} catch (e: AssertionError) {
				// only when the app is alive: after a crash, capturing hangs the test
				screenshot(failureName)
				throw e
			}
		}
	} finally {
		// the player is a Koin singleton, never cleared outside tests: stop its
		// coroutines first, and resolve the databases before Koin shuts down (they
		// are plain `single`s, not closed by onClose). stopKoin() then runs every
		// onClose (cancelling the SyncManager/DownloadManager/AudioStore scopes), and
		// only after that is the database closed — otherwise a late periodic sync from
		// the previous flow "Database is closed" asynchronously into the next test's
		// uncaught handler
		val cacheDatabase = runCatching { koin.koin.get<CacheDatabase>() }.getOrNull()
		val downloadDatabase = runCatching { koin.koin.get<DownloadDatabase>() }.getOrNull()
		ViewModelStore().apply { put("player", player); clear() }
		stopKoin()
		// the screens' cancelled queries can still be running (a large library's take a while):
		// closing the databases under them fails them into the next test's uncaught handler too
		awaitNoDatabaseWork()
		runCatching { cacheDatabase?.close() }
		runCatching { downloadDatabase?.close() }
		Dispatchers.resetMain()
		// a fresh install for the next flow
		desktopDataDir.listFiles()?.forEach { it.deleteRecursively() }
	}
}

/** Waits (10 s at most) until no thread has been in Room for a few polls in a row. */
private fun awaitNoDatabaseWork() {
	val deadline = TimeSource.Monotonic.markNow() + 10.seconds
	var quietPolls = 0
	while (quietPolls < 3 && deadline.hasNotPassedNow()) {
		val busy = Thread.getAllStackTraces().values.any { trace ->
			trace.any { it.className.startsWith("androidx.room3.") }
		}
		quietPolls = if (busy) 0 else quietPolls + 1
		Thread.sleep(20)
	}
}

// one picture per window: the screen, then any open popup or dialog
private fun ComposeUiTest.screenshot(name: String) = runCatching {
	val dir = File(System.getProperty("loak.uitest.failures") ?: "build/ui-test-failures").apply { mkdirs() }
	val roots = onAllNodes(isRoot())
	for (i in roots.fetchSemanticsNodes().indices) {
		val suffix = if (i == 0) "" else ".$i"
		val file = File(dir, "$name$suffix.png")
		ImageIO.write(roots[i].captureToImage().toAwtImage(), "png", file)
		println("Screenshot of the failure: $file")
	}
}

private val hasAudioOutput by lazy { runCatching { RodioPlayer().close() }.isSuccess }

class DesktopUserFlowsWide : DesktopUserFlows(1280, 800)

class DesktopUserFlowsPhone : DesktopUserFlows(412, 892)

/** The large-library flows on the desktop app, in a wide window. */
class DesktopLargeLibraryFlows : LargeLibraryFlows() {
	@get:Rule
	val name = TestName()

	// the first sync of the large library takes a while
	@get:Rule
	val timeout: Timeout = Timeout.seconds(600)

	override fun runApp(flow: suspend ComposeUiTest.() -> Unit) =
		runDesktopApp(1280, 800, "${this::class.simpleName}.${name.methodName}", flow = flow)
}
