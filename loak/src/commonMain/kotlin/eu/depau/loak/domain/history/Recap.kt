package eu.depau.loak.domain.history

import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_recap_all
import eu.depau.loak.generated.resources.option_recap_day
import eu.depau.loak.generated.resources.option_recap_month
import eu.depau.loak.generated.resources.option_recap_week
import eu.depau.loak.generated.resources.option_recap_year
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration
import kotlin.time.Instant

/** The stretch of time a Recap covers. */
enum class RecapPeriod(val displayName: StringResource) {
	Day(Res.string.option_recap_day),
	Week(Res.string.option_recap_week),
	Month(Res.string.option_recap_month),
	Year(Res.string.option_recap_year),
	All(Res.string.option_recap_all)
}

/** A half-open span of epoch seconds, `[start, end)`. */
data class RecapSpan(val start: Long, val end: Long) {
	operator fun contains(at: Long) = at >= start && at < end
}

/**
 * The spans a Recap is built from: the one on screen, the one before it to compare against, and
 * [activity], the fixed window the activity grid shows. [previous] is null for [RecapPeriod.All],
 * which has nothing before it.
 */
data class RecapSpans(
	val period: RecapPeriod,
	val current: RecapSpan,
	val previous: RecapSpan?,
	/** Always the same [ACTIVITY_DAYS] days ending with [current]: the grid does not resize. */
	val activity: RecapSpan
)

/** What a Recap adds up to, over a span. */
data class RecapTotals(
	val plays: Int,
	/** Summed song durations: what was listened to, not wall-clock time with the screen open. */
	val listened: Duration,
	val tracks: Int,
	val artists: Int,
	/** Artists not heard in the span before this one; null when there is nothing to compare to. */
	val newArtists: Int?
)

/** One artist, album or track of a Recap, with how much of the span it accounts for. */
data class RecapEntry(
	val id: String,
	val title: String,
	/** The artist for an album, "artist • album" for a track, null for an artist. */
	val subtitle: String?,
	val coverArtId: String?,
	val plays: Int,
	/** The song itself, for a track: its row plays it and opens its sheet, like any song row. */
	val song: DomainSong? = null
)

/** One day of the activity grid. [inPeriod] marks the days the Recap's own span covers. */
data class RecapDay(val date: LocalDate, val plays: Int, val inPeriod: Boolean)

/** Everything the Recap screen shows. */
data class RecapData(
	val period: RecapPeriod,
	/** The id of the source the history came from, e.g. "local" or "local+koito". */
	val sourceId: String,
	/**
	 * Sources the user turned on that could not be read at all, by id. The figures are missing
	 * them, so the screen says so rather than quietly narrowing to what answered.
	 */
	val unreadable: List<String>,
	val totals: RecapTotals,
	val previous: RecapTotals?,
	/**
	 * Plays per day over [ACTIVITY_DAYS] days, oldest first, ending today: the same grid whatever
	 * the period is, with the period's own days marked rather than the grid resized to it.
	 */
	val activity: List<RecapDay>,
	val topArtists: List<RecapEntry>,
	val topAlbums: List<RecapEntry>,
	val topTracks: List<RecapEntry>
)

private const val TOP_ARTISTS = 5
private const val TOP_ALBUMS = 5
private const val TOP_TRACKS = 6

/** The days the activity grid always shows: 27 weeks, as the design has it. */
private const val ACTIVITY_DAYS = 27 * 7

/**
 * The spans [period] covers, counting back from [now], and the span before it. [All] runs back
 * to [oldest] — the oldest play any source knows about — and has nothing before it.
 *
 * Days, weeks, months and years start where a calendar says they do in [zone], not a fixed
 * number of hours back, so "this month" means the month the user is in.
 */
fun recapSpans(period: RecapPeriod, now: Instant, zone: TimeZone, oldest: Instant): RecapSpans {
	val date = now.toLocalDateTime(zone).date
	val today = date.atStartOfDayIn(zone).toEpochMilliseconds() / 1000
	fun at(date: LocalDate) = date.atStartOfDayIn(zone).toEpochMilliseconds() / 1000
	// a play is stored as whole seconds: the span runs to the end of the second it ends on, so
	// one recorded in the very second a Recap is asked for counts as inside it
	val end = now.toEpochMilliseconds() / 1000 + 1

	val current = when (period) {
		RecapPeriod.Day -> RecapSpan(today, end)
		RecapPeriod.Week -> at(date.minus(date.dayOfWeek.ordinal, DateTimeUnit.DAY)).let { RecapSpan(it, end) }
		RecapPeriod.Month -> at(LocalDate(date.year, date.month, 1)).let { RecapSpan(it, end) }
		RecapPeriod.Year -> at(LocalDate(date.year, 1, 1)).let { RecapSpan(it, end) }
		RecapPeriod.All -> RecapSpan(oldest.toEpochMilliseconds() / 1000, end)
	}

	val previous = when (period) {
		RecapPeriod.Day -> RecapSpan(current.start - DAY, current.start)
		RecapPeriod.Week -> RecapSpan(current.start - 7 * DAY, current.start)
		RecapPeriod.Month -> {
			val first = LocalDate(date.year, date.month, 1)
			RecapSpan(at(first.minus(1, DateTimeUnit.MONTH)), at(first))
		}
		RecapPeriod.Year -> {
			val first = LocalDate(date.year, 1, 1)
			RecapSpan(at(first.minus(1, DateTimeUnit.YEAR)), at(first))
		}
		RecapPeriod.All -> null
	}
	return RecapSpans(
		period = period,
		current = current,
		previous = previous,
		// the grid is the same width whatever the period picked; only the period's own days
		// are picked out of it, so it always asks for the whole window
		activity = RecapSpan(at(date.minus(ACTIVITY_DAYS - 1, DateTimeUnit.DAY)), end)
	)
}

/**
 * The span every source has to be asked about to answer every part of a Recap: one call per
 * source, split afterwards, rather than one per part.
 */
val RecapSpans.fetch: RecapSpan
	get() = RecapSpan(minOf(current.start, activity.start, previous?.start ?: current.start), current.end)

/**
 * A Recap, as a pure function of the plays and the library — testable without a database, a
 * clock or the network, like [pickQuickPicks].
 *
 * [listens] is every play in [spans].fetch by library song id, [songs] the library entries those
 * ids resolve to. Plays whose song is no longer in the library are counted in nothing: there is
 * no title or artist to show them under.
 */
fun buildRecap(
	listens: Map<String, List<Long>>,
	songs: Map<String, DomainSong>,
	spans: RecapSpans,
	zone: TimeZone,
	sourceId: String,
	unreadable: List<String> = emptyList()
): RecapData {
	fun playsIn(span: RecapSpan?) = span?.let { s ->
		listens.asSequence()
			.flatMap { (songId, at) -> at.asSequence().filter { it in s }.map { songId to it } }
			.filter { it.first in songs }
			.toList()
	}.orEmpty()

	val current = playsIn(spans.current)
	val previous = playsIn(spans.previous)
	val activity = playsIn(spans.activity)

	fun totals(plays: List<Pair<String, Long>>, withNewArtists: Boolean): RecapTotals {
		val duration = plays.fold(Duration.ZERO) { sum, play -> sum + songs.getValue(play.first).duration }
		val artistsNow = plays.mapNotNull { songs[it.first]?.artistId }.toSet()
		return RecapTotals(
			plays = plays.size,
			listened = duration,
			tracks = plays.mapTo(HashSet()) { it.first }.size,
			artists = artistsNow.size,
			newArtists = if (!withNewArtists) null else {
				val before = previous.mapNotNull { songs[it.first]?.artistId }.toSet()
				(artistsNow - before).size
			}
		)
	}

	val previousTotals = spans.previous?.let { totals(previous, withNewArtists = false) }

	/** The top [count] of a grouping, biggest first. */
	fun top(count: Int, key: (DomainSong) -> String?, entry: (DomainSong, Int) -> RecapEntry): List<RecapEntry> {
		val counts = HashMap<String, Int>()
		val byKey = HashMap<String, DomainSong>()
		for ((songId, _) in current) {
			val song = songs.getValue(songId)
			val k = key(song) ?: continue
			counts[k] = (counts[k] ?: 0) + 1
			byKey.getOrPut(k) { song }
		}
		return counts.entries
			.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
			.take(count)
			.map { (k, plays) -> entry(byKey.getValue(k), plays) }
	}

	return RecapData(
		period = spans.period,
		sourceId = sourceId,
		unreadable = unreadable,
		totals = totals(current, withNewArtists = true),
		previous = previousTotals,
		activity = activityGrid(activity, spans, zone),
		topArtists = top(TOP_ARTISTS, key = { it.artistId }) { song, plays ->
			RecapEntry(song.artistId, song.artistName ?: song.artistId, subtitle = null, coverArtId = null, plays = plays)
		},
		topAlbums = top(TOP_ALBUMS, key = { it.albumId }) { song, plays ->
			RecapEntry(
				song.albumId ?: song.albumTitle.orEmpty(),
				song.albumTitle.orEmpty(),
				subtitle = song.artistName,
				coverArtId = song.coverArtId,
				plays = plays
			)
		},
		topTracks = top(TOP_TRACKS, key = { it.id }) { song, plays ->
			RecapEntry(
				song.id,
				song.title,
				subtitle = listOfNotNull(song.artistName, song.albumTitle).joinToString(" • "),
				coverArtId = song.coverArtId,
				plays = plays,
				song = song
			)
		}
	)
}

/**
 * Plays per day over [RecapSpans.activity], oldest first. Every period gets the same grid — a
 * Day or a Week Recap would otherwise be a handful of squares floating at the left edge — and the
 * days of the period itself come back flagged. A day with no play — long before the play log's
 * retention, or between two of a remote source's listens — is a zero.
 */
private fun activityGrid(
	plays: List<Pair<String, Long>>,
	spans: RecapSpans,
	zone: TimeZone
): List<RecapDay> {
	val first = Instant.fromEpochSeconds(spans.activity.start).toLocalDateTime(zone).date
	val startDate = Instant.fromEpochSeconds(spans.current.start).toLocalDateTime(zone).date
	val endDate = Instant.fromEpochSeconds(spans.current.end).toLocalDateTime(zone).date
	val byDate = plays.groupingBy { Instant.fromEpochSeconds(it.second).toLocalDateTime(zone).date }
		.eachCount()
	return (0 until ACTIVITY_DAYS).map { offset ->
		val date = first.plus(offset, DateTimeUnit.DAY)
		RecapDay(date, byDate[date] ?: 0, inPeriod = date >= startDate && date <= endDate)
	}
}

private const val DAY = 86_400L
