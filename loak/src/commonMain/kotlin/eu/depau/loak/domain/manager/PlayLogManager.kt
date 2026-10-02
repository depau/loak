package eu.depau.loak.domain.manager

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
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
 * What this device played, for Home: recent song plays (the server only keeps a count per song)
 * and how often each playlist or album was started (the server keeps no count for playlists).
 *
 * ponytail: JSON in the settings, rewritten on every play; the song list is pruned to
 * [KEEP] so it stays a few hundred entries. Move it to a Room table if it ever needs more.
 */
class PlayLogManager(private val settings: Settings) {
	@Serializable
	private data class Log(
		/** Song id to the epoch seconds of each play, oldest first. */
		val songs: Map<String, List<Long>> = emptyMap(),
		/** "playlist:<id>" or "album:<id>" to how many times it was started. */
		val collections: Map<String, Int> = emptyMap()
	)

	private val json = Json { ignoreUnknownKeys = true }

	private var log: Log = settings.getStringOrNull(KEY)
		?.let { runCatching { json.decodeFromString<Log>(it) }.getOrNull() }
		?: Log()

	/** Records a song as played, once it counts as listened to (the scrobble threshold). */
	fun recordSong(songId: String) = update {
		val cutoff = (Clock.System.now() - KEEP).epochSeconds
		val now = Clock.System.now().epochSeconds
		val songs = (it.songs + (songId to (it.songs[songId].orEmpty() + now)))
			.mapValues { (_, plays) -> plays.filter { at -> at >= cutoff } }
			.filterValues { plays -> plays.isNotEmpty() }
		it.copy(songs = songs)
	}

	/** Records a playlist or album as started. */
	fun recordCollection(collection: DomainSongCollection) {
		val key = collection.key() ?: return
		update { it.copy(collections = it.collections + (key to (it.collections[key] ?: 0) + 1)) }
	}

	/** How many times each song was played in the last [window] (at most [KEEP]). */
	fun songPlays(window: Duration): Map<String, Int> {
		val cutoff = (Clock.System.now() - window).epochSeconds
		return log.songs.mapValues { (_, plays) -> plays.count { it >= cutoff } }
			.filterValues { it > 0 }
	}

	/** The song played last, with when. */
	fun lastSong(): Pair<String, Instant>? = log.songs
		.mapValues { (_, plays) -> plays.last() }
		.maxByOrNull { it.value }
		?.let { it.key to Instant.fromEpochSeconds(it.value) }

	/** How many times each playlist was started, by playlist id. */
	fun playlistStarts(): Map<String, Int> = starts("playlist:")

	/** How many times each album was started, by album id. */
	fun albumStarts(): Map<String, Int> = starts("album:")

	private fun starts(prefix: String) = log.collections
		.filterKeys { it.startsWith(prefix) }
		.mapKeys { it.key.removePrefix(prefix) }

	private fun DomainSongCollection.key() = when (this) {
		is DomainPlaylist -> "playlist:$id"
		is DomainAlbum -> "album:$id"
		else -> null
	}

	private fun update(change: (Log) -> Log) {
		log = change(log)
		try {
			settings[KEY] = json.encodeToString(log)
		} catch (e: Exception) {
			Logger.w(TAG, "could not save the play log", e)
		}
	}

	companion object {
		private const val TAG = "PlayLogManager"
		private const val KEY = "playLog"
		val KEEP = 5.days
	}
}
