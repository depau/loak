package eu.depau.loak.domain.discover

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListenBrainzClientTest {
	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun parsesSimilarArtists() {
		// Labs similar-artists endpoint: a bare JSON array of {artist_mbid, name, score, …}
		val artists = json
			.parseToJsonElement("""[
				{"artist_mbid": "e2ef1279-ee73-4b6c-9f47-4b4c05cc06da", "name": "Boards of Canada"},
				{"artist_mbid": "c7f2e2b0-0b0e-4d5e-8d44-2c60f0f0e0f0", "name": "Aphex Twin"},
				{"artist_mbid": "f82bcf78-5b69-4623-a5ef-3c4b8f07b6d9", "name": "Flying Lotus"}
			]""".trimIndent()).jsonArray
			.mapNotNull { similarArtistOf(it.jsonObject) }

		assertEquals(3, artists.size)
		assertEquals("Boards of Canada", artists[0].name)
		assertEquals("e2ef1279-ee73-4b6c-9f47-4b4c05cc06da", artists[0].mbid)
		assertEquals("e2ef1279-ee73-4b6c-9f47-4b4c05cc06da", artists[0].id)
	}

	@Test
	fun skipsEntriesWithoutNameOrMbid() {
		val artists = json
			.parseToJsonElement("""[
				{"name": "No Mbid"},
				{"artist_mbid": "e2ef1279-ee73-4b6c-9f47-4b4c05cc06da"},
				{"artist_mbid": "e2ef1279-ee73-4b6c-9f47-4b4c05cc06da", "name": "Boards of Canada"}
			]""".trimIndent()).jsonArray
			.mapNotNull { similarArtistOf(it.jsonObject) }

		assertEquals(1, artists.size)
		assertEquals("Boards of Canada", artists.single().name)
		assertTrue(artists.single().mbid != null)
	}

	@Test
	fun parsesFreshRelease() {
		val album = freshReleaseAlbumOf(json.parseToJsonElement("""{
			"release_group_mbid": "19cfbf79-cc83-4fa5-9571-24b35040442d",
			"release_name": "Some Album",
			"artist_credit_name": "Some Artist",
			"artist_mbids": ["e2ef1279-ee73-4b6c-9f47-4b4c05cc06da"],
			"release_group_primary_type": "Album",
			"caa_id": 46358373164,
			"caa_release_mbid": "544589e6-22ed-4bf9-ad5d-d21117b75758"
		}""".trimIndent()).jsonObject)

		assertNotNull(album)
		assertEquals("19cfbf79-cc83-4fa5-9571-24b35040442d", album.mbid)
		assertEquals("19cfbf79-cc83-4fa5-9571-24b35040442d", album.id)
		assertEquals("Some Album", album.title)
		assertEquals("Some Artist", album.artist)
		assertEquals(
			"https://coverartarchive.org/release/544589e6-22ed-4bf9-ad5d-d21117b75758/46358373164-250.jpg",
			album.coverUrl
		)
	}

	@Test
	fun freshReleaseWithoutCoverStillMaps() {
		val album = freshReleaseAlbumOf(json.parseToJsonElement("""{
			"release_group_mbid": "19cfbf79-cc83-4fa5-9571-24b35040442d",
			"release_name": "Some Album",
			"artist_credit_name": "Some Artist"
		}""".trimIndent()).jsonObject)

		assertNotNull(album)
		assertNull(album.coverUrl)
		assertEquals("Some Album", album.title)
	}

	@Test
	fun freshReleaseWithoutIdentityIsNull() {
		assertNull(freshReleaseAlbumOf(json.parseToJsonElement("""{"release_name": "Only Title"}""".trimIndent()).jsonObject))
	}
}
