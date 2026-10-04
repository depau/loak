package eu.depau.loak.domain.manager

import dev.zt64.subsonic.api.model.Song
import eu.depau.loak.data.database.entities.AlbumEntity
import eu.depau.loak.domain.repositories.toLibrarySongs
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LibrarySyncTest {
	private fun pull(
		since: Duration = 2.hours,
		scanCount: Int? = 100,
		lastCount: Int = 100,
		metered: Boolean = false,
		empty: Boolean = false,
		scanning: Boolean = false
	) = shouldPullLibrary(empty, metered, since, scanCount, scanning, lastCount)

	@Test
	fun shouldPull() {
		assertFalse(pull(), "unchanged count, recent pull")
		assertTrue(pull(lastCount = 99), "count changed")
		assertTrue(pull(since = 25.hours), "too old")
		assertFalse(pull(lastCount = 99, metered = true), "never on metered")
		assertTrue(pull(metered = true, empty = true), "first sync even on metered")
		assertFalse(pull(lastCount = 99, scanning = true), "scanning")
		assertFalse(pull(scanCount = null), "no scan status, recent")
		assertTrue(pull(scanCount = null, since = 7.hours), "no scan status, old")
	}

	@Test
	fun librarySongs() {
		val album = AlbumEntity(
			albumId = "al", name = "Album", artistId = "ar", artistName = "Artist",
			coverArtId = null, songCount = 2, duration = Duration.ZERO, year = null, genre = null,
			starredAt = null, userRating = null, musicBrainzId = null, createdAt = Instant.fromEpochSeconds(0),
			lastPlayedAt = null, playCount = 0, genres = emptyList(), version = null, isExternal = false
		)
		// Song's constructor is internal; build it the way the client does
		val songs = Json { ignoreUnknownKeys = true }.decodeFromString(
			ListSerializer(Song.serializer()),
			"""[
				{"id": "s1", "title": "One", "albumId": "al", "artistId": "x", "artist": "X"},
				{"id": "s2", "title": "Two", "albumId": "al", "track": 2, "discNumber": 1},
				{"id": "s3", "title": "Orphan", "albumId": "other"}
			]"""
		).toLibrarySongs(mapOf("al" to album))

		assertEquals(listOf("s1", "s2"), songs.map { it.songId })
		assertEquals("x", songs[0].artistId)
		assertEquals("ar", songs[1].artistId)
		assertEquals("Artist", songs[1].artistName)
		assertEquals("al", songs[1].belongsToAlbumId)
		assertEquals(2, songs[1].trackNumber)
		assertEquals(1, songs[1].discNumber)
	}
}
