package eu.depau.loak.domain.history

import eu.depau.loak.domain.discover.listenOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The wire shapes of the three remote sources, as each API actually sends them. These are the
 * part of a source that breaks when the other side changes something, and the part that no
 * amount of local testing catches.
 */
class RemoteListenTest {
	private fun json(body: String): JsonObject = Json.parseToJsonElement(body.trimIndent()) as JsonObject

	// region ListenBrainz

	@Test
	fun listenBrainzListenCarriesIdentityWhenItHasIt() {
		val listen = listenOf(
			json(
				"""
				{"listened_at":1712345678,"track_metadata":{
					"artist_name":"Tame Impala","track_name":"Dracula","release_name":"Deadbeat",
					"additional_info":{"recording_mbid":"5c1f-abc","isrc":"AUUM71400531"}
				}}
				"""
			)
		)
		assertEquals("Dracula", listen?.title)
		assertEquals("Tame Impala", listen?.artist)
		assertEquals(1712345678L, listen?.at)
		assertEquals("5c1f-abc", listen?.mbid)
		assertEquals("AUUM71400531", listen?.isrc)
	}

	@Test
	fun listenBrainzListenWithoutIdentityIsStillUsable() {
		// what a submitter that knows nothing but the names sends
		val listen = listenOf(
			json("""{"listened_at":1712345678,"track_metadata":{"artist_name":"A","track_name":"T"}}""")
		)
		assertEquals("T", listen?.title)
		assertNull(listen?.mbid)
		assertNull(listen?.isrc)
	}

	@Test
	fun listenBrainzNowPlayingIsNotAListen() {
		// the track being played right now has no listened_at until it is submitted
		val listen = listenOf(
			json("""{"track_metadata":{"artist_name":"A","track_name":"T"},"playing_now":true}""")
		)
		assertNull(listen)
	}

	@Test
	fun listenBrainzListenWithoutANameIsDropped() {
		assertNull(listenOf(json("""{"listened_at":1,"track_metadata":{"track_name":"T"}}""")))
		assertNull(listenOf(json("""{"listened_at":1,"track_metadata":{"artist_name":"A"}}""")))
		assertNull(listenOf(json("""{"listened_at":1}""")))
	}

	// endregion

	// region Koito

	@Test
	fun koitoListenReadsItsArtists() {
		val listen = koitoListenOf(
			json(
				"""
				{"time":"2026-10-16T09:30:00Z","track":{"id":41,"title":"Dracula",
				 "artists":[{"id":7,"name":"Tame Impala"}],"image":[]}}
				"""
			)
		)
		assertEquals("Dracula", listen?.title)
		assertEquals("Tame Impala", listen?.artist)
		assertEquals(1_792_143_000L, listen?.at)
		// Koito's listens carry no identifiers at all
		assertNull(listen?.mbid)
		assertNull(listen?.isrc)
	}

	@Test
	fun koitoListenWithoutATrackOrArtistIsDropped() {
		assertNull(koitoListenOf(json("""{"time":"2026-10-16T09:30:00Z"}""")))
		assertNull(koitoListenOf(json("""{"time":"2026-10-16T09:30:00Z","track":{"title":"T","artists":[]}}""")))
		assertNull(koitoListenOf(json("""{"time":"not a time","track":{"title":"T","artists":[{"name":"A"}]}}""")))
	}

	@Test
	fun koitoListenKeepsTheFirstNamedArtist() {
		val listen = koitoListenOf(
			json(
				"""
				{"time":"2026-10-16T09:30:00Z","track":{"title":"T",
				 "artists":[{"id":1},{"id":2,"name":"Second"},{"id":3,"name":"Third"}]}}
				"""
			)
		)
		assertEquals("Second", listen?.artist)
	}

	// endregion

	// region Last.fm

	@Test
	fun lastFmScrobbleReadsTheDateAndArtistText() {
		val listen = lastFmListenOf(
			json(
				"""
				{"artist":{"mbid":"a-mbid","#text":"Tame Impala"},"album":{"mbid":"","#text":"Deadbeat"},
				 "name":"Dracula","mbid":"rec-mbid","date":{"uts":"1712345678","#text":"01 Jan 2024"}}
				"""
			)
		)
		assertEquals("Dracula", listen?.title)
		assertEquals("Tame Impala", listen?.artist)
		assertEquals(1712345678L, listen?.at)
		assertEquals("rec-mbid", listen?.mbid)
	}

	@Test
	fun lastFmNowPlayingIsNotAListen() {
		// "now playing" carries @attr and no date
		val listen = lastFmListenOf(
			json("""{"artist":{"#text":"A"},"name":"T","@attr":{"nowplaying":"true"}}""")
		)
		assertNull(listen)
	}

	@Test
	fun lastFmEmptyMbidIsNoIdentifier() {
		// the API sends "" rather than omitting the field, which is not an id to match on
		val listen = lastFmListenOf(
			json("""{"artist":{"#text":"A"},"name":"T","mbid":"","date":{"uts":"1712345678"}}""")
		)
		assertNull(listen?.mbid)
	}

	@Test
	fun lastFmScrobbleWithoutNamesIsDropped() {
		assertNull(lastFmListenOf(json("""{"artist":{"#text":"A"},"date":{"uts":"1"}}""")))
		assertNull(lastFmListenOf(json("""{"name":"T","date":{"uts":"1"}}""")))
	}

	// endregion
}
