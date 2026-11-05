package eu.depau.loak.uitest

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import kotlinx.coroutines.test.TestResult
import kotlin.test.Test

/**
 * How long the large library's slow steps get: its first sync, and scrolling a list down to
 * its middle (thousands of rows: ~20 s on a fast desktop).
 */
const val LARGE_LIBRARY_TIMEOUT_MS = 300_000L

/**
 * User flows against the large library (NavidromeFixture in buildSrc generates it: artists
 * "Artist 001"–"Artist 200", 5 albums "Album 001-1"… each, 10 songs "Song 001-1-01"… each, and
 * the playlists "Long Mix", 80 songs, and "Big Mix", 5000). Subclassed per platform like
 * [UserFlows].
 */
abstract class LargeLibraryFlows {
	/** Starts the real app as a fresh install, runs [flow] against it, then tears it down. */
	abstract fun runApp(flow: suspend ComposeUiTest.() -> Unit): TestResult

	/** Logs in to the large library's server and waits for its first sync to bring it all in. */
	private suspend fun ComposeUiTest.logInAndSync() {
		logIn(TestServer.largeUrl)
		// Home shows the sync's progress instead of its shelves until the whole library is in
		waitFor(text("Artist ", substring = true), LARGE_LIBRARY_TIMEOUT_MS)
	}

	@Test
	fun songsReachTheMiddleOfTheAlphabet() = runApp {
		logInAndSync()
		openTab("Library")
		click("Songs")
		waitFor(text("Song 100-3-05"), LARGE_LIBRARY_TIMEOUT_MS)
	}

	@Test
	fun albumsAndArtistsOpen() = runApp {
		logInAndSync()
		openTab("Library")
		click("Albums")
		waitForText("Album 100-3")
		openTab("Library")
		click("Artists")
		waitForText("Artist 100")
	}

	@Test
	fun longMixPlays() = runApp {
		logInAndSync()
		openTab("Playlists")
		click("Long Mix")
		click(text("Play") and hasClickAction())
		// the player picks it up: a song title outside the list, in the player
		waitFor(text("Song ", substring = true) and !hasAnyAncestor(hasScrollToNodeAction()))
	}

	@Test
	fun bigMixShowsItsSongCount() = runApp {
		logInAndSync()
		openTab("Playlists")
		click("Big Mix")
		waitForText("5000 songs")
	}
}
