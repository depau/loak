package eu.depau.loak.domain.discover

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
