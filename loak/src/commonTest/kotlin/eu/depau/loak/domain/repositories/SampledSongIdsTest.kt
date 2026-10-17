package eu.depau.loak.domain.repositories

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Home's shelves read a slice of the library, so what they can act on has to be in it.
 */
class SampledSongIdsTest {
	private val all = (1..500).map { "s$it" }

	@Test
	fun keepsEverySongAShelfActsOn() {
		val wanted = listOf("s3", "s" + "400", "s7")
		val ids = sampledSongIds(all, wanted, day = 1, size = 10)

		assertTrue(wanted.all { it in ids }, "played/starred/rated songs are always in the library")
	}

	@Test
	fun samplesOnlyTheRestAndStaysBounded() {
		val wanted = listOf("s3", "s4")
		val ids = sampledSongIds(all, wanted, day = 1, size = 10)

		assertEquals(setOf("s3", "s4") + ids.takeLast(10), ids.toSet())
		assertEquals(12, ids.size)
		assertEquals(10, ids.count { it !in wanted })
	}

	@Test
	fun samplesTheSameSongsOnTheSameDay() {
		assertEquals(
			sampledSongIds(all, emptyList(), day = 7, size = 20),
			sampledSongIds(all, emptyList(), day = 7, size = 20)
		)
	}

	@Test
	fun samplesDifferentSongsTheNextDay() {
		assertTrue(
			sampledSongIds(all, emptyList(), day = 7, size = 20) !=
				sampledSongIds(all, emptyList(), day = 8, size = 20)
		)
	}

	@Test
	fun takesEverythingWhenTheLibraryIsSmall() {
		val small = listOf("s1", "s2", "s3")
		val ids = sampledSongIds(small, listOf("s1"), day = 1, size = 1500)

		assertEquals(small.toSet(), ids.toSet())
	}
}
