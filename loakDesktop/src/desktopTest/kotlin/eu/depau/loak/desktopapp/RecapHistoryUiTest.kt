@file:OptIn(ExperimentalTestApi::class)

package eu.depau.loak.desktopapp

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import eu.depau.loak.App
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.data.database.DownloadDatabase
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.di.initKoin
import eu.depau.loak.domain.manager.PlayLogManager
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.uitest.click
import eu.depau.loak.uitest.await
import eu.depau.loak.uitest.description
import eu.depau.loak.uitest.logIn
import eu.depau.loak.uitest.openTab
import eu.depau.loak.uitest.text
import eu.depau.loak.uitest.waitForText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.koin.core.context.stopKoin
import java.io.File
import javax.imageio.ImageIO

/**
 * The Recap with a history behind it.
 *
 * No user flow can play its way here: a play is only counted once 30 s of a song have played
 * (`ScrobbleManager.MIN_SCROBBLE_DURATION_MS`) and the fixture's songs last 10 s. So this writes
 * the plays into the play log the way playback would, then opens the screen — the sections, the
 * grid and the covers only render when there is something to show.
 */
class RecapHistoryUiTest {
	@Test
	fun recapRendersWhatWasPlayed() {
		Dispatchers.setMain(Dispatchers.Swing)
		stopKoin() // after a flow that timed out, its app is still around
		val koin = initKoin()
		val player = koin.koin.get<MediaPlayerViewModel>()
		player.setVolume(0f)
		try {
			runDesktopComposeUiTest(412, 892) {
				setContent { App() }
				logIn()

				// the library the login just synced has to have landed before a play of one
				// of its songs can resolve to anything
				openTab("Library")
				click("Songs")
				waitForText("Morning Static")
				val songs = koin.koin.get<SongDao>().getAllSongs()
				val playLog = koin.koin.get<PlayLogManager>()
				songs.take(6).forEach { song -> repeat(2) { playLog.recordSong(song.songId) } }

				openTab("Library")
				click("Recap")
				waitForText("Top artists")
				waitForText("Top albums")
				waitForText("Top tracks")
				waitForText("Activity")

				// a track row plays what it names, like a song row in any other list. The mini
				// player is the tabs' bottom bar, so this screen has none to check: the player
				// itself is what says the row was the one that started.
				val top = songs.take(6).minByOrNull { it.songId }!!.songId
				click(text(songs.first { it.songId == top }.title, substring = true))
				await({ "the tapped track never reached the player" }) {
					player.uiState.value.currentSong?.id == top
				}

				val dir = File(
					System.getProperty("loak.uitest.failures") ?: "build/ui-test-failures"
				).apply { mkdirs() }
				val file = File(dir, "recap-populated.png")
				ImageIO.write(onAllNodes(isRoot())[0].captureToImage().toAwtImage(), "png", file)
				println("Screenshot of the populated Recap: $file")
			}
		} finally {
			// the player is a Koin singleton, never cleared outside tests: stop its
			// coroutines before the databases they use close under them
			ViewModelStore().apply { put("player", player); clear() }
			runCatching { koin.koin.get<CacheDatabase>().close() }
			runCatching { koin.koin.get<DownloadDatabase>().close() }
			stopKoin()
			Dispatchers.resetMain()
		}
	}
}
