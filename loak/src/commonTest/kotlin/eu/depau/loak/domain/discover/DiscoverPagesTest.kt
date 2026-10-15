package eu.depau.loak.domain.discover

import eu.depau.loak.domain.manager.LidarrAlbumSnapshot
import eu.depau.loak.domain.manager.LidarrArtistSnapshot
import eu.depau.loak.domain.manager.LidarrCatalog
import eu.depau.loak.domain.manager.albumFor
import eu.depau.loak.domain.manager.artistFor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import eu.depau.loak.domain.manager.monitors
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The pure parts behind the Discover album and artist pages. */
class DiscoverPagesTest {
	private val catalog = LidarrCatalog(
		artists = listOf(
			LidarrArtistSnapshot(1, "a-mbid", "deadmau5"),
			LidarrArtistSnapshot(2, null, "Other"),
			LidarrArtistSnapshot(3, null, "Mangoo", monitored = false)
		),
		albums = listOf(
			LidarrAlbumSnapshot(10, "rg-1", "For Lack of a Better Name", artistId = 1, monitored = true),
			LidarrAlbumSnapshot(11, "rg-2", "Random Album Title", artistId = 2, monitored = true),
			LidarrAlbumSnapshot(12, "rg-3", "Strobe", artistId = 1, monitored = false)
		),
		queue = emptyList()
	)

	@Test
	fun findsTheLidarrAlbumByReleaseGroupThenByName() {
		assertEquals(11L, catalog.albumFor(DiscoverAlbum("", "Anything", "Anyone", mbid = "rg-2"))?.id)
		// Deezer's edition markers and case don't matter
		assertEquals(10L, catalog.albumFor(DiscoverAlbum("", "for lack of a better name (Remastered)", "Deadmau5"))?.id)
		// same title, other artist: not this album
		assertNull(catalog.albumFor(DiscoverAlbum("", "Random Album Title", "deadmau5")))
		// a library album's release id isn't a release group: it falls back to the name
		assertEquals(10L, catalog.albumFor("release-id", "For Lack of a Better Name", "deadmau5")?.id)
	}

	@Test
	fun findsTheLidarrArtistByMusicBrainzIdThenByName() {
		assertEquals(1L, catalog.artistFor(DiscoverArtist("", "Anyone", mbid = "a-mbid"))?.id)
		assertEquals(1L, catalog.artistFor(DiscoverArtist("", "DEADMAU5"))?.id)
		assertNull(catalog.artistFor(DiscoverArtist("", "Missing")))
	}

	@Test
	fun sizesDeezerImages() {
		assertEquals(
			"https://e-cdns-images.dzcdn.net/images/cover/abc/500x500-000000-80-0-0.jpg",
			deezerImageUrl("https://e-cdns-images.dzcdn.net/images/cover/abc/250x250-000000-80-0-0.jpg", 500)
		)
		assertEquals("https://coverartarchive.org/x", deezerImageUrl("https://coverartarchive.org/x", 500))
		assertNull(deezerImageUrl(null, 500))
	}

	@Test
	fun readsMusicBrainzArtistGenreAndArea() {
		val artist = musicBrainzArtistOf(Json.parseToJsonElement("""{
			"id": "mbid", "name": "Glass Beams", "score": 100,
			"area": {"name": "Melbourne"},
			"tags": [{"count": 1, "name": "jazz"}, {"count": 4, "name": "psychedelic"}]
		}""").jsonObject)
		assertEquals(MusicBrainzArtist("mbid", "Glass Beams", "Melbourne", "Psychedelic"), artist)
		assertEquals(
			MusicBrainzArtist("x", "Bare"),
			musicBrainzArtistOf(Json.parseToJsonElement("""{"id": "x", "name": "Bare"}""").jsonObject)
		)
	}

	// ---------------- Deezer-off search answers (M10) ----------------

	@Test
	fun mapsMusicBrainzReleaseGroupSearchToDiscoverAlbum() {
		val item = Json.parseToJsonElement("""{
			"id": "rg-123", "title": "Mau5ville: Level 1", "score": 100, "primary-type": "Album",
			"artist-credit": [{"name": "deadmau5", "artist": {"id": "a-1", "name": "deadmau5"}}]
		}""").jsonObject
		assertEquals(DiscoverAlbum("rg-123", "Mau5ville: Level 1", "deadmau5", recordType = "Album", mbid = "rg-123"), item.discoverAlbumOfMusicBrainz())
	}

	@Test
	fun mapsDiscographyReleaseGroupsToDiscoverAlbums() {
		val item = Json.parseToJsonElement("""{
			"id": "rg-9", "title": "Mangoo", "primary-type": "EP", "first-release-date": "2020-03-01",
			"artist-credit": [{"name": "Glass Beams", "artist": {"id": "a-1", "name": "Glass Beams"}}]
		}""").jsonObject
		// the type is lowercased like Deezer's, which the artist page's rail filters by
		assertEquals(
			DiscoverAlbum(
				"rg-9", "Mangoo", "Glass Beams", year = 2020, recordType = "ep", mbid = "rg-9",
				coverUrl = "https://coverartarchive.org/release-group/rg-9/front-250"
			),
			item.discographyAlbumOfMusicBrainz()
		)
	}

	@Test
	fun mapsMusicBrainzArtistSearchToDiscoverArtist() {
		val item = Json.parseToJsonElement("""{"id": "a-1", "name": "Glass Beams", "score": 100}""").jsonObject
		assertEquals(DiscoverArtist("a-1", "Glass Beams", mbid = "a-1"), item.discoverArtistOfMusicBrainz())
	}

	@Test
	fun onlyMonitoredReleasesCountAsInLidarr() {
		assertTrue(catalog.monitors(DiscoverAlbum("", "For Lack of a Better Name", "deadmau5")))
		// an artist Lidarr has lists its other releases unmonitored: those aren't in it yet
		assertFalse(catalog.monitors(DiscoverAlbum("", "Strobe", "deadmau5")))
		assertFalse(catalog.monitors(DiscoverSong("", "Strobe", "deadmau5", album = "Strobe")))
		// nor is every song of an artist it has
		assertFalse(catalog.monitors(DiscoverSong("", "Ghosts n Stuff", "deadmau5", album = "Ghosts n Stuff")))
		assertTrue(catalog.monitors(DiscoverSong("", "Moar Ghosts", "deadmau5", album = "For Lack of a Better Name")))
		// an artist it lists but doesn't monitor isn't in it either
		assertTrue(catalog.monitors(DiscoverArtist("", "deadmau5")))
		assertFalse(catalog.monitors(DiscoverArtist("", "Mangoo")))
	}
}
