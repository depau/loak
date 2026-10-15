package eu.depau.loak.uitest

import androidx.compose.ui.test.ComposeUiTest
import kotlinx.coroutines.test.TestResult
import kotlin.test.Test

/**
 * The user flows every platform runs against its real app and the fixture
 * server's library (loakUiTest/fixture). Each platform's test suite subclasses
 * this with [runApp]; a flow fails on a crash, on an assertion, or when a screen
 * doesn't show its content within [SCREEN_TIMEOUT_MS].
 */
abstract class UserFlows {
	/**
	 * Starts the real app, as a fresh install, runs [flow] against it, then tears
	 * it down so the next flow starts fresh too. Returns the platform's async test
	 * result (a Promise on the web, Unit elsewhere).
	 */
	abstract fun runApp(flow: suspend ComposeUiTest.() -> Unit): TestResult

	@Test
	fun logInLandsOnHome() = runApp {
		logIn()
		waitForText("Quick picks")
	}

	@Test
	fun everyTabOpens() = runApp {
		logIn()
		openTab("Playlists")
		waitForText("UI Test Mix")
		openTab("Explore")
		waitForText("In your library")
		openTab("Library")
		waitForText("Recently added")
		openTab("Home")
		waitForText("Quick picks")
		openSettings()
		waitForText("Appearance")
	}

	@Test
	fun libraryListsOpen() = runApp {
		logIn()
		for ((row, content) in listOf(
			"Songs" to "Morning Static",
			"Albums" to "First Light",
			"Artists" to "Basalt Echo",
			"Genres" to "Electronic",
			"Recently added" to "Fourth Wall",
			"Random" to "Third Rail",
			"Starred" to "Nothing's starred",
			"Playlists" to "UI Test Mix",
			"Shares" to "No shares",
		)) {
			openTab("Library")
			click(row)
			waitForText(content)
		}
	}

	@Test
	fun albumPlays() = runApp {
		logIn()
		openTab("Library")
		click("Albums")
		click("First Light")
		click("Low Tide")
		// the player picks it up: the title shows twice, in the list and the player.
		// Not "Pause": CI machines have no audio device, so the stream can't start there
		await({ "Low Tide never reached the player" }) {
			onAllNodes(text("Low Tide")).fetchSemanticsNodes().size >= 2
		}
	}

	@Test
	fun searchFindsSongs() = runApp {
		logIn()
		click(text("Search your library") or description("Search"))
		type(text("Search", substring = true), "Copper")
		waitForText("Copper Lines")
	}

	@Test
	fun settingsPagesOpenAndGoBack() = runApp {
		logIn()
		openSettings()
		for (page in listOf(
			"Appearance", "Player", "Tabs", "Playback", "Sound & quality",
			"Downloads & storage", "Advanced", "About Lo'ak",
		)) {
			click(page)
			navigateUp()
			waitForText("Appearance")
		}
	}

	@Test
	fun logOutReturnsToLogin() = runApp {
		logIn()
		openSettings()
		click(text(TestServer.USER, substring = true))
		click("Log out")
		waitForText("Instance URL")
	}
}
