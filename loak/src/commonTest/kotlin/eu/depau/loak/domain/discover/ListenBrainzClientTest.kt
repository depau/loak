package eu.depau.loak.domain.discover

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
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
	fun sendsUserAgentToMainAndLabsApis() = runTest {
		val engine = MockEngine { request ->
			respond(
				if (request.url.host == "labs.api.listenbrainz.org") "[]"
				else """{"payload":{"releases":[]}}"""
			)
		}
		try {
			val client = ListenBrainzClient(engine)
			client.getFreshReleases()
			client.getSimilarArtists("e2ef1279-ee73-4b6c-9f47-4b4c05cc06da")

			assertEquals(
				listOf("api.listenbrainz.org", "labs.api.listenbrainz.org"),
				engine.requestHistory.map { it.url.host }
			)
			for (request in engine.requestHistory) {
				val userAgent = request.headers["User-Agent"]
				assertNotNull(userAgent)
				assertTrue(userAgent.isNotBlank())
				assertEquals(USER_AGENT, userAgent)
			}
		} finally {
			engine.close()
		}
	}

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
			"release_date": "2026-09-30",
			"caa_id": 46358373164,
			"caa_release_mbid": "544589e6-22ed-4bf9-ad5d-d21117b75758"
		}""".trimIndent()).jsonObject)

		assertNotNull(album)
		assertEquals("19cfbf79-cc83-4fa5-9571-24b35040442d", album.mbid)
		assertEquals("19cfbf79-cc83-4fa5-9571-24b35040442d", album.id)
		assertEquals("Some Album", album.title)
		assertEquals("Some Artist", album.artist)
		assertEquals("Album", album.recordType)
		assertEquals(2026, album.year)
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

	@Test
	fun parsesCreatedForPlaylist() {
		val playlist = createdForPlaylistOf(json.parseToJsonElement("""{
			"playlist": {
				"creator": "listenbrainz",
				"identifier": "https://listenbrainz.org/playlist/43de9932-d640-4f9b-aca8-79d97e8d4995",
				"title": "Weekly Exploration for tmsp, week of 2026-10-05 Mon",
				"track": []
			}
		}""".trimIndent()).jsonObject)

		assertNotNull(playlist)
		assertEquals("43de9932-d640-4f9b-aca8-79d97e8d4995", playlist.mbid)
		assertEquals("Weekly Exploration for tmsp, week of 2026-10-05 Mon", playlist.title)
		assertEquals("listenbrainz", playlist.creator)
		assertEquals(0, playlist.trackCount)
	}

	@Test
	fun createdForWithoutMbidIdentifierIsNull() {
		assertNull(createdForPlaylistOf(json.parseToJsonElement("""{
			"playlist": {"identifier": "https://listenbrainz.org/playlist/not-a-mbid", "title": "X"}
		}""".trimIndent()).jsonObject))
	}

	@Test
	fun parsesJspfTrack() {
		val track = listenBrainzTrackOf(json.parseToJsonElement("""{
			"album": "The Way I Are",
			"creator": "Timbaland",
			"extension": {
				"https://musicbrainz.org/doc/jspf#track": {
					"additional_metadata": {
						"caa_id": 46358373164,
						"caa_release_mbid": "544589e6-22ed-4bf9-ad5d-d21117b75758"
					}
				}
			},
			"identifier": ["https://musicbrainz.org/recording/4ae8e623-7c3b-4c3e-aaaa-7fe6c763b058"],
			"title": "The Way I Are"
		}""".trimIndent()).jsonObject)

		assertNotNull(track)
		assertEquals("4ae8e623-7c3b-4c3e-aaaa-7fe6c763b058", track.id)
		assertEquals("4ae8e623-7c3b-4c3e-aaaa-7fe6c763b058", track.mbid)
		assertEquals("The Way I Are", track.title)
		assertEquals("Timbaland", track.artist)
		assertEquals("The Way I Are", track.album)
		assertNull(track.previewUrl)
		assertEquals(
			"https://coverartarchive.org/release/544589e6-22ed-4bf9-ad5d-d21117b75758/46358373164-250.jpg",
			track.coverUrl
		)
	}

	@Test
	fun jspfTrackWithoutRecordingStillMaps() {
		val track = listenBrainzTrackOf(json.parseToJsonElement("""{
			"creator": "Kraftklub & Faber",
			"title": "Sterben in Karl-Marx-Stadt"
		}""".trimIndent()).jsonObject)

		assertNotNull(track)
		assertNull(track.mbid)
		assertEquals("Sterben in Karl-Marx-Stadt Kraftklub & Faber", track.id)
	}

	@Test
	fun jspfTrackWithoutTitleIsNull() {
		assertNull(listenBrainzTrackOf(json.parseToJsonElement("""{"creator": "Nobody"}""".trimIndent()).jsonObject))
	}
}
