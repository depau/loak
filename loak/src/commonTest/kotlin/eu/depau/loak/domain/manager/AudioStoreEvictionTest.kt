package eu.depau.loak.domain.manager

import eu.depau.loak.data.database.entities.AudioFileEntity
import eu.depau.loak.domain.models.AudioQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioStoreEvictionTest {
	private fun entry(
		song: String,
		quality: AudioQuality = AudioQuality.Raw,
		bytes: Long = 10,
		accessed: Long = 0,
		pinned: Boolean = false,
		complete: Boolean = true
	) = AudioFileEntity(song, quality.key, song, bytes, null, complete, pinned, accessed, 0)

	private fun List<AudioFileEntity>.keys() = map { it.songId to it.quality }

	@Test
	fun qualityOrder() {
		val opus96 = AudioQuality.of("opus", 96)
		val opus192 = AudioQuality.of("opus", 192)
		assertTrue(AudioQuality.Raw > opus192 && opus192 > opus96)
		assertEquals(AudioQuality.Raw, AudioQuality.of(null, 0))
		assertEquals(AudioQuality.Raw, AudioQuality.of("raw", 320))
		assertEquals(AudioQuality.Raw, AudioQuality.of("", null))
		assertEquals(AudioQuality("default", 128), AudioQuality.of(null, 128))
		for (q in listOf(AudioQuality.Raw, opus192, AudioQuality.of(" MP3 ", 320))) {
			assertEquals(q, AudioQuality.parse(q.key))
		}
	}

	@Test
	fun evictsLeastRecentlyPlayedBySize() {
		val entries = listOf(
			entry("a", accessed = 3), entry("b", accessed = 1), entry("c", accessed = 2),
			entry("p", accessed = 0, pinned = true, bytes = 1000),
			entry("q", accessed = 0, complete = false)
		)
		assertEquals(
			listOf("b", "c"),
			evictions(entries, 10, Int.MAX_VALUE, emptySet()).map { it.songId }
		)
		assertEquals(emptyList(), evictions(entries, 30, Int.MAX_VALUE, emptySet()))
	}

	@Test
	fun evictsByCountSkippingInUse() {
		val entries =
			listOf(entry("a", accessed = 3), entry("b", accessed = 1), entry("c", accessed = 2))
		assertEquals(
			listOf("c"),
			evictions(entries, Long.MAX_VALUE, 2, setOf("b")).map { it.songId }
		)
		// disabled: everything unpinned not in use goes
		assertEquals(listOf("c", "a"), evictions(entries, 0, 0, setOf("b")).map { it.songId })
	}

	@Test
	fun dropsRedundantLowerQualities() {
		val low = AudioQuality.of("opus", 96)
		val high = AudioQuality.of("opus", 192)
		val entries = listOf(
			// cached low + cached high: low is redundant
			entry("a", low), entry("a", high),
			// pinned low + cached high: keep the download
			entry("b", low, pinned = true), entry("b", high),
			// pinned low upgraded to pinned high: low goes
			entry("c", low, pinned = true), entry("c", AudioQuality.Raw, pinned = true),
			// higher one still downloading: keep the low one
			entry("d", low), entry("d", high, complete = false),
			// in use: untouched
			entry("e", low), entry("e", high)
		)
		assertEquals(
			listOf("a" to low.key, "c" to low.key),
			evictions(entries, Long.MAX_VALUE, Int.MAX_VALUE, setOf("e")).keys()
		)
	}
}
