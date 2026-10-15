package eu.depau.loak.uitest

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.printToString

/**
 * How long a screen gets to show its content. Generous for slow CI emulators,
 * and short enough that a frozen screen fails the flow instead of the job.
 */
const val SCREEN_TIMEOUT_MS = 30_000L

/** The fixture server (NavidromeFixture in buildSrc): keep these in sync with it. */
object TestServer {
	const val PORT = 45330
	const val USER = "loak"
	const val PASSWORD = "loak-ui-test"
	val url get() = "http://$testServerHost:$PORT"
}

/** The host the app reaches the fixture server's machine at (10.0.2.2 from an Android emulator). */
expect val testServerHost: String

fun text(value: String, substring: Boolean = false) = hasText(value, substring = substring)

fun description(value: String) = hasContentDescription(value)

/** A navigation bar / rail item. */
fun tab(label: String) = hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

/**
 * Waits until a node matching [matcher] is on screen and returns the first. On
 * timeout the failure carries the whole semantics tree, i.e. what was on screen.
 */
fun ComposeUiTest.waitFor(
	matcher: SemanticsMatcher,
	timeoutMillis: Long = SCREEN_TIMEOUT_MS,
): SemanticsNodeInteraction {
	try {
		waitUntil(timeoutMillis = timeoutMillis) { isShown(matcher) || reveal(matcher) }
	} catch (e: ComposeTimeoutException) {
		throw AssertionError(
			"Nothing matching ${matcher.description} after ${timeoutMillis / 1000} s. On screen:\n" +
				onRoot(useUnmergedTree = false).printToString(),
			e,
		)
	}
	return onAllNodes(matcher)[0]
}

private fun ComposeUiTest.isShown(matcher: SemanticsMatcher) =
	onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

/** Lazy lists only compose what's on screen: scroll each one looking for [matcher]. */
private fun ComposeUiTest.reveal(matcher: SemanticsMatcher): Boolean {
	val lists = onAllNodes(hasScrollToNodeAction())
	for (i in lists.fetchSemanticsNodes().indices) {
		if (runCatching { lists[i].performScrollToNode(matcher) }.isSuccess) return true
	}
	return false
}

fun ComposeUiTest.waitForText(value: String, substring: Boolean = false) = waitFor(text(value, substring))

fun ComposeUiTest.click(matcher: SemanticsMatcher) {
	val node = waitFor(matcher)
	// lazy lists compose rows past the window's edge; nodes outside a scrollable
	// container just can't (and needn't) scroll
	runCatching { node.performScrollTo() }
	// the click action, not a tap at the node's centre: scrolled just into view, a
	// row's centre can sit under the mini player or the navigation bar
	node.performSemanticsAction(SemanticsActions.OnClick)
	waitForIdle()
}

fun ComposeUiTest.click(label: String) = click(text(label))

fun ComposeUiTest.type(field: SemanticsMatcher, value: String) {
	waitFor(field and hasSetTextAction()).performTextInput(value)
}

/** Logs into the fixture server from the login screen and dismisses the setup page. */
fun ComposeUiTest.logIn() {
	type(text("Instance URL"), TestServer.url)
	type(text("Username"), TestServer.USER)
	type(text("Password"), TestServer.PASSWORD)
	// "Log in" is both the headline and the button
	click(text("Log in") and hasClickAction())
	click("Done")
}

fun ComposeUiTest.openTab(label: String) = click(tab(label))

/** Settings sit at the bottom of the navigation rail, or behind a top bar button on phones. */
fun ComposeUiTest.openSettings() = click(tab("Settings") or description("Settings"))

fun ComposeUiTest.navigateUp() = click(description("Navigate up"))
