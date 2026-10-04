package eu.depau.loak.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaylistReplayTest {
	@Test
	fun removalResolvesBySongId() {
		val server = listOf("a", "b", "a", "c", "a")
		assertEquals(3, serverIndexOf(server, "c", 0), "moved since: found by id")
		assertEquals(0, serverIndexOf(server, "a", 0), "first of the duplicates")
		assertEquals(2, serverIndexOf(server, "a", 1), "the same occurrence")
		assertEquals(4, serverIndexOf(server, "a", 7), "fewer on the server: the last one")
		assertNull(serverIndexOf(server, "d", 0), "gone from the server")
	}

	/** Two removals of the same duplicated song, replayed in either order, remove both. */
	@Test
	fun duplicateRemovalsInAnyOrder() {
		fun apply(songs: List<String>, occurrence: Int) =
			songs.toMutableList().apply { removeAt(serverIndexOf(this, "a", occurrence)!!) }
		val server = listOf("a", "b", "a")
		// locally: the second "a" went first (occurrence 1), then the remaining one (occurrence 0)
		assertEquals(listOf("b"), apply(apply(server, 1), 0))
		assertEquals(listOf("b"), apply(apply(server, 0), 1))
	}
}
