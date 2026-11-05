package eu.depau.loak.domain.repositories

import androidx.room3.Room
import com.russhwolf.settings.PropertiesSettings
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.data.database.entities.SongEntity
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.di.JdbcSQLiteDriver
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.ui.core.PlayerUiState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

/** The saved queue: ids for the song table's songs, whole songs for the rest, and the upgrade. */
class PlayerStateRepositoryTest {
	private val db = Room.inMemoryDatabaseBuilder<CacheDatabase>().setDriver(JdbcSQLiteDriver()).build()
	private val settings = PropertiesSettings(Properties())
	private fun repository() = PlayerStateRepository(settings, db.songDao())

	/** A save as the player makes it, the state stamped when taken. */
	private suspend fun save(state: PlayerUiState) = repository().run { save(state, stamp()) }

	@AfterTest
	fun close() = db.close()

	private val library = listOf(song("a"), song("b"), song("c"))
	// a radio stream or an Autoplay pick: not in the song table
	private val extra = song("radio").toDomainModel()

	private fun state() = library.map { it.toDomainModel() }.let { songs ->
		val queue = listOf(songs[0], extra, songs[1], songs[2], songs[0])
		PlayerUiState(queue = queue, currentSong = queue[2], currentIndex = 2, repeatMode = 1, progress = 0.5f)
	}

	@Test
	fun theQueueComesBackWithTheSongsOutsideTheLibrary() = runBlocking {
		db.songDao().insertSongsIgnoringConflicts(library)
		val state = state()
		save(state)
		assertTrue(repository().hasSavedQueue)
		// the library's songs go as ids: only the extra (and the current song) is saved whole
		val saved = settings.getString("player_state_v2", "")
		assertTrue("\"title\":\"radio\"" in saved && "\"title\":\"a\"" !in saved, saved)
		assertEquals(state, repository().load())
	}

	@Test
	fun aFlushWithoutLookupsComesBackToo() = runBlocking {
		db.songDao().insertSongsIgnoringConflicts(library)
		val state = state()
		repository().flush(state)
		assertEquals(state, repository().load())
	}

	@Test
	fun songsNoLongerKnownAreDropped() = runBlocking {
		db.songDao().insertSongsIgnoringConflicts(library)
		save(state())
		db.songDao().deleteSong("b")
		val loaded = repository().load()!!
		assertEquals(listOf("a", "radio", "c", "a"), loaded.queue.map { it.id })
		// the current song went: the next one plays
		assertEquals(2, loaded.currentIndex)
		assertEquals("c", loaded.currentSong?.id)
	}

	@Test
	fun theOldFormatIsUpgradedOnce() = runBlocking {
		db.songDao().insertSongsIgnoringConflicts(library)
		val state = state()
		settings.putString("player_state", Json.encodeToString(state))
		assertEquals(state, repository().load())
		assertFalse(settings.hasKey("player_state"))
		assertTrue(repository().hasSavedQueue)
		assertEquals(state, repository().load())
	}

	@Test
	fun anEmptyQueueIsNoSavedQueue() = runBlocking {
		assertNull(repository().load())
		save(state())
		save(PlayerUiState())
		assertFalse(repository().hasSavedQueue)
	}

	@Test
	fun aSlowSaveOfAnOlderStateNeverLandsAfterAFlush() = runBlocking {
		db.songDao().insertSongsIgnoringConflicts(library)
		val repository = repository()
		// taken before the app went to the background, still looking up its songs
		val older = repository.stamp()
		val state = state()
		repository.flush(state)
		repository.save(PlayerUiState(), older)
		assertEquals(state, repository().load())
		assertTrue(repository().hasSavedQueue)
	}

	private fun song(id: String) = SongEntity(
		songId = id,
		title = id,
		artistName = "Artist",
		artistId = "a1",
		albumTitle = null,
		belongsToAlbumId = null,
		parentId = null,
		comment = null,
		trackNumber = 1,
		discNumber = 1,
		isrc = emptyList(),
		year = 2024,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = 2.minutes,
		bpm = null,
		contributors = emptyList(),
		playCount = 0,
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 1000,
		fileExtension = "mp3",
		mimeType = "audio/mpeg",
		filePath = "/$id.mp3",
		starredAt = null,
		coverArtId = null,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown,
		artists = emptyList(),
		albumArtists = emptyList(),
		isExternal = false
	)
}
