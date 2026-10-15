package eu.depau.loak.uitest

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * How long a screen gets to show its content. Generous for slow CI emulators,
 * and short enough that a frozen screen fails the flow instead of the job.
 */
const val SCREEN_TIMEOUT_MS = 30_000L

private val REVEAL_AFTER = 2.seconds

/** The fixture server (NavidromeFixture in buildSrc): keep these in sync with it. */
object TestServer {
	const val DEFAULT_PORT = 45330
	const val USER = "loak"
	const val PASSWORD = "loak-ui-test"
	val url get() = "http://$testServerHost:${testServerPort ?: DEFAULT_PORT}"
}

/** The port the build started the fixture server on (-Ploak.uitest.port), if not the default. */
internal expect val testServerPort: Int?

/** The host the app reaches the fixture server's machine at (10.0.2.2 from an Android emulator). */
expect val testServerHost: String

fun text(value: String, substring: Boolean = false) = hasText(value, substring = substring)

fun description(value: String) = hasContentDescription(value)

/**
 * [matcher] inside an item's open sheet, not the screen behind it: a bottom sheet on
 * touch screens, a popup menu where a mouse is in use.
 */
fun inSheet(matcher: SemanticsMatcher) = matcher and hasAnyAncestor(
	SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle) or
		SemanticsMatcher.keyIsDefined(SemanticsProperties.IsPopup)
)

/** A navigation bar / rail item. */
fun tab(label: String) = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

/**
 * Waits until [condition] holds. Polls in real time, off the test's virtual clock, so
 * the app's network and database work progresses meanwhile: on the web's single thread
 * Compose's own `waitUntil` spins without ever yielding to them.
 */
suspend fun ComposeUiTest.await(
	what: () -> String,
	timeoutMillis: Long = SCREEN_TIMEOUT_MS,
	condition: () -> Boolean,
) {
	val deadline = TimeSource.Monotonic.markNow() + timeoutMillis.milliseconds
	while (!condition()) {
		if (deadline.hasPassedNow()) {
			throw AssertionError(
				// every root: an open popup or dialog is a root of its own
				"${what()} after ${timeoutMillis / 1000} s. On screen:\n" +
					onAllNodes(isRoot()).printToString(maxDepth = Int.MAX_VALUE)
			)
		}
		withContext(Dispatchers.Default) { delay(50) }
	}
}

/**
 * Waits until a node matching [matcher] is on screen and returns the first. On
 * timeout the failure carries the whole semantics tree, i.e. what was on screen.
 */
suspend fun ComposeUiTest.waitFor(
	matcher: SemanticsMatcher,
	timeoutMillis: Long = SCREEN_TIMEOUT_MS,
): SemanticsNodeInteraction {
	val start = TimeSource.Monotonic.markNow()
	await({ "Nothing matching ${matcher.description}" }, timeoutMillis) {
		// scroll lists for it only once the screen had time to settle: right after a
		// click the old screen, mid-transition, can still be the one on screen
		isShown(matcher) || (start.elapsedNow() > REVEAL_AFTER && reveal(matcher))
	}
	return onAllNodes(matcher)[0]
}

private fun ComposeUiTest.isShown(matcher: SemanticsMatcher) =
	onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

/**
 * Lazy lists only compose what's on screen: search each one for [matcher], from its
 * top (scrolling to a node only searches forwards from where the list is).
 */
private fun ComposeUiTest.reveal(matcher: SemanticsMatcher): Boolean {
	val lists = onAllNodes(hasScrollToNodeAction())
	for (i in lists.fetchSemanticsNodes().indices) {
		val found = runCatching {
			lists[i].performScrollToIndex(0)
			lists[i].performScrollToNode(matcher)
		}.isSuccess
		if (found) return true
		runCatching { lists[i].performScrollToIndex(0) } // not there: leave the list as found
	}
	return false
}

suspend fun ComposeUiTest.waitForText(value: String, substring: Boolean = false) = waitFor(text(value, substring))

suspend fun ComposeUiTest.click(matcher: SemanticsMatcher) {
	val node = waitFor(matcher)
	// lazy lists compose rows past the window's edge; nodes outside a scrollable
	// container just can't (and needn't) scroll
	runCatching { node.performScrollTo() }
	// the click action, not a tap at the node's centre: scrolled just into view, a
	// row's centre can sit under the mini player or the navigation bar
	node.performSemanticsAction(SemanticsActions.OnClick)
	waitForIdle()
}

suspend fun ComposeUiTest.click(label: String) = click(text(label))

/** A long press, the way rows open their item's sheet. */
suspend fun ComposeUiTest.longClick(label: String) {
	val node = waitFor(text(label) and SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick))
	runCatching { node.performScrollTo() }
	node.performSemanticsAction(SemanticsActions.OnLongClick)
	waitForIdle()
}

suspend fun ComposeUiTest.type(field: SemanticsMatcher, value: String) {
	waitFor(field and hasSetTextAction()).performTextInput(value)
}

/** Logs into the fixture server from the login screen and dismisses the setup page. */
suspend fun ComposeUiTest.logIn() {
	type(text("Instance URL"), TestServer.url)
	type(text("Username"), TestServer.USER)
	type(text("Password"), TestServer.PASSWORD)
	// "Log in" is both the headline and the button
	click(text("Log in") and hasClickAction())
	click("Done")
}

suspend fun ComposeUiTest.openTab(label: String) = click(tab(label))

/** Settings sit at the bottom of the navigation rail, or behind a top bar button on phones. */
suspend fun ComposeUiTest.openSettings() = click(tab("Settings") or description("Settings"))

suspend fun ComposeUiTest.navigateUp() = click(description("Navigate up"))
