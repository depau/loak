package eu.depau.loak.domain.history

import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.util.Logger
import kotlinx.coroutines.CancellationException

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
 * Implementations say whether the user has filled them in, and answer with listens — never with
 * an exception. A source that is misconfigured, offline or rate-limited is the same as one that
 * reported nothing: the app is expected to work with none of them.
 */
interface RemoteHistorySource {
	/** The value stored in the preference and matched in settings. */
	val id: String

	/** True once the user has filled in whatever this source needs. */
	val isConfigured: Boolean

	/**
	 * The listens in `[from, to)` (epoch seconds), newest first, at most [limit]. Never throws:
	 * a source that cannot be read at all — not configured, offline, refused, not an API at that
	 * address — answers null.
	 *
	 * Null and "no listens in this window" are different answers, and both are worth keeping:
	 * the first says the figures on screen are missing a source the user turned on (which the
	 * Recap says out loud), the second that the source had nothing to contribute.
	 */
	suspend fun recentListens(from: Long, to: Long, limit: Int): List<RemoteListen>?
}

/**
 * What a history contributed to a window: the plays by library song id, and the ids of the
 * sources that could not be read at all.
 *
 * Empty [plays] with no [unreadable] is a real answer — nothing was played — while every id in
 * [unreadable] is a source the figures are missing.
 */
data class HistoryRead(
	val plays: Map<String, List<Long>> = emptyMap(),
	val unreadable: List<String> = emptyList()
)

/**
 * A remote source's listens, matched into library songs and handed on as plays: the same
 * [ListeningHistory] the local play log is, so neither Quick picks nor the Recap can tell them
 * apart.
 *
 * Matching goes through [LibraryMatcher], the same indexes and normalisation Discover resolves
 * a search result with. A listen that matches nothing is dropped rather than guessed at — a
 * wrongly matched listen would put plays on the wrong song, which is worse than none.
 */
class RemoteHistory(
	private val source: RemoteHistorySource,
	private val matcher: LibraryMatcher
) : ListeningHistory {
	override val id get() = source.id

	override suspend fun reads(from: Long, to: Long): HistoryRead {
		val listens = try {
			source.recentListens(from, to, MAX_LISTENS)
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.w(TAG, "the ${source.id} listening history could not be read", e)
			null
		} ?: return HistoryRead(unreadable = listOf(source.id))
		if (listens.isEmpty()) return HistoryRead()
		val bySong = listens
			.mapNotNull { listen ->
				// the source may have listened past [to] since the window it was asked about
				if (listen.at !in from until to) return@mapNotNull null
				matcher.songIdOf(listen.title, listen.artist, listen.mbid, listen.isrc)
					?.let { it to listen.at }
			}
			.groupBy({ it.first }, { it.second })
		if (bySong.isEmpty()) Logger.w(TAG, "nothing from ${source.id} matched the library")
		return HistoryRead(bySong)
	}

	companion object {
		private const val TAG = "RemoteHistory"

		/** One page of a source's history: what a window needs, and not a scroll through it. */
		const val MAX_LISTENS = 1000
	}
}

/**
 * Every source's plays added up: this device's, plus whichever remote source is on. Sources are
 * resolved on every call, so switching one on in settings takes effect at once.
 *
 * A source that fails contributes nothing instead of breaking the sum, so an unreachable
 * integration degrades the list to what the other sources know — and is named in
 * [HistoryRead.unreadable] rather than passed off as a source that had nothing to say.
 */
class CombinedHistory(private val sources: () -> List<ListeningHistory>) : ListeningHistory {
	override val id get() = sources().joinToString("+") { it.id }

	override suspend fun reads(from: Long, to: Long): HistoryRead {
		val total = HashMap<String, MutableList<Long>>()
		val unreadable = mutableListOf<String>()
		for (source in sources()) {
			val read = try {
				source.reads(from, to)
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.w(TAG, "the ${source.id} listening history is unavailable", e)
				HistoryRead(unreadable = listOf(source.id))
			}
			read.plays.forEach { (songId, at) -> total.getOrPut(songId) { mutableListOf() } += at }
			unreadable += read.unreadable
		}
		return HistoryRead(total, unreadable.distinct())
	}

	private companion object {
		const val TAG = "CombinedHistory"
	}
}
