package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.AudioQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudioPrefetchTest {
	@Test
	fun targets() {
		val upcoming = listOf("a", null, "b", "c", "d")
		assertEquals(listOf("a"), prefetchTargets("x", upcoming, metered = true))
		// a downloaded/radio entry (null) still uses up a slot
		assertEquals(listOf("a", "b"), prefetchTargets("x", upcoming, metered = false))
		// a short repeating queue: no duplicates, never the current song
		assertEquals(listOf("y"), prefetchTargets("x", listOf("y", "x", "y"), metered = false))
		assertEquals(emptyList(), prefetchTargets("x", emptyList(), metered = false))
	}

	@Test
	fun replaces() {
		val opus128 = AudioQuality.of("opus", 128)
		val opus192 = AudioQuality.of("opus", 192)
		assertTrue(opus192.canReplace(opus128, online = true))
		assertTrue(AudioQuality.Raw.canReplace(opus192, online = true))
		assertFalse(opus128.canReplace(opus192, online = true))
		assertFalse(opus128.canReplace(AudioQuality.Raw, online = true))
		assertTrue(opus128.canReplace(AudioQuality.Raw, online = false))
	}
}
