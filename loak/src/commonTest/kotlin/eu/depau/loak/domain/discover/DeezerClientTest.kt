package eu.depau.loak.domain.discover

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeezerClientTest {
	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun parsesQuotaErrorPayload() {
		val error = deezerErrorOf(
			json.parseToJsonElement("""{
				"error": {"type": "Exception", "message": "Quota limit exceeded", "code": 4}
			}""".trimIndent()).jsonObject
		)
		assertNotNull(error)
		assertEquals("Exception", error.type)
		assertEquals(4, error.code)
		assertEquals("Quota limit exceeded", error.errorMsg)
	}

	@Test
	fun parsesOtherErrorPayload() {
		val error = deezerErrorOf(
			json.parseToJsonElement("""{"error":{"type":"ParamException","code":800,"message":"No query"}}""")
				.jsonObject
		)
		assertNotNull(error)
		assertEquals("ParamException", error.type)
		assertEquals(800, error.code)
		assertEquals("No query", error.errorMsg)
	}

	@Test
	fun successfulPayloadHasNoError() {
		val root = json.parseToJsonElement("""{"data":[{"id":3135556,"title":"Believer"}],"total":1}""").jsonObject
		assertNull(deezerErrorOf(root))
	}

	@Test
	fun parsesTrackSearchResult() {
		val song = discoverSongOf(
			json.parseToJsonElement("""{
				"id": 3135556,
				"title": "Believer",
				"duration": 204,
				"preview": "https://cdns-preview-5.dzcdn.net/preview.mp3",
				"isrc": "USUM71703861",
				"artist": {"id": 8368, "name": "Imagine Dragons"},
				"album": {
					"id": 5257032,
					"title": "Evolve",
					"cover_medium": "https://e-cdns-images.dzcdn.net/images/cover/abc/500x500-000000-80-0-0.jpg"
				}
			}""".trimIndent()).jsonObject
		)
		assertEquals("3135556", song.id)
		assertEquals("Believer", song.title)
		assertEquals("Imagine Dragons", song.artist)
		assertEquals("Evolve", song.album)
		assertEquals(204, song.duration)
		assertEquals("https://cdns-preview-5.dzcdn.net/preview.mp3", song.previewUrl)
		assertEquals("USUM71703861", song.isrc)
		assertEquals(3135556L, song.deezerId)
		assertTrue(song.mbid == null)
	}

	@Test
	fun parsesAlbumSearchResult() {
		val album = discoverAlbumOf(
			json.parseToJsonElement("""{
				"id": 5257032,
				"title": "Evolve",
				"release_date": "2017-06-23",
				"record_type": "album",
				"artist": {"id": 8368, "name": "Imagine Dragons"},
				"cover_medium": "https://e-cdns-images.dzcdn.net/images/cover/abc/500x500.jpg"
			}""".trimIndent()).jsonObject
		)
		assertEquals("5257032", album.id)
		assertEquals("Evolve", album.title)
		assertEquals("Imagine Dragons", album.artist)
		assertEquals(2017, album.year)
		assertEquals("album", album.recordType)
		assertEquals(5257032L, album.deezerId)
	}

	@Test
	fun parsesArtistSearchResult() {
		val artist = discoverArtistOf(
			json.parseToJsonElement("""{
				"id": 8368,
				"name": "Imagine Dragons",
				"picture_medium": "https://e-cdns-images.dzcdn.net/images/artist/xyz/250x250.jpg"
			}""".trimIndent()).jsonObject
		)
		assertEquals("8368", artist.id)
		assertEquals("Imagine Dragons", artist.name)
		assertEquals(8368L, artist.deezerId)
		assertEquals("https://e-cdns-images.dzcdn.net/images/artist/xyz/250x250.jpg", artist.pictureUrl)
	}

	@Test
	fun missingFieldsFallBackToDefaults() {
		val song = discoverSongOf(json.parseToJsonElement("""{"id": 1, "title": "X"}""").jsonObject)
		assertEquals("1", song.id)
		assertEquals("X", song.title)
		assertTrue(song.artist.isEmpty())
		assertEquals(0, song.duration)
		assertNull(song.album)
	}

	@Test
	fun deezerErrorIsRejectedBySearchParsers() {
		// an error payload would never reach the per-item parsers, but a stray "error" key
		// inside a result is ignored rather than parsed as fields
		val artist = discoverArtistOf(
			json.parseToJsonElement("""{"id": 8368, "name": "Imagine Dragons", "error": "x"}""").jsonObject
		)
		assertEquals("Imagine Dragons", artist.name)
	}
}
