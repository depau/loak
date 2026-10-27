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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

data class ServerQueue(
	val songs: List<DomainSong>,
	val currentIndex: Int,
	val positionMs: Long,
	val changed: Instant?,
	/** The client name of the device that saved it, e.g. "Lo'ak (Pixel 9)". */
	val changedBy: String
)

/**
 * Saves the queue to the server as playback goes and lets the user move it between devices:
 * the server keeps one queue per user, this device's own comes up first, and the server's
 * (saved by another device) is adopted when this one has been idle or offered otherwise.
 *
 * ponytail: raw calls, because subsonic-kotlin 1.0.0-SNAPSHOT can't save a whole queue
 * (savePlayQueue takes one id) and drops getPlayQueueByIndex's result. Use the library once it
 * handles these.
 */
class QueueSyncManager(
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	private val songDao: SongDao,
	/**
	 * Only reached when "On startup, play" is a playlist. Nullable (as with the SessionManager
	 * engine seam) so flow tests can construct the manager without a Room-backed repository.
	 */
	private val collectionRepository: CollectionRepository? = null
) {
	private val json = Json { ignoreUnknownKeys = true }

	// ponytail: read once per run; a server switch in Settings keeps the old server's answer
	private var extensions: Set<String>? = null

	/** The queue picked up from another device, until this device saves its own. */
	val pickedUpFrom: StateFlow<ServerQueue?>
		field = MutableStateFlow(null)

	/**
	 * When this device last told the server how playback was going (a push), as epoch ms.
	 * Never forgetting it is what keeps a device that has been playing all along from being
	 * treated as idle and having a foreign queue pushed over its head.
	 */
	@Volatile
	var lastActivityMs: Long = preferenceManager.lastQueueSyncMs
		set(value) {
			field = value
			preferenceManager.lastQueueSyncMs = value
		}

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
		lastActivityMs = Clock.System.now().toEpochMilliseconds()
		pickedUpFrom.value = null
	}

	/**
	 * How the server's queue relates to this device's playback right now, [now] in epoch ms.
	 * Pure so it is testable. A device is never adoptable while it is playing, and once it has
	 * ever told the server anything it is only adoptable after the [IDLE_WINDOW_MS] window with
	 * nothing new; otherwise the server's queue is offered instead. Everything else: nothing.
	 */
	fun decision(remote: ServerQueue?, local: PlayerUiState?, now: Long): Decision {
		if (remote == null || local?.queue.isNullOrEmpty()) return Decision.Nothing
		if (remote.songs.map { it.id } == local.queue.map { it.id }) return Decision.Nothing
		val playing = !local.isPaused && local.currentIndex >= 0
		val idle = now - lastActivityMs >= IDLE_WINDOW_MS && !playing
		// never told the server anything (fresh install, reinstall, sync off since): nothing of
		// this device's to protect, resume the foreign queue silently — unless it is playing
		val fresh = lastActivityMs == 0L && !playing
		return when {
			idle || fresh -> Decision.Adopt
			else -> Decision.Offer
		}
	}

	/** The queue to start with, following the "On startup, play" setting; null for none. */
	suspend fun startupState(local: PlayerUiState?): PlayerUiState? =
		when (preferenceManager.startupQueue) {
			StartupQueue.Nothing -> null
			StartupQueue.Local -> local
			StartupQueue.Playlist -> playlistState(local) ?: local
			// the server queue is no longer fetched at startup: this device's own queue comes up
			// first and the server's is offered (or adopted when idle) by the foreground check
			StartupQueue.Server -> local
		}

	private suspend fun playlistState(base: PlayerUiState?): PlayerUiState? {
		val id = preferenceManager.startupPlaylistId.ifBlank { return null }
		// fetched again because automations may rewrite the playlist; the cached copy if offline
		val playlist = runCatching {
			collectionRepository?.getCollectionFlow(fullRefresh = true, collectionId = id)?.last()?.data
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

		/**
		 * A device that pushed nothing for this long (and isn't playing) is treated as idle:
		 * the server's foreign queue may take over silently instead of being offered.
		 */
		const val IDLE_WINDOW_MS = 60L * 60L * 1000L
		private val CLIENT_NAME = Regex("""^Lo'ak \((.+)\)$""")

		/** "Lo'ak (Pixel 9)" → "Pixel 9"; other clients' names as they are. */
		fun sourceName(changedBy: String): String =
			CLIENT_NAME.matchEntire(changedBy)?.groupValues?.get(1) ?: changedBy
	}

	/** What to do with a foreign server queue when this device comes up from behind. */
	enum class Decision {
		/** No foreign queue, no local queue, or the queues already match. */
		Nothing,
		/** Take it over silently (this device hasn't touched playback in [IDLE_WINDOW_MS]). */
		Adopt,
		/** Ask: this device is fresh or was recently playing; never replace without a say. */
		Offer
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
