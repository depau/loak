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
	fun previewMatchPrefersExactArtistOverEarlierTitleMatch() {
		val wrongArtist = DiscoverSong("1", "The Motto", "Cover Artist", previewUrl = "wrong")
		val exact = DiscoverSong("2", "The Motto", "Tiësto & Ava Max", previewUrl = "right")

		assertEquals(exact, previewMatch(listOf(wrongArtist, exact), "The Motto", "Tiesto & Ava Max"))
	}

	@Test
	fun previewMatchPrefersUnversionedTitleWhenArtistCreditsDiffer() {
		val remix = DiscoverSong("1", "The Motto (VIP Mix)", "Tiësto", previewUrl = "remix")
		val original = DiscoverSong("2", "The Motto", "Tiësto", previewUrl = "original")

		assertEquals(original, previewMatch(listOf(remix, original), "The Motto", "Tiësto & Ava Max"))
	}

	@Test
	fun previewMatchFallsBackToTitleWhenCreditsDiffer() {
		val coldplay = DiscoverSong("1", "A Sky Full of Stars", "Coldplay", previewUrl = "preview")

		assertEquals(coldplay, previewMatch(listOf(coldplay), "A Sky Full of Stars", "Coldplay & Rihanna"))
		assertNull(previewMatch(listOf(coldplay), "Yellow", "Coldplay"))
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

	@Test
	fun parsesAlbumDetailWithTracks() {
		val root = json.parseToJsonElement("""{
			"id": 302127,
			"title": "Discovery",
			"upc": "724384960650",
			"release_date": "2001-03-03",
			"record_type": "album",
			"cover_medium": "https://e-cdns-images.dzcdn.net/images/cover/ab/500x500.jpg",
			"artist": {"id": 27, "name": "Daft Punk"},
			"tracks": {
				"data": [
					{
						"id": 3135553,
						"title": "One More Time",
						"duration": 320,
						"preview": "https://cdnt-preview.dzcdn.net/preview.mp3",
						"isrc": "GBDUW0000053",
						"artist": {"id": 27, "name": "Daft Punk"}
					},
					{
						"id": 3135554,
						"title": "Aerodynamic",
						"duration": 224,
						"preview": null,
						"artist": {"id": 27, "name": "Daft Punk"}
					}
				]
			}
		}""".trimIndent()).jsonObject

		val (album, tracks) = discoverAlbumWithTracksOf(root)
		assertEquals("302127", album.id)
		assertEquals("Discovery", album.title)
		assertEquals("Daft Punk", album.artist)
		assertEquals(2001, album.year)
		assertEquals("album", album.recordType)
		assertEquals("724384960650", album.upc)
		assertEquals(302127L, album.deezerId)
		assertEquals(27L, album.artistDeezerId)

		assertEquals(2, tracks.size)
		assertEquals("One More Time", tracks[0].title)
		assertEquals("Daft Punk", tracks[0].artist)
		assertEquals(320, tracks[0].duration)
		assertEquals("https://cdnt-preview.dzcdn.net/preview.mp3", tracks[0].previewUrl)
		assertEquals("GBDUW0000053", tracks[0].isrc)
		assertEquals(3135553L, tracks[0].deezerId)
		assertNull(tracks[1].previewUrl)
		// album tracks carry no album of their own: they get the page's
		assertEquals("Discovery", tracks[1].album)
		assertEquals(302127L, tracks[1].albumDeezerId)
		assertEquals(album.coverUrl, tracks[1].coverUrl)

		// an album payload without a tracks section yields an empty tracklist
		val (bareAlbum, bareTracks) = discoverAlbumWithTracksOf(
			json.parseToJsonElement("""{"id": 1, "title": "X", "artist": {"name": "A"}}""").jsonObject
		)
		assertEquals("X", bareAlbum.title)
		assertTrue(bareTracks.isEmpty())
	}

	private fun chartsJson(entries: String) =
		json.parseToJsonElement("""{"data": [$entries]}""".trimIndent()).jsonObject

	@Test
	fun parsesCountryChartsAndDropsVariants() {
		val fake = """{"id": 777, "title": "Top Fake"}"""
		val charts = parseCountryCharts(chartsJson("""
			{"id": 1362508955, "title": "Top Japan"},
			{"id": 1116190041, "title": "Top Spain"},
			{"id": 1266971851, "title": "Top Netherlands"},
			{"id": 1116187241, "title": "Top Italy"},
			{"id": 3155776842, "title": "Top Worldwide"},
			{"id": 11846226041, "title": "Top France 2023"},
			{"id": 14591920041, "title": "Top Argentina 2025"},
			{"id": 13578432561, "title": "Top Mujeres Argentina"},
			{"id": 13578432881, "title": "Top Mulheres Brasil"},
			{"id": 13568614081, "title": "Top Women Germany"},
			{"id": 13562522521, "title": "Top Women Worldwide"},
			{"id": 13560166481, "title": "Top Femmes France"},
			{"id": 10496059222, "title": "Top SongCatcher"},
			{"id": 12653857903, "title": "Top Songcatcher Brazil"},
			{"id": 1314389855, "title": "Top Atlantis 2019"},
			$fake
		""".trimIndent()))

		val expected = mapOf(
			"Japan" to "JP",
			"Spain" to "ES",
			"Netherlands" to "NL",
			"Italy" to "IT",
			"Worldwide" to "XW"
		)
		assertEquals(expected.keys, charts.map { it.countryName }.toSet())
		for (chart in charts) {
			assertEquals(expected.getValue(chart.countryName), chart.countryCode)
			assertTrue(chart.playlistId > 0)
		}
		// playlist ids still line up by country
		assertEquals(1116187241L, charts.first { it.countryName == "Italy" }.playlistId)
		assertEquals(3155776842L, charts.first { it.countryName == "Worldwide" }.playlistId)
	}

	@Test
	fun countryNameMapsToIso() {
		assertEquals("IT", countryNameToIso("Italy"))
		assertEquals("US", countryNameToIso("USA"))
		assertEquals("ZA", countryNameToIso("South Africa"))
		assertEquals("AE", countryNameToIso("United Arab Emirates"))
		assertEquals("KR", countryNameToIso("South Korea"))
		assertEquals("GB", countryNameToIso("UK"))
		assertEquals("XW", countryNameToIso("Worldwide"))
		assertNull(countryNameToIso("Italy 2025"))
		assertNull(countryNameToIso("Mujeres Argentina"))
		assertNull(countryNameToIso("Atlantis"))
	}

	@Test
	fun isoMapsBackToCountryName() {
		assertEquals("Italy", isoCountryName("IT"))
		assertEquals("Worldwide", isoCountryName("XW"))
		assertNull(isoCountryName("ZZ"))
	}

	@Test
	fun parsesRadioResult() {
		val radio = discoverRadioOf(
			json.parseToJsonElement("""{
				"id": 30991,
				"title": "Hip Hop",
				"picture_medium": "https://e-cdns-images.dzcdn.net/images/misc/abc/250x250-000000-80-0-0.jpg"
			}""".trimIndent()).jsonObject
		)
		assertEquals(30991L, radio.id)
		assertEquals("Hip Hop", radio.title)
		assertEquals("https://e-cdns-images.dzcdn.net/images/misc/abc/250x250-000000-80-0-0.jpg", radio.pictureUrl)
	}

	@Test
	fun parsesPlaylistResult() {
		val playlist = discoverPlaylistOf(
			json.parseToJsonElement("""{
				"id": 9346933942,
				"title": "Acoustic Hits",
				"nb_tracks": 80,
				"picture_medium": "https://cdn-images.dzcdn.net/images/playlist/abc/250x250-000000-80-0-0.jpg"
			}""".trimIndent()).jsonObject
		)
		assertEquals(9346933942L, playlist.id)
		assertEquals("Acoustic Hits", playlist.title)
		assertEquals(80, playlist.trackCount)
		assertEquals("https://cdn-images.dzcdn.net/images/playlist/abc/250x250-000000-80-0-0.jpg", playlist.pictureUrl)
	}

	@Test
	fun missingRadioFieldsFallBackToDefaults() {
		val radio = discoverRadioOf(
			json.parseToJsonElement("""{"id": 5}""".trimIndent()).jsonObject
		)
		assertEquals(5L, radio.id)
		assertEquals("", radio.title)
		assertNull(radio.pictureUrl)
	}
}
