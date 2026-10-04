package eu.depau.loak.domain.manager

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.russhwolf.settings.PropertiesSettings
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.domain.models.AudioQuality
import eu.depau.loak.domain.models.settings.AudioCacheLimit
import kotlinx.coroutines.delay
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
			.setDriver(BundledSQLiteDriver())
			.build()
		val prefs = PreferenceManager(PropertiesSettings(Properties())).apply {
			audioCacheLimit = AudioCacheLimit.Songs
			audioCacheMaxSongs = 1
		}
		val store = AudioStore(dir.path, db.audioFileDao(), prefs)
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
		assertFalse(store.pin("s1", AudioQuality.of("opus", 192)))

		// only 1 cached song allowed; the pinned one doesn't count
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
		AudioStore(dir.path, db.audioFileDao(), prefs)
		repeat(100) { if (File(dir, "stray").exists()) delay(20) }
		assertEquals(
			setOf(File(store.pathOf(raw)).name, File(store.pathOf(s3)).name, "s6.raw.opus.partial"),
			dir.list()!!.toSet()
		)
		assertEquals(1000, store.openWrite("s6", AudioQuality.Raw, "opus", resume = true)!!.offset)
		db.close()
	}
}
