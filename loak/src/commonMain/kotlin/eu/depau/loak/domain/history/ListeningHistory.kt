package eu.depau.loak.domain.history

import eu.depau.loak.domain.manager.PlayLogManager
import kotlin.math.pow
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * What a listening history source knows about what was played, and when.
 *
 * Sources answer in *library song ids*: translating a source's own idea of a track into the
 * library ([eu.depau.loak.domain.discover.LibraryMatcher]) stays inside them, so neither Quick
 * picks nor the Recap ever sees an integration.
 */
interface ListeningHistory {
	/** For settings, logs and the recap's "from …" label. */
	val id: String

	/**
	 * Song id to the epoch seconds of each play in `[from, to)`. Empty (never an error) when the
	 * source is not configured, is unreachable, or matched nothing: a source with nothing to say
	 * must leave the app working on the sources below it.
	 */
	suspend fun listens(from: Long, to: Long): Map<String, List<Long>>

	/**
	 * Each play in the [window] ending now, halved every [halfLife], by song id. This is what
	 * Quick picks ranks on: a burst of plays counts for a lot while it is fresh and fades over
	 * days rather than disappearing the moment the raw count drops.
	 */
	suspend fun heat(window: Duration, halfLife: Duration): Map<String, Double> {
		val now = Clock.System.now().epochSeconds
		return decayedHeat(listens(now - window.inWholeSeconds, now), now, window, halfLife)
	}
}

/** This device's own plays, out of the play log. Always available, needs no configuration. */
class LocalHistory(private val playLog: PlayLogManager) : ListeningHistory {
	override val id = "local"

	override suspend fun listens(from: Long, to: Long): Map<String, List<Long>> =
		playLog.listens(from, to)
}

/**
 * [songId] to the epoch seconds of each play, folded into a decayed count: a play now is 1.0,
 * one [halfLife] ago 0.5, two half-lives 0.25. Plays older than [window] are dropped, so the
 * window is what a source can still be asked about and the half-life is how fast it forgets.
 *
 * Shared by the local play log and every remote source, so a listen ListenBrainz reports and
 * one this device recorded weigh exactly the same.
 */
fun decayedHeat(
	plays: Map<String, List<Long>>,
	now: Long,
	window: Duration,
	halfLife: Duration
): Map<String, Double> {
	val cutoff = now - window.inWholeSeconds
	val half = halfLife.inWholeSeconds.coerceAtLeast(1)
	return plays.mapValues { (_, at) ->
		at.sumOf { seconds -> if (seconds < cutoff) 0.0 else 2.0.pow(-(now - seconds).toDouble() / half) }
	}.filterValues { it > 0.0 }
}
