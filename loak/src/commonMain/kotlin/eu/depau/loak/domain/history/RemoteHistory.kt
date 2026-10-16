package eu.depau.loak.domain.history

import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.util.Logger
import kotlinx.coroutines.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration

/** One listen a remote source reported, as that source described it. */
data class RemoteListen(
	val title: String,
	val artist: String,
	/** Epoch seconds. */
	val at: Long,
	/** MusicBrainz recording id, when the source carries one. */
	val mbid: String? = null,
	/** ISRC, when the source carries one. */
	val isrc: String? = null
)

/**
 * Somewhere listens can be read from: ListenBrainz, Koito, Last.fm.
 *
 * Implementations say whether the user has filled them in, and answer with listens — never
 * with an exception. A source that is misconfigured, offline or rate-limited is the same as
 * one that reported nothing: the app is expected to work with none of them.
 */
interface RemoteHistorySource {
	/** The value stored in the preference and matched in settings. */
	val id: String

	/** True once the user has filled in whatever this source needs. */
	val isConfigured: Boolean

	/**
	 * The listens since [since] (epoch seconds), newest first, at most [limit]. Anything the
	 * source cannot answer is an empty list.
	 */
	suspend fun recentListens(since: Long, limit: Int): List<RemoteListen>
}

/**
 * A remote source's listens, matched into library songs and decayed into heat: the same
 * [ListeningHistory] the local play log is, so the ranking cannot tell them apart.
 *
 * Matching goes through [LibraryMatcher], the same indexes and normalisation Discover
 * resolves a search result with. A listen that matches nothing is dropped rather than guessed
 * at — a wrongly matched listen would put heat on the wrong song, which is worse than none.
 */
class RemoteHistory(
	private val source: RemoteHistorySource,
	private val matcher: LibraryMatcher
) : ListeningHistory {
	override val id get() = source.id

	override suspend fun heat(window: Duration, halfLife: Duration): Map<String, Double> {
		val now = Clock.System.now().epochSeconds
		val listens = try {
			source.recentListens(now - window.inWholeSeconds, MAX_LISTENS)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.w(TAG, "the ${source.id} listening history could not be read", e)
			return emptyMap()
		}
		if (listens.isEmpty()) return emptyMap()
		val bySong = listens
			.mapNotNull { listen ->
				matcher.songIdOf(listen.title, listen.artist, listen.mbid, listen.isrc)
					?.let { it to listen.at }
			}
			.groupBy({ it.first }, { it.second })
		if (bySong.isEmpty()) Logger.w(TAG, "nothing from ${source.id} matched the library")
		return decayedHeat(bySong, now, window, halfLife)
	}

	companion object {
		private const val TAG = "RemoteHistory"

		/** One page of a source's history: what the heat window needs, and not a scroll through it. */
		const val MAX_LISTENS = 1000
	}
}

/**
 * Every source's heat added up: this device's plays, plus whichever remote source is on.
 * Sources are resolved on every call, so switching one on in settings takes effect at once.
 *
 * A source that fails contributes nothing instead of breaking the sum, so an unreachable
 * integration degrades the list to what the other sources know.
 */
class CombinedHistory(private val sources: () -> List<ListeningHistory>) : ListeningHistory {
	override val id get() = sources().joinToString("+") { it.id }

	override suspend fun heat(window: Duration, halfLife: Duration): Map<String, Double> {
		val total = HashMap<String, Double>()
		for (source in sources()) {
			val heat = try {
				source.heat(window, halfLife)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.w(TAG, "the ${source.id} listening history is unavailable", e)
				continue
			}
			heat.forEach { (songId, value) -> total[songId] = (total[songId] ?: 0.0) + value }
		}
		return total
	}

	private companion object {
		const val TAG = "CombinedHistory"
	}
}
