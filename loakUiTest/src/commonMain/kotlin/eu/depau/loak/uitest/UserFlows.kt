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

	/**
	 * How far the current song has played (0–1), or null when this machine has no audio
	 * output to play it on; then [albumPlays] only checks the player picked the song up.
	 */
	protected abstract fun playbackProgress(): Float?

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
		// the player picks it up: the title shows twice, in the list and the player
		await({ "Low Tide never reached the player" }) {
			onAllNodes(text("Low Tide")).fetchSemanticsNodes().size >= 2
		}
		if (playbackProgress() == null) return@runApp
		await({ "Low Tide never started playing" }) { playbackProgress()!! > 0f }
	}

	@Test
	fun collectionsOpen() = runApp {
		logIn()
		openTab("Library")
		click("Artists")
		click("Aurora Test")
		waitForText("Second Wind")
		openTab("Library")
		click("Genres")
		click("Rock")
		waitForText("Fourth Wall")
		openTab("Playlists")
		click("UI Test Mix")
		waitForText("Copper Lines")
	}

	@Test
	fun songSheetStarsAndUnstars() = runApp {
		logIn()
		openTab("Library")
		click("Songs")
		longClick("Signal Fire")
		click(inSheet(description("Star")))
		openTab("Library")
		click("Starred")
		waitForText("Signal Fire")
		// the star lives on the shared fixture server: take it back for the other flows
		longClick("Signal Fire")
		click(inSheet(description("Unstar")))
		waitForText("Nothing's starred")
	}

	@Test
	fun playerShowsLyricsAndQueue() = runApp {
		logIn()
		// the fixture server's lyrics only: the online providers would make the flow wait on
		// the internet (lyrics show once every provider has answered)
		openSettings()
		click("Player")
		click("Lyrics sources")
		for (provider in listOf("Musixmatch", "LRCLIB", "LyricsPlus")) {
			click(checkbox(provider))
		}
		click(description("Close sheet"))
		navigateUp()
		// phones stack settings over Home: up once more, back to the tabs
		click(tab("Library") or description("Navigate up"))
		openTab("Library")
		click("Albums")
		click("First Light")
		click("Morning Static") // the album's first song, the one with (synced) lyrics
		openPlayer("Morning Static")
		click(description("Pause")) // the songs last 10 s: keep this one on
		click(tab("Lyrics"))
		waitForText("First line of the test lyrics")
		click(tab("Up next"))
		await({ "Up next never replaced the lyrics" }) {
			onAllNodes(text("First line of the test lyrics")).fetchSemanticsNodes().isEmpty()
		}
		waitForText("Low Tide")
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
