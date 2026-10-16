package eu.depau.loak.domain.manager

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import eu.depau.loak.data.database.dao.PlayLogDao
import eu.depau.loak.data.database.entities.PlayLogEntity
import eu.depau.loak.domain.history.decayedHeat
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.util.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * What this device played, for Home and the recap: one row per counted play (the server only
 * keeps a running count per song, with no way to ask when), and how often each playlist or
 * album was started (the server keeps no count for playlists at all).
 *
 * Plays go to a Room table and are kept for [KEEP] — months, because the heat Quick picks rank
 * on decays over weeks and the recap counts back over them. The collection counters stay in the
 * settings: they are a few dozen numbers, and they are not per-play.
 *
 * The old JSON log's plays are not carried over; at most [KEEP] of them existed, and losing a
 * few days of device-local history beats writing a migration to keep it.
 */
class PlayLogManager(private val settings: Settings, private val playLogDao: PlayLogDao) {
	@Serializable
	private data class Counters(
		/** "playlist:<id>" or "album:<id>" to how many times it was started. */
		val collections: Map<String, Int> = emptyMap()
	)

	private val json = Json { ignoreUnknownKeys = true }

	private var counters: Counters = settings.getStringOrNull(KEY)
		?.let { runCatching { json.decodeFromString<Counters>(it) }.getOrNull() }
		?: Counters()

	/** The day the log was last pruned, so a play doesn't run a delete every time. */
	private var prunedDay = -1L

	/** How long plays are kept, and so the longest window anything can ask about. */
	val keep = KEEP

	/** Records a song as played, once it counts as listened to (the scrobble threshold). */
	suspend fun recordSong(songId: String) {
		val now = Clock.System.now().epochSeconds
		playLogDao.insert(PlayLogEntity(songId = songId, playedAt = now))
		prune(now)
	}

	/** Records a playlist or album as started. */
	fun recordCollection(collection: DomainSongCollection) {
		val key = collection.key() ?: return
		counters = counters.copy(
			collections = counters.collections + (key to (counters.collections[key] ?: 0) + 1)
		)
		try {
			settings[KEY] = json.encodeToString(counters)
		} catch (e: Exception) {
			Logger.w(TAG, "could not save the collection counters", e)
		}
	}

	/**
	 * Every play in `[from, to)`, by song id, oldest first. The window the Recap counts over and
	 * the one Quick picks decay are both this, asked for different spans.
	 */
	suspend fun listens(from: Long, to: Long): Map<String, List<Long>> =
		playLogDao.playsBetween(from, to).groupBy({ it.songId }, { it.playedAt })

	/** How many times each song was played in the last [window]. */
	suspend fun songPlays(window: Duration): Map<String, Int> =
		playLogDao.playCountsSince(cutoff(window)).associate { it.songId to it.count }

	/** The song played last, with when. */
	suspend fun lastSong(): Pair<String, Instant>? =
		playLogDao.lastPlay()?.let { it.songId to Instant.fromEpochSeconds(it.playedAt) }

	/**
	 * When the oldest play still in the log was, or null with none. What an all-time Recap runs
	 * back to, rather than the epoch.
	 */
	suspend fun oldestPlay(): Instant? =
		playLogDao.firstPlay()?.let { Instant.fromEpochSeconds(it.playedAt) }

	/** How many times each playlist was started, by playlist id. */
	fun playlistStarts(): Map<String, Int> = starts("playlist:")

	/** How many times each album was started, by album id. */
	fun albumStarts(): Map<String, Int> = starts("album:")

	private fun starts(prefix: String) = counters.collections
		.filterKeys { it.startsWith(prefix) }
		.mapKeys { it.key.removePrefix(prefix) }

	/** Epoch seconds before which a play is outside a [window] ending now. */
	private fun cutoff(window: Duration) = Clock.System.now().epochSeconds - window.inWholeSeconds

	/** Drops plays past [KEEP], at most once a day. */
	private suspend fun prune(now: Long) {
		val day = now / SECONDS_PER_DAY
		if (day == prunedDay) return
		prunedDay = day
		try {
			playLogDao.prune(now - KEEP.inWholeSeconds)
		} catch (e: Exception) {
			Logger.w(TAG, "could not prune the play log", e)
		}
	}

	private fun DomainSongCollection.key() = when (this) {
		is DomainPlaylist -> "playlist:$id"
		is DomainAlbum -> "album:$id"
		else -> null
	}

	companion object {
		private const val TAG = "PlayLogManager"
		private const val KEY = "playLog"
		private const val SECONDS_PER_DAY = 86_400
		val KEEP = 90.days
	}
}
