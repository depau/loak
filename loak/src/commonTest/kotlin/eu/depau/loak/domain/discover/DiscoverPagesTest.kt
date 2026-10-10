package eu.depau.loak.domain.discover

import eu.depau.loak.domain.manager.LidarrAlbumSnapshot
import eu.depau.loak.domain.manager.LidarrArtistSnapshot
import eu.depau.loak.domain.manager.LidarrCatalog
import eu.depau.loak.domain.manager.albumFor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The pure parts behind the Discover album and artist pages. */
class DiscoverPagesTest {
	private val catalog = LidarrCatalog(
		artists = listOf(LidarrArtistSnapshot(1, "a-mbid", "deadmau5"), LidarrArtistSnapshot(2, null, "Other")),
		albums = listOf(
			LidarrAlbumSnapshot(10, "rg-1", "For Lack of a Better Name", artistId = 1, monitored = true),
			LidarrAlbumSnapshot(11, "rg-2", "Random Album Title", artistId = 2, monitored = true)
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
}
