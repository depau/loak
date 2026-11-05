@file:OptIn(ExperimentalAtomicApi::class)

package eu.depau.loak.domain.repositories

import com.russhwolf.settings.Settings
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The player's state, kept for the next start. The queue is saved as song ids, resolved from the
 * song table when loaded, with whole songs only for the ones the table may not have (radio
 * streams, Autoplay, mixes, search results): a queue of thousands then takes a few hundred KB,
 * which the web's localStorage holds and the desktop rewrites quickly.
 */
class PlayerStateRepository(
	private val settings: Settings,
	private val songDao: SongDao,
) {
	private val json = Json { ignoreUnknownKeys = true }

	/** Queued songs the song table has: a save only looks up the ones it hasn't seen yet. */
	@Volatile
	private var inLibrary = emptySet<String>()

	/**
	 * Whether a non-empty queue is saved, read without loading it: for a media button that
	 * starts the app, which must not start playback service with nothing to play.
	 */
	val hasSavedQueue get() = settings.getBoolean(KEY_HAS_QUEUE, false)

	/** The saved state, with the queue songs neither the song table nor the save has left out. */
	suspend fun load(): PlayerUiState? = withContext(Dispatchers.Default) {
		try {
			settings.getStringOrNull(KEY_OLD_STATE)?.let { old ->
				// saved whole by an older version: once, in the new form. Removed first, as
				// localStorage may not hold both, and a state that can't be read is gone anyway
				settings.remove(KEY_OLD_STATE)
				val state = json.decodeFromString<PlayerUiState>(old)
				save(state, stamp())
				return@withContext state
			}
			val saved = json.decodeFromString<SavedState>(
				settings.getStringOrNull(KEY_STATE) ?: return@withContext null
			)
			val found = saved.queue.distinct().chunked(LOOKUP_CHUNK)
				.flatMap { songDao.getSongsByIds(it) }
				.associate { it.songId to it.toDomainModel() }
			inLibrary = found.keys
			val extras = saved.extras.associateBy { it.id }
			val kept = saved.queue.withIndex().mapNotNull { (i, id) ->
				(found[id] ?: extras[id])?.let { IndexedValue(i, it) }
			}
			val queue = kept.map { it.value }
			if (kept.size == saved.queue.size) return@withContext saved.state.copy(queue = queue)
			Logger.w(TAG, "dropped ${saved.queue.size - kept.size} saved songs no longer known")
			// the current song, or the next one left
			val index = kept.indexOfFirst { it.index >= saved.state.currentIndex }
				.takeIf { it >= 0 } ?: kept.lastIndex
			saved.state.copy(queue = queue, currentIndex = index, currentSong = queue.getOrNull(index))
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.e(TAG, "failed to load the player state", e)
			null
		}
	}

	/**
	 * Stamps states in the order they are captured: [save] and [flush] run on different threads,
	 * and a slow save of an older state must not land after a flush of a newer one.
	 */
	fun stamp() = stamps.addAndFetch(1)

	private val stamps = AtomicLong(0)

	/** The newest state handed to a write, encoded: a write never ends on an older one. */
	private val newest = AtomicReference(Encoded(0, "", false))

	/** Saves [state], [stamp]ed when captured, off the main thread: looks up the song table. */
	suspend fun save(state: PlayerUiState, stamp: Long) = withContext(Dispatchers.Default) {
		try {
			val unseen = state.queue.mapTo(HashSet()) { it.id } - inLibrary
			if (unseen.isNotEmpty()) {
				inLibrary = inLibrary + unseen.chunked(LOOKUP_CHUNK).flatMap { songDao.getSongIdsIn(it) }
			}
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			// saved anyway: the songs not looked up go whole
			Logger.e(TAG, "failed to look up the queued songs", e)
		}
		flush(state, stamp)
	}

	/**
	 * Saves [state] now, on this thread and without the database: for the app going away, when
	 * nothing can be awaited. Songs added since the last [save] go whole.
	 */
	fun flush(state: PlayerUiState, stamp: Long = stamp()) {
		val known = inLibrary
		try {
			val mine = Encoded(stamp, json.encodeToString(SavedState(
				state = state.copy(queue = emptyList()),
				queue = state.queue.map { it.id },
				extras = state.queue.filter { it.id !in known }.distinctBy { it.id },
			)), state.queue.isNotEmpty())
			while (true) {
				val current = newest.load()
				// older than a state already handed over, whose writer writes that one
				if (current.stamp > stamp) return
				if (newest.compareAndSet(current, mine)) break
			}
			// the newest, again if a newer one came in meanwhile: the last write is the newest
			do {
				val top = newest.load()
				// the flag never claims a queue the saved state doesn't have
				if (!top.hasQueue && hasSavedQueue) settings.putBoolean(KEY_HAS_QUEUE, false)
				settings.putString(KEY_STATE, top.json)
				if (top.hasQueue && !hasSavedQueue) settings.putBoolean(KEY_HAS_QUEUE, true)
			} while (newest.load() !== top)
		} catch (e: Throwable) {
			// a Throwable: on the web, a full localStorage throws a JS error, not an Exception
			Logger.e(TAG, "failed to save the player state", e)
		}
	}

	private class Encoded(val stamp: Long, val json: String, val hasQueue: Boolean)

	@Serializable
	private class SavedState(
		/** Everything but the queue. */
		val state: PlayerUiState,
		/** The queue's song ids. */
		val queue: List<String>,
		/** The queued songs the song table may not have. */
		val extras: List<DomainSong>,
	)

	private companion object {
		const val TAG = "PlayerStateRepository"
		const val KEY_STATE = "player_state_v2"
		const val KEY_OLD_STATE = "player_state"
		const val KEY_HAS_QUEUE = "has_saved_queue"

		/** Ids per query: SQLite builds may cap a statement's variables at 999. */
		const val LOOKUP_CHUNK = 900
	}
}
