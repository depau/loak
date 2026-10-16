package eu.depau.loak.domain.history

import eu.depau.loak.domain.manager.PlayLogManager
import kotlin.math.pow
import kotlin.time.Duration

/**
 * What a listening history source knows about how much each library song has been on repeat.
 *
 * [heat] is a decayed play count: a play now is 1.0, a play one half-life ago 0.5, two
 * half-lives 0.25, and so on. That is what makes a song you are playing to death lead Quick
 * picks for a few days and then sink back in among its peers instead of vanishing.
 *
 * Implementations answer in *library song ids*: translating a source's own idea of a track
 * into the library ([eu.depau.loak.domain.discover.LibraryMatcher]) stays inside them, so the
 * ranking never sees an integration.
 */
interface ListeningHistory {
	/** For settings, logs and the recap's "from …" label. */
	val id: String

	/**
	 * Song id to decayed plays inside [window]. Empty (never an error) when the source is not
	 * configured, is unreachable, or matched nothing: a source having nothing to say must
	 * leave the app working on the sources below it.
	 */
	suspend fun heat(window: Duration, halfLife: Duration): Map<String, Double>
}

/** This device's own plays, out of the play log. Always available, needs no configuration. */
class LocalHistory(private val playLog: PlayLogManager) : ListeningHistory {
	override val id = "local"

	override suspend fun heat(window: Duration, halfLife: Duration): Map<String, Double> =
		playLog.heat(window, halfLife)
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
