package eu.depau.loak.domain.history

import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainSong
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * [buildRecap] and [recapSpans] are pure, so the numbers, the calendar and the grid can be
 * checked without a database, a clock or a network.
 */
class RecapTest {
	private val zone = TimeZone.UTC

	/** 2024-03-10 12:00 UTC, a Sunday, mid-month. */
	private val now = Instant.parse("2024-03-10T12:00:00Z")

	/** A library song with only the fields a Recap looks at filled in. */
	private fun song(id: String, artist: String, album: String, duration: kotlin.time.Duration = 3.minutes) =
		DomainSong(
			id = id,
			title = "Track $id",
			artistName = artist,
			artistId = "artist-$artist",
			albumTitle = album,
			albumId = "album-$album",
			parentId = null,
			comment = null,
			trackNumber = 1,
			discNumber = 1,
			isrc = emptyList(),
			year = 2024,
			genre = null,
			genres = emptyList(),
			moods = emptyList(),
			duration = duration,
			bpm = null,
			contributors = emptyList(),
			userRating = null,
			averageRating = null,
			bitRate = null,
			bitDepth = null,
			sampleRate = null,
			audioChannelCount = null,
			replayGain = null,
			fileSize = 0,
			fileExtension = null,
			mimeType = null,
			filePath = null,
			starredAt = null,
			coverArtId = "cover-$id",
			musicBrainzId = null,
			explicitStatus = DomainExplicitStatus.Unknown,
			artists = emptyList(),
			albumArtists = emptyList()
		)

	private val songs = mapOf(
		"s1" to song("s1", "A", "One"),
		"s2" to song("s2", "A", "One"),
		"s3" to song("s3", "B", "Two"),
		"s4" to song("s4", "C", "Three")
	)

	@Test
	fun `a play in the second the span ends on is inside it`() {
		val spans = recapSpans(RecapPeriod.Day, now, zone, oldest = now)
		val recap = buildRecap(
			// recorded the same second the Recap is asked for: the log keeps whole seconds
			listens = mapOf("s1" to listOf(now.epochSeconds)),
			songs = songs,
			spans = spans,
			zone = zone,
			sourceId = "local"
		)

		assertEquals(1, recap.totals.plays)
	}

	@Test
	fun `week runs from monday and compares with the week before`() {
		val spans = recapSpans(RecapPeriod.Week, now, zone, oldest = now)

		// 2024-03-04 is the Monday of that week
		assertEquals(Instant.parse("2024-03-04T00:00:00Z").epochSeconds, spans.current.start)
		// to the end of the second it is asked in, so a play recorded right now is inside
		assertEquals(now.epochSeconds + 1, spans.current.end)
		// the Monday before it
		assertEquals(Instant.parse("2024-02-26T00:00:00Z").epochSeconds, spans.previous?.start)
		// one call covers both halves
		assertEquals(spans.previous!!.start, spans.fetch.start)
		assertEquals(spans.current.end, spans.fetch.end)
	}

	@Test
	fun `month and year start on the calendar and all has nothing before it`() {
		val month = recapSpans(RecapPeriod.Month, now, zone, oldest = now)
		assertEquals(Instant.parse("2024-03-01T00:00:00Z").epochSeconds, month.current.start)
		assertEquals(Instant.parse("2024-02-01T00:00:00Z").epochSeconds, month.previous?.start)

		val year = recapSpans(RecapPeriod.Year, now, zone, oldest = now)
		assertEquals(Instant.parse("2024-01-01T00:00:00Z").epochSeconds, year.current.start)
		assertEquals(Instant.parse("2023-01-01T00:00:00Z").epochSeconds, year.previous?.start)

		val all = recapSpans(RecapPeriod.All, now, zone, oldest = Instant.parse("2020-01-01T00:00:00Z"))
		assertEquals(Instant.parse("2020-01-01T00:00:00Z").epochSeconds, all.current.start)
		assertNull(all.previous)
	}

	@Test
	fun `totals count plays, distinct tracks, artists and duration`() {
		val spans = recapSpans(RecapPeriod.Week, now, zone, oldest = now)
		val recap = buildRecap(
			listens = mapOf(
				// this week: s1 twice, s2 once; s3 once, plus once last week
				"s1" to listOf(epoch("2024-03-05T10:00:00Z"), epoch("2024-03-06T10:00:00Z")),
				"s2" to listOf(epoch("2024-03-05T11:00:00Z")),
				"s3" to listOf(epoch("2024-03-09T11:00:00Z"), epoch("2024-02-27T11:00:00Z")),
				// a song no longer in the library is counted in nothing
				"gone" to listOf(epoch("2024-03-05T12:00:00Z"))
			),
			songs = songs,
			spans = spans,
			zone = zone,
			sourceId = "local"
		)

		assertEquals(4, recap.totals.plays)
		assertEquals(3, recap.totals.tracks)
		assertEquals(2, recap.totals.artists)
		assertEquals(12.minutes, recap.totals.listened)
		// A is new this week; B was heard last week too
		assertEquals(1, recap.totals.newArtists)
		assertEquals(1, recap.previous?.plays)
	}

	@Test
	fun `tops are ranked by plays and tie-broken by name`() {
		val spans = recapSpans(RecapPeriod.Week, now, zone, oldest = now)
		val recap = buildRecap(
			listens = mapOf(
				"s1" to listOf(epoch("2024-03-05T10:00:00Z"), epoch("2024-03-06T10:00:00Z")),
				"s2" to listOf(epoch("2024-03-05T11:00:00Z")),
				"s3" to listOf(epoch("2024-03-08T11:00:00Z")),
				"s4" to listOf(epoch("2024-03-07T11:00:00Z"))
			),
			songs = songs,
			spans = spans,
			zone = zone,
			sourceId = "local"
		)

		assertEquals(listOf("artist-A", "artist-B", "artist-C"), recap.topArtists.map { it.id })
		assertEquals(3, recap.topArtists.first().plays)
		assertEquals(listOf("album-One", "album-Three", "album-Two"), recap.topAlbums.map { it.id })
		assertEquals(listOf("s1", "s2", "s3", "s4"), recap.topTracks.map { it.id })
	}

	@Test
	fun `the activity grid is one square per day of the span`() {
		val spans = recapSpans(RecapPeriod.Week, now, zone, oldest = now)
		val recap = buildRecap(
			listens = mapOf(
				"s1" to listOf(epoch("2024-03-05T10:00:00Z"), epoch("2024-03-05T18:00:00Z")),
				"s2" to listOf(epoch("2024-03-09T11:00:00Z"))
			),
			songs = songs,
			spans = spans,
			zone = zone,
			sourceId = "local"
		)

		// Monday 2024-03-04 to Sunday 2024-03-10
		assertEquals(7, recap.activity.size)
		assertEquals(LocalDate(2024, 3, 4), recap.activity.first().date)
		assertEquals(listOf(0, 2, 0, 0, 0, 1, 0), recap.activity.map { it.plays })
	}

	@Test
	fun `the activity grid is capped and ends on the last day of the span`() {
		val spans = recapSpans(RecapPeriod.All, now, zone, oldest = Instant.parse("2015-01-01T00:00:00Z"))
		val recap = buildRecap(
			listens = mapOf("s1" to listOf(epoch("2024-03-05T10:00:00Z"))),
			songs = songs,
			spans = spans,
			zone = zone,
			sourceId = "local"
		)

		assertEquals(27 * 7, recap.activity.size)
		assertEquals(LocalDate(2024, 3, 10), recap.activity.last().date)
		// the single play falls on 2024-03-05, five squares from the end
		assertEquals(1, recap.activity[recap.activity.lastIndex - 5].plays)
	}

	private fun epoch(at: String) = Instant.parse(at).epochSeconds
}
