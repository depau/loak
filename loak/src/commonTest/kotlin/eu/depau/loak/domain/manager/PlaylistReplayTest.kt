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

	@Test
	fun reorderListMovesItemCorrectly() {
		val songs = listOf("song1", "song2", "song3", "song4")
		// Move item from index 0 to index 2
		val reordered = songs.toMutableList().apply { add(2, removeAt(0)) }
		assertEquals(listOf("song2", "song3", "song1", "song4"), reordered)

		// Move item from index 3 to index 0
		val movedToTop = reordered.toMutableList().apply { add(0, removeAt(3)) }
		assertEquals(listOf("song4", "song2", "song3", "song1"), movedToTop)
	}

	@Test
	fun playlistFilterAndReverseOrdering() {
		data class DummySong(val id: String, val title: String, val artist: String, val album: String)
		val songs = listOf(
			DummySong("1", "Lonely", "Back On Earth", "Love"),
			DummySong("2", "Electric Love", "BØRNS", "Dopamine"),
			DummySong("3", "Midnight City", "M83", "Hurry Up")
		)

		// Filter by query "love" matches title "Electric Love" and album "Love"
		fun filter(query: String, reversed: Boolean): List<DummySong> {
			val matched = if (query.isBlank()) songs else songs.filter {
				it.title.contains(query, ignoreCase = true) ||
					it.artist.contains(query, ignoreCase = true) ||
					it.album.contains(query, ignoreCase = true)
			}
			return if (reversed) matched.reversed() else matched
		}

		val filteredReversed = filter("love", reversed = true)
		assertEquals(listOf("2", "1"), filteredReversed.map { it.id })

		val filteredAscending = filter("love", reversed = false)
		assertEquals(listOf("1", "2"), filteredAscending.map { it.id })

		val fullReversed = filter("", reversed = true)
		assertEquals(listOf("3", "2", "1"), fullReversed.map { it.id })
	}
}
