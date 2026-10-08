package eu.depau.loak.domain.manager

import androidx.room3.Room
import androidx.room3.useWriterConnection
import eu.depau.loak.di.JdbcSQLiteDriver
import com.russhwolf.settings.PropertiesSettings
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.domain.models.AudioQuality
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AudioStoreTest {
	@Test
	fun writeCompleteUpgradeEvictSweep() = runBlocking {
		val dir = Files.createTempDirectory("audio").toFile().apply { deleteOnExit() }
		val db = Room.inMemoryDatabaseBuilder<CacheDatabase>()
			.setDriver(JdbcSQLiteDriver())
			.build()
		val prefs = PreferenceManager(PropertiesSettings(Properties())).apply {
			audioCacheMaxBytes = 1000
		}
		val store = AudioStore(dir.path, db.audioFileDao(), prefs, MutableStateFlow(""))
		val bytes = ByteArray(1000) { it.toByte() }

		suspend fun write(song: String, q: AudioQuality, n: Int = 1000, expected: Long? = null) =
			store.openWrite(song, q, "opus", expectedBytes = expected)!!.run {
				write(bytes, 0, n)
				complete().also { delay(5) } // distinct lastAccessed
			}

		val low = write("s1", AudioQuality.of("opus", 96))!!
		assertEquals(1000, low.bytes)
		assertEquals(bytes.toList(), File(store.pathOf(low)).readBytes().toList())
		assertEquals(low, store.bestComplete("s1"))

		// a better copy replaces the worse one
		val raw = write("s1", AudioQuality.Raw)!!
		assertFalse(File(store.pathOf(low)).exists())
		assertEquals(raw, store.bestComplete("s1"))
		assertFalse(store.pin("s1", AudioQuality.of("opus", 192), "opus"))

		// room for 1 cached song; the pinned one doesn't count
		val s2 = write("s2", AudioQuality.Raw)!!
		val s3 = write("s3", AudioQuality.Raw)!!
		assertNull(store.bestComplete("s2"))
		assertFalse(File(store.pathOf(s2)).exists())
		assertTrue(File(store.pathOf(s3)).exists())
		assertEquals(AudioStoreUsage(1000, 1, 1000, 1), store.usage.first())

		// truncated download: dropped
		assertNull(write("s4", AudioQuality.Raw, n = 10, expected = 1000))
		assertNull(store.bestComplete("s4"))

		// a restart drops strays and unpinned partials, keeps pinned partials to resume
		File(dir, "stray").writeText("x")
		store.openWrite("s5", AudioQuality.Raw, "opus")!!.run { write(bytes); close() }
		store.openWrite("s6", AudioQuality.Raw, "opus", pinned = true)!!.run { write(bytes); close() }
		AudioStore(dir.path, db.audioFileDao(), prefs, MutableStateFlow(""))
		repeat(100) { if (File(dir, "stray").exists()) delay(20) }
		assertEquals(
			setOf(File(store.pathOf(raw)).name, File(store.pathOf(s3)).name, "s6.raw.opus.partial"),
			dir.list()!!.toSet()
		)
		assertEquals(1000, store.openWrite("s6", AudioQuality.Raw, "opus", resume = true)!!.offset)
		db.close()
	}

	@Test
	fun downloadsArePinnedEntriesPerServer() = runBlocking {
		val dir = Files.createTempDirectory("audio").toFile().apply { deleteOnExit() }
		val db = Room.inMemoryDatabaseBuilder<CacheDatabase>()
			.setDriver(JdbcSQLiteDriver())
			.build()
		val prefs = PreferenceManager(PropertiesSettings(Properties()))
		val server = MutableStateFlow("a")
		val store = AudioStore(dir.path, db.audioFileDao(), prefs, server)
		val bytes = ByteArray(1000) { it.toByte() }
		val low = AudioQuality.of("opus", 96)
		store.openWrite("s1", low, "opus")!!.run { write(bytes); complete() }

		// downloading a cached song: the cached copy is pinned, the better one queued
		assertTrue(store.pin("s1", AudioQuality.Raw, "flac"))
		assertEquals(listOf("raw"), store.pendingDownloads().map { it.quality })
		assertEquals(setOf("s1"), store.downloadedIds())
		store.openWrite("s1", AudioQuality.Raw, "flac", pinned = true, resume = true)!!
			.run { write(bytes); complete() }
		assertEquals(emptyList(), store.pendingDownloads())
		assertEquals("raw", store.bestComplete("s1")!!.quality)
		assertEquals(1, dir.list()!!.size) // the lower copy is gone

		// files from before the store move in, pinned
		val old = File(Files.createTempDirectory("old").toFile(), "s2.mp3").apply { writeText("x") }
		store.import("s2", AudioQuality("mp3", 0), old.path)
		assertFalse(old.exists())
		assertEquals("x", File(store.pathOf(store.bestComplete("s2")!!)).readText())

		// another server's song with the same id is another song
		server.value = "b"
		assertNull(store.bestComplete("s1"))
		assertEquals(emptySet(), store.downloadedIds())
		server.value = "a"

		store.pin("s3", AudioQuality.Raw, "flac")
		store.dropPending(listOf("s3"))
		store.unpin(listOf("s1"))
		assertEquals(emptyList(), store.pendingDownloads())
		assertEquals(setOf("s2"), store.downloadedIds())
		db.close()
	}

	@Test
	fun storedSongsAndTheirCollections() = runBlocking {
		val dir = Files.createTempDirectory("audio").toFile().apply { deleteOnExit() }
		val db = Room.inMemoryDatabaseBuilder<CacheDatabase>()
			.setDriver(JdbcSQLiteDriver())
			.build()
		val prefs = PreferenceManager(PropertiesSettings(Properties()))
		val store = AudioStore(dir.path, db.audioFileDao(), prefs, MutableStateFlow("a"))
		val sql = listOf(
			"INSERT INTO AlbumEntity (albumId, artistId, genres, songCount, createdAt, " +
				"playCount, isExternal) VALUES ('al1', 'albumArtist', '[]', 2, 0, 0, 0)",
			"INSERT INTO PlaylistEntity (playlistId, songCount, duration, createdAt, " +
				"modifiedAt, allowedUsers) VALUES ('p1', 1, 0, 0, 0, '[]'), " +
				"('p2', 1, 0, 0, 0, '[]')"
		) + listOf("s1" to "al1", "s2" to "al1", "s3" to "al2").map { (song, album) ->
			"INSERT INTO SongEntity (songId, title, artistId, belongsToAlbumId, isrc, genres, " +
				"moods, duration, contributors, playCount, fileSize, explicitStatus, artists, " +
				"albumArtists, isExternal) VALUES ('$song', '', 'ar-$song', '$album', '[]', " +
				"'[]', '[]', 0, '[]', 0, 0, 0, '[]', '[]', 0)"
		} + listOf(
			"INSERT INTO PlaylistSongCrossRef VALUES ('p1', 's1', 0), ('p2', 's3', 0)"
		)
		db.useWriterConnection { c -> sql.forEach { q -> c.usePrepared(q) { it.step() } } }

		store.openWrite("s1", AudioQuality.Raw, "flac")!!.run { write(ByteArray(10)); complete() }
		// not complete yet: doesn't count
		store.openWrite("s3", AudioQuality.Raw, "flac")!!.write(ByteArray(10))

		assertEquals(setOf("s1"), store.storedSongs.first { it.isNotEmpty() })
		assertEquals(
			setOf("al1", "ar-s1", "albumArtist", "p1"),
			store.storedCollections.first { it.isNotEmpty() }
		)
		db.close()
	}
}
