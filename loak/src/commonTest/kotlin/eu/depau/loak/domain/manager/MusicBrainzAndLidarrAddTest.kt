package eu.depau.loak.domain.manager

import eu.depau.loak.domain.discover.selectDefaultReleaseGroup
import eu.depau.loak.domain.discover.MusicBrainzReleaseGroup
import eu.depau.loak.domain.discover.isSecondary
import eu.depau.loak.domain.discover.songReleasesOf
import eu.depau.loak.domain.discover.musicBrainzAlbumOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Pure-function tests for Milestone M6's backend logic that touches no network:
 * release-group preselection, the `POST /album` recipe bodies, and finding Lidarr's
 * None metadata profile.
 */
class MusicBrainzAndLidarrAddTest {
	private val json = Json { ignoreUnknownKeys = true }

	// ---------------- release preselection ----------------

	private fun rg(
		id: String,
		primaryType: String?,
		secondaryTypes: List<String>? = null,
		date: String? = null
	) = MusicBrainzReleaseGroup(
		id = id,
		title = "t$id",
		score = 100,
		primaryType = primaryType,
		secondaryTypes = secondaryTypes,
		firstReleaseDate = date,
		artistName = "artist"
	)

	@Test
	fun prefersEarliestStudioAlbumOverSinglesAndEps() {
		val picked = selectDefaultReleaseGroup(
			listOf(
				rg("single-new", "Single", date = "2020-01-01"),
				rg("album-later", "Album", date = "2013-05-01"),
				rg("ep", "EP", date = "2018-02-02"),
				rg("album-earlier", "Album", date = "2011-11-11")
			)
		)
		assertNotNull(picked)
		assertEquals("album-earlier", picked.id)
	}

	@Test
	fun fallsBackToSingleThenEpWhenNoStudioAlbum() {
		val noAlbum = selectDefaultReleaseGroup(
			listOf(
				rg("single-later", "Single", date = "2021-01-01"),
				rg("ep", "EP", date = "2018-02-02"),
				rg("single-earlier", "Single", date = "2015-05-05")
			)
		)
		assertNotNull(noAlbum)
		assertEquals("single-earlier", noAlbum.id)

		val onlyEp = selectDefaultReleaseGroup(listOf(rg("ep", "EP", date = "2020-06-06")))
		assertNotNull(onlyEp)
		assertEquals("ep", onlyEp.id)
	}

	@Test
	fun liveOrCompilationAlbumsAreNotStudioAlbums() {
		val picked = selectDefaultReleaseGroup(
			listOf(
				rg("live", "Album", secondaryTypes = listOf("Live"), date = "2010-01-01"),
				rg("compilation", "Album", secondaryTypes = listOf("Compilation"), date = "2012-01-01"),
				rg("studio", "Album", date = "2015-01-01")
			)
		)
		assertNotNull(picked)
		assertEquals("studio", picked.id)
	}

	@Test
	fun emptyListReturnsNull() {
		assertNull(selectDefaultReleaseGroup(emptyList()))
	}

	// ---------------- None metadata profile ----------------

	private fun metadataProfile(name: String, allowed: List<Boolean>) = json.parseToJsonElement(
		"""{"id": 5, "name": "$name", "primaryAlbumTypes": ${allowed.map { """{"allowed": $it}""" }} }"""
	).jsonObject

	@Test
	fun findsProfileWithAllPrimaryTypesDisallowed() {
		val found = noneMetadataProfileOf(
			listOf(
				metadataProfile("Standard", listOf(true, true, true)),
				metadataProfile("None", listOf(false, false, false))
			)
		)
		assertNotNull(found)
		assertEquals("None", found.name)
		assertEquals(5L, found.id)
	}

	@Test
	fun ignoresProfilesWithAnyAllowedTypeAndReturnsNullWhenMissing() {
		val none = noneMetadataProfileOf(
			listOf(
				metadataProfile("Standard", listOf(true, true, true)),
				metadataProfile("Half", listOf(false, true, false))
			)
		)
		assertNull(none)
		assertNull(noneMetadataProfileOf(emptyList()))
	}

	// ---------------- POST /album recipe bodies ----------------

	private fun artist(
		id: Long? = null,
		monitored: Boolean = true,
		monitorNewItems: String = "none",
		monitor: String = "none"
	) = AlbumArtistFields(
		id = id,
		name = "Some Artist",
		qualityProfileId = 3,
		rootFolderPath = "/music",
		metadataProfileId = 1,
		musicBrainzId = "artist-mbid",
		monitored = monitored,
		monitorNewItems = monitorNewItems,
		monitor = monitor
	)

	@Test
	fun switchOffNewArtistBodyHasNoneProfileAndMonitorsDiscography() {
		val body = albumAddBody(artist(monitorNewItems = "none", monitor = "all"), "Album", "album-mbid")

		// album: monitored, no auto-search
		assertEquals("Album", body["title"]?.jsonPrimitive?.content)
		assertEquals("album-mbid", body["foreignAlbumId"]?.jsonPrimitive?.content)
		assertEquals(true, body["monitored"]?.jsonPrimitive?.content?.toBoolean())
		assertEquals(false, body["addOptions"]?.jsonObject?.get("searchForNewAlbum")?.jsonPrimitive?.content?.toBoolean())

		// artist: new (no id), monitored, future releases off -> None-profile style
		val artistObj = body["artist"]?.jsonObject
		assertNotNull(artistObj)
		assertNull(artistObj["id"])
		assertEquals(true, artistObj["monitored"]?.jsonPrimitive?.content?.toBoolean())
		assertEquals("none", artistObj["monitorNewItems"]?.jsonPrimitive?.content)
		assertEquals("all", artistObj["addOptions"]?.jsonObject?.get("monitor")?.jsonPrimitive?.content)
		assertEquals(false, artistObj["addOptions"]?.jsonObject?.get("searchForMissingAlbums")?.jsonPrimitive?.content?.toBoolean())
		assertEquals(0, artistObj["tags"]?.jsonArray?.size)
	}

	@Test
	fun switchOnNewArtistBodyTracksJustTheAddedAlbum() {
		val body = albumAddBody(artist(monitorNewItems = "new", monitor = "none"), "Album", "album-mbid")
		val artistObj = body["artist"]?.jsonObject
		assertNotNull(artistObj)
		assertNull(artistObj["id"])
		assertEquals("new", artistObj["monitorNewItems"]?.jsonPrimitive?.content)
		assertEquals("none", artistObj["addOptions"]?.jsonObject?.get("monitor")?.jsonPrimitive?.content)
	}

	@Test
	fun existingArtistBodyCarriesTheArtistId() {
		val body = albumAddBody(artist(id = 42L, monitored = false), "Album", "album-mbid")
		val artistObj = body["artist"]?.jsonObject
		assertNotNull(artistObj)
		assertEquals(42L, artistObj["id"]?.jsonPrimitive?.content?.toLong())
		assertEquals(false, artistObj["monitored"]?.jsonPrimitive?.content?.toBoolean())
		// an existing artist never re-monitors the discography
		assertEquals("none", artistObj["addOptions"]?.jsonObject?.get("monitor")?.jsonPrimitive?.content)
	}

	// ---------------- a song's releases ----------------

	@Test
	fun songReleasesKeepOfficialReleasesGroupedAndDatedByTheEarliest() {
		val result = json.parseToJsonElement(
			"""{"recordings":[{"score":100,
			"artist-credit":[{"artist":{"id":"a1","name":"deadmau5"}}],
			"releases":[
			 {"status":"Official","date":"2023-06-30","track-count":11,"release-group":{"id":"rg1","title":"FLOABN","primary-type":"Album"}},
			 {"status":"Official","date":"2009-09-22","track-count":10,"release-group":{"id":"rg1","title":"FLOABN","primary-type":"Album"}},
			 {"status":"Promotion","date":"2009-09","release-group":{"id":"rg1","title":"FLOABN","primary-type":"Album"}},
			 {"status":"Official","date":"2010-11-19","track-count":62,"release-group":{"id":"rg2","title":"Superclub","primary-type":"Album","secondary-types":["Compilation","DJ-mix"]}},
			 {"status":"Bootleg","date":"2008","release-group":{"id":"rg3","title":"Boot","primary-type":"Album"}}
			]}]}"""
		).jsonObject
		val releases = songReleasesOf(result)
		assertEquals("a1", releases.artistMbid)
		assertEquals(listOf("rg1", "rg2"), releases.groups.map { it.id })
		assertEquals("2009-09-22", releases.groups[0].firstReleaseDate)
		assertEquals(10, releases.groups[0].trackCount)
		assertEquals("rg1", selectDefaultReleaseGroup(releases.groups)?.id)
		assertEquals(listOf(false, true), releases.groups.map { it.isSecondary() })
	}

	@Test
	fun foldsAReleaseGroupAndItsReleaseIntoAnAlbumPage() {
		val group = json.parseToJsonElement("""
			{"id":"rg1","title":"finir en (...)","primary-type":"Single","first-release-date":"2026-09-30",
			 "artist-credit":[{"name":"A","joinphrase":" & "},{"name":"B"}]}
		""").jsonObject
		val release = json.parseToJsonElement("""
			{"media":[
				{"tracks":[{"id":"t1","title":"One","length":61000,"recording":{"id":"r1","title":"One"}}]},
				{"tracks":[{"id":"t2","title":"One","recording":{"id":"r1","length":2000},
				  "artist-credit":[{"name":"C"}]}]}
			]}
		""").jsonObject
		val (album, tracks) = musicBrainzAlbumOf(group, release)
		assertEquals("A & B", album.artist)
		assertEquals(2026, album.year)
		assertEquals("Single", album.recordType)
		// both media, keyed by track so a repeated recording stays unique
		assertEquals(listOf("t1", "t2"), tracks.map { it.id })
		assertEquals(listOf("r1", "r1"), tracks.map { it.mbid })
		assertEquals(listOf("A & B", "C"), tracks.map { it.artist })
		assertEquals(listOf(61, 2), tracks.map { it.duration })
	}
}
