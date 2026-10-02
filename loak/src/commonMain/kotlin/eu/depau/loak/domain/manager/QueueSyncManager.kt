package eu.depau.loak.domain.manager

import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.settings.StartupQueue
import eu.depau.loak.domain.repositories.CollectionRepository
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The play queue as the server keeps it: one per user, saved by whichever device played last. */
data class ServerQueue(
	val songs: List<DomainSong>,
	val currentIndex: Int,
	val positionMs: Long,
	val changed: Instant?,
	/** The client name of the device that saved it, e.g. "Lo'ak (Pixel 9)". */
	val changedBy: String
)

/**
 * Saves the queue to the server and picks it up again at startup, so playback can move between
 * devices. The queue is only read at startup or when asked: a queue saved by another device
 * never replaces the one playing here.
 *
 * ponytail: raw calls, because subsonic-kotlin 1.0.0-SNAPSHOT can't save a whole queue
 * (savePlayQueue takes one id) and drops getPlayQueueByIndex's result. Use the library once it
 * handles these.
 */
class QueueSyncManager(
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	private val songDao: SongDao,
	private val collectionRepository: CollectionRepository
) {
	private val json = Json { ignoreUnknownKeys = true }

	// ponytail: read once per run; a server switch in Settings keeps the old server's answer
	private var extensions: Set<String>? = null

	/** The queue picked up from another device, until this device saves its own. */
	val pickedUpFrom: StateFlow<ServerQueue?>
		field = MutableStateFlow(null)

	/** The OpenSubsonic extensions the server has; none when it can't say. */
	suspend fun extensions(): Set<String> = extensions ?: runCatching {
		sessionManager.api.getOpenSubsonicExtensions().mapTo(HashSet()) { it.name }
	}.getOrElse {
		Logger.w(TAG, "could not read the server's extensions", it)
		emptySet()
	}.also { extensions = it }

	/** The server's queue, or null when it has none. Throws when the server can't be reached. */
	suspend fun fetch(): ServerQueue? {
		// the index-based variant tells duplicates of the current song apart
		val byIndex = INDEX_BASED in extensions()
		val body = sessionManager.api.httpClient
			.get(if (byIndex) "getPlayQueueByIndex.view" else "getPlayQueue.view")
			.bodyAsText()
		val queue = json.parseToJsonElement(body).jsonObject["subsonic-response"]
			?.jsonObject?.get(if (byIndex) "playQueueByIndex" else "playQueue")
			?.jsonObject ?: return null

		val ids = queue["entry"]?.jsonArray?.map { it.jsonObject["id"]!!.jsonPrimitive.content }
			.orEmpty()
		val current = if (byIndex) queue["currentIndex"]?.jsonPrimitive?.int ?: 0
		else ids.indexOf(queue["current"]?.jsonPrimitive?.content).coerceAtLeast(0)

		// ponytail: songs the library sync hasn't seen yet are dropped
		val known = songDao.getSongsByIds(ids).associateBy { it.songId }
		val kept = ids.withIndex().filter { it.value in known }
		if (kept.isEmpty()) return null

		return ServerQueue(
			songs = kept.map { known.getValue(it.value).toDomainModel() },
			currentIndex = kept.indexOfFirst { it.index >= current }.takeIf { it >= 0 } ?: 0,
			positionMs = queue["position"]?.jsonPrimitive?.long ?: 0,
			changed = queue["changed"]?.jsonPrimitive?.content?.let { runCatching { Instant.parse(it) }.getOrNull() },
			changedBy = queue["changedBy"]?.jsonPrimitive?.content.orEmpty()
		)
	}

	/** Saves [state]'s queue, current song and position to the server. */
	suspend fun save(state: PlayerUiState) {
		val extensions = extensions()
		val byIndex = INDEX_BASED in extensions
		val current = state.queue.getOrNull(state.currentIndex)
		val position = ((current?.duration?.inWholeMilliseconds ?: 0) * state.progress).toLong()
		val params = parameters {
			state.queue.forEach { append("id", it.id) }
			if (current != null) {
				if (byIndex) append("currentIndex", state.currentIndex.toString())
				else append("current", current.id)
			}
			append("position", position.toString())
		}
		val endpoint = if (byIndex) "savePlayQueueByIndex.view" else "savePlayQueue.view"
		val client = sessionManager.api.httpClient
		// a long queue doesn't fit in a URL
		if (FORM_POST in extensions) client.submitForm(endpoint, params)
		else client.get(endpoint) { params.forEach { key, values -> values.forEach { parameter(key, it) } } }
		pickedUpFrom.value = null
	}

	/** The queue to start with, following the "On startup, play" setting; null for none. */
	suspend fun startupState(local: PlayerUiState?): PlayerUiState? =
		when (preferenceManager.startupQueue) {
			StartupQueue.Nothing -> null
			StartupQueue.Local -> local
			StartupQueue.Playlist -> playlistState(local) ?: local
			StartupQueue.Server -> {
				if (!preferenceManager.queueSyncEnabled) local
				else serverState(local) ?: local
			}
		}

	private suspend fun serverState(base: PlayerUiState?): PlayerUiState? {
		val remote = try {
			withTimeoutOrNull(STARTUP_TIMEOUT) { fetch() }
		} catch (e: Exception) {
			Logger.w(TAG, "could not fetch the server queue, using this device's", e)
			null
		} ?: return null
		markPickedUp(remote)
		return remote.toState(base)
	}

	private suspend fun playlistState(base: PlayerUiState?): PlayerUiState? {
		val id = preferenceManager.startupPlaylistId.ifBlank { return null }
		// fetched again because automations may rewrite the playlist; the cached copy if offline
		val playlist = runCatching {
			collectionRepository.getCollectionFlow(fullRefresh = true, collectionId = id).last().data
		}.getOrNull() ?: return null
		if (playlist.songs.isEmpty()) return null
		return (base ?: PlayerUiState()).copy(
			queue = playlist.songs,
			currentSong = playlist.songs.first(),
			currentCollection = playlist,
			currentIndex = 0,
			progress = 0f
		)
	}

	/** Shows where [remote] came from, unless this device saved it. */
	fun markPickedUp(remote: ServerQueue) {
		pickedUpFrom.value = remote.takeIf { it.changedBy != sessionManager.clientName }
	}

	companion object {
		private const val TAG = "QueueSyncManager"
		private const val INDEX_BASED = "indexBasedQueue"
		private const val FORM_POST = "formPost"
		private val STARTUP_TIMEOUT = 5.seconds
		private val CLIENT_NAME = Regex("""^Lo'ak \((.+)\)$""")

		/** "Lo'ak (Pixel 9)" → "Pixel 9"; other clients' names as they are. */
		fun sourceName(changedBy: String): String =
			CLIENT_NAME.matchEntire(changedBy)?.groupValues?.get(1) ?: changedBy
	}
}

/** [base] (shuffle, repeat, speed) with this queue, paused at the saved position. */
fun ServerQueue.toState(base: PlayerUiState?): PlayerUiState {
	val song = songs[currentIndex]
	val duration = song.duration.inWholeMilliseconds
	return (base ?: PlayerUiState()).copy(
		queue = songs,
		currentSong = song,
		currentCollection = null,
		currentIndex = currentIndex,
		progress = if (duration > 0) (positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
	)
}
