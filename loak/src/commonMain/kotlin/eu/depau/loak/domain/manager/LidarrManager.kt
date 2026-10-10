package eu.depau.loak.domain.manager

import eu.depau.loak.di.PlatformType
import eu.depau.loak.di.recordPii
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.seconds

/** What the connected Lidarr looks like, read once per connection. */
data class LidarrInfo(
	val version: String,
	val rootFolderCount: Int = 0,
	val queueCount: Int = 0
)

/**
 * Parses Lidarr's API answers into [LidarrInfo] without touching the network: the status answer
 * is required, missing/failed root-folder or queue answers only drop their counts.
 */
fun lidarrInfoOf(status: JsonElement?, rootFolders: JsonElement?, queue: JsonElement?): LidarrInfo {
	val version = status?.jsonObject?.get("version")?.jsonPrimitive?.contentOrNull ?: "unknown"
	val rootFolderCount = (rootFolders as? JsonArray)?.size ?: 0
	val queueCount = queue?.jsonObject?.get("totalRecords")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
	return LidarrInfo(version = version, rootFolderCount = rootFolderCount, queueCount = queueCount)
}

class LidarrException(message: String, val status: HttpStatusCode? = null) : Exception(message)

/** Something Lidarr is currently downloading, as Discover reports it on the queue row. */
data class LidarrQueueItem(
	val id: Long,
	val albumId: Long?,
	val artistId: Long?,
	val title: String,
	val artistName: String?,
	val size: Double,
	val sizeLeft: Double,
	val progress: Float,
	val quality: String?,
	val eta: String?
)

/** An artist Lidarr knows about, enough to recognise Discover items already in it. */
data class LidarrArtistSnapshot(
	val id: Long,
	val musicBrainzId: String?,
	val name: String
)

/** An album Lidarr knows about, enough to recognise Discover items already in it. */
data class LidarrAlbumSnapshot(
	val id: Long,
	val musicBrainzId: String?,
	val title: String,
	val artistId: Long,
	val monitored: Boolean
)

/**
 * A snapshot of Lidarr's artists, albums and queue, read together so statuses stay consistent
 * within one refresh. Pairs with [LibraryMatcher] to answer "where is this item?".
 */
data class LidarrCatalog(
	val artists: List<LidarrArtistSnapshot>,
	val albums: List<LidarrAlbumSnapshot>,
	val queue: List<LidarrQueueItem>
) {
	companion object {
		val EMPTY = LidarrCatalog(emptyList(), emptyList(), emptyList())
	}
}

/**
 * Where a Discover item stands on its way to the user: already in the Subsonic library
 * ([InLibrary], carrying the song it plays as, or the album/artist id), in Lidarr's queue
 * ([Downloading]), or already in Lidarr ([InLidarr]). UI-independent so screens can render a badge.
 */
sealed interface DiscoverBadgeState {
	data class InLibrary(val song: DomainSong? = null, val albumId: String? = null, val artistId: String? = null) : DiscoverBadgeState
	data class Downloading(val progress: Float) : DiscoverBadgeState
	data object InLidarr : DiscoverBadgeState
	data object None : DiscoverBadgeState
}

/**
 * Lidarr's own REST API (/api/v1, the one its web UI uses), for what Subsonic can't do:
 * discovering music to grab. Talks to an extra Lidarr instance with its own API key.
 */
class LidarrManager(
	private val preferenceManager: PreferenceManager,
	private val platformType: PlatformType,
	private val libraryMatcher: LibraryMatcher
) {
	private val json = Json { ignoreUnknownKeys = true }
	private val client = HttpClient()

	val info: StateFlow<LidarrInfo?>
		field = MutableStateFlow<LidarrInfo?>(null)

	val catalog: StateFlow<LidarrCatalog>
		field = MutableStateFlow(LidarrCatalog.EMPTY)

	@Volatile
	private var polling = false
	private val scope = CoroutineScope(SupervisorJob() + IoDispatcher)

	val isConfigured get() = preferenceManager.lidarrUrl.isNotBlank()

	/**
	 * Whether Discover shows anywhere (tab, rail, Library): Lidarr set up, the tab on, not the
	 * web. Reads snapshot state, so composables reading it follow changes.
	 */
	val discoverShown
		get() = platformType != PlatformType.Web && preferenceManager.discoverTabEnabled && isConfigured

	private val baseUrl get() = preferenceManager.lidarrUrl.trimEnd('/')

	/**
	 * Checks [url] with [apiKey] and, if it answers, keeps them. Rolls the old settings back
	 * (and leaves [info] unchanged) when the server is unreachable or not happy with the key.
	 */
	suspend fun connect(url: String, apiKey: String): LidarrInfo {
		val old = listOf(preferenceManager.lidarrUrl, preferenceManager.lidarrApiKey,
			preferenceManager.lidarrCustomHeaders, preferenceManager.lidarrInheritServerHeaders)
		val newUrl = url.trim().trimEnd('/')
		preferenceManager.lidarrUrl = newUrl
		preferenceManager.lidarrApiKey = apiKey.trim()
		// its errors go to Sentry too; censor its server address up front
		recordPii(newUrl)
		return try {
			val found = refresh() ?: throw LidarrException("No answer from Lidarr")
			info.value = found
			found
		} catch (e: Exception) {
			preferenceManager.lidarrUrl = old[0] as String
			preferenceManager.lidarrApiKey = old[1] as String
			preferenceManager.lidarrCustomHeaders = old[2] as String
			preferenceManager.lidarrInheritServerHeaders = old[3] as Boolean
			info.value = null
			throw e
		}
	}

	fun disconnect() {
		preferenceManager.lidarrUrl = ""
		preferenceManager.lidarrApiKey = ""
		preferenceManager.lidarrCustomHeaders = ""
		preferenceManager.lidarrInheritServerHeaders = false
		info.value = null
	}

	/** Reads the server's version and how full its library and queue are. Null when not configured. */
	suspend fun refresh(): LidarrInfo? {
		if (!isConfigured) return null
		// failing anything but the status call is fine: only the counts suffer
		val status = getJson("api/v1/system/status")
		val rootFolders = runCatching { getJson("api/v1/rootfolder") }.getOrNull()
		val queue = runCatching { getJson("api/v1/queue") }.getOrNull()
		val found = lidarrInfoOf(status, rootFolders, queue)
		info.value = found
		return found
	}

	/** Connects in the background at startup, if set up. */
	suspend fun start() {
		runCatching { refresh() }.onFailure { Logger.w(TAG, "Lidarr unreachable", it) }
	}

	/** Re-reads Lidarr's artists, albums and queue into [catalog]; no-op when not configured. */
	suspend fun refreshCatalog() {
		if (!isConfigured) return
		val artists = runCatching { parseArtists(getJson("api/v1/artist")) }.getOrElse {
			Logger.w(TAG, "Lidarr artists fetch failed", it)
			emptyList()
		}
		val albums = runCatching { parseAlbums(getJson("api/v1/album")) }.getOrElse {
			Logger.w(TAG, "Lidarr albums fetch failed", it)
			emptyList()
		}
		val queue = runCatching { parseQueue(getJson("api/v1/queue") { parameter("includeAlbum", true); parameter("pageSize", 200) }) }.getOrElse {
			Logger.w(TAG, "Lidarr queue fetch failed", it)
			emptyList()
		}
		catalog.value = LidarrCatalog(artists, albums, queue)
	}

	/** Refreshes only the queue (the piece that changes while downloading). */
	private suspend fun refreshQueue() {
		if (!isConfigured) return
		val queue = runCatching { parseQueue(getJson("api/v1/queue") { parameter("includeAlbum", true); parameter("pageSize", 200) }) }.getOrElse {
			Logger.w(TAG, "Lidarr queue fetch failed", it)
			return
		}
		catalog.value = catalog.value.copy(queue = queue)
	}

	/** Starts the ~10 s queue poll used while a "Downloading" item is on screen; safe to call twice. */
	fun startQueuePolling() {
		if (polling) return
		polling = true
		scope.launch {
			Logger.i(TAG, "queue polling started")
			refreshQueue()
			while (polling) {
				delay(10.seconds)
				refreshQueue()
			}
		}
	}

	/** Stops the queue poll; the running coroutine sees the flag and exits on its next tick. */
	fun stopQueuePolling() {
		polling = false
	}

	private fun parseArtists(json: JsonElement): List<LidarrArtistSnapshot> =
		foldAsJsonArray(json) { obj ->
			LidarrArtistSnapshot(
				id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@foldAsJsonArray null,
				musicBrainzId = obj["foreignArtistId"]?.jsonPrimitive?.contentOrNull,
				name = obj["artistName"]?.jsonPrimitive?.contentOrNull ?: ""
			)
		}

	private fun parseAlbums(json: JsonElement): List<LidarrAlbumSnapshot> =
		foldAsJsonArray(json) { obj ->
			LidarrAlbumSnapshot(
				id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@foldAsJsonArray null,
				musicBrainzId = obj["foreignAlbumId"]?.jsonPrimitive?.contentOrNull,
				title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "",
				artistId = obj["artistId"]?.jsonPrimitive?.longOrNull ?: 0L,
				monitored = obj["monitored"]?.jsonPrimitive?.booleanOrNull ?: false
			)
		}

	private fun parseQueue(json: JsonElement): List<LidarrQueueItem> =
		foldAsJsonArray(json) { obj ->
			val album = obj["album"]?.jsonObject
			// with includeAlbum the record nests album/artist; fall back to top-level fields
			val title = album?.get("title")?.jsonPrimitive?.contentOrNull
				?: obj["title"]?.jsonPrimitive?.contentOrNull ?: return@foldAsJsonArray null
			val artistName = album?.get("artist")?.jsonObject?.get("artistName")?.jsonPrimitive?.contentOrNull
				?: obj["artist"]?.jsonObject?.get("artistName")?.jsonPrimitive?.contentOrNull
			val size = obj["size"]?.jsonPrimitive?.doubleOrNull ?: 0.0
			val sizeLeft = obj["sizeleft"]?.jsonPrimitive?.doubleOrNull ?: 0.0
			val progress = if (size > 0.0 && sizeLeft >= 0.0 && sizeLeft.isFinite()) {
				(1.0f - (sizeLeft / size).toFloat()).coerceIn(0f, 1f)
			} else 0f
			LidarrQueueItem(
				id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@foldAsJsonArray null,
				albumId = obj["albumId"]?.jsonPrimitive?.longOrNull,
				artistId = obj["artistId"]?.jsonPrimitive?.longOrNull,
				title = title,
				artistName = artistName,
				size = size,
				sizeLeft = sizeLeft,
				progress = progress,
				quality = obj["quality"]?.jsonObject?.get("quality")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull,
				eta = obj["estimatedCompletionTime"]?.jsonPrimitive?.contentOrNull
			)
		}

	private inline fun <T> foldAsJsonArray(
		json: JsonElement,
		build: (JsonObject) -> T?
	): List<T> {
		val arr = json as? JsonArray ?: return emptyList()
		return arr.mapNotNull { build(it.jsonObject) }
	}

	/**
	 * Whether the Discover item already lives in the Subsonic library ([LibraryMatcher]), is
	 * queueing in Lidarr ([Downloading]) or is already in Lidarr ([InLidarr]). Library first,
	 * then queue, then catalog — checks in the order the user cares about.
	 */
	suspend fun statusFor(song: DiscoverSong): DiscoverBadgeState {
		libraryMatcher.songInLibrary(song)?.let { return it.toInLibrary() }
		catalog.value.queue.firstOrNull { item ->
			item.title == song.title && item.artistName == song.artist
		}?.let { return DiscoverBadgeState.Downloading(it.progress) }
		if (catalog.value.albums.any { it.title == song.title } ||
			catalog.value.artists.any { it.name == song.artist }
		) return DiscoverBadgeState.InLidarr
		return DiscoverBadgeState.None
	}

	/**
	 * Whether the Discover album already lives in the Subsonic library ([LibraryMatcher]), is
	 * queueing in Lidarr ([Downloading]) or is already in Lidarr ([InLidarr]).
	 */
	suspend fun statusFor(album: DiscoverAlbum): DiscoverBadgeState {
		libraryMatcher.albumInLibrary(album)?.let { return it.toInLibrary() }
		catalog.value.queue.firstOrNull { item -> item.title == album.title && item.artistName == album.artist }
			?.let { return DiscoverBadgeState.Downloading(it.progress) }
		if (catalog.value.albums.any { it.title == album.title } ||
			catalog.value.artists.any { it.name == album.artist }
		) return DiscoverBadgeState.InLidarr
		return DiscoverBadgeState.None
	}

	/**
	 * Whether the Discover artist already lives in the Subsonic library ([LibraryMatcher]), is
	 * queueing in Lidarr ([Downloading]) or is already in Lidarr ([InLidarr]).
	 */
	suspend fun statusFor(artist: DiscoverArtist): DiscoverBadgeState {
		libraryMatcher.artistInLibrary(artist)?.let { return it.toInLibrary() }
		catalog.value.queue.firstOrNull { item -> item.artistName == artist.name }
			?.let { return DiscoverBadgeState.Downloading(it.progress) }
		if (catalog.value.artists.any { it.name == artist.name }) return DiscoverBadgeState.InLidarr
		return DiscoverBadgeState.None
	}

	private fun DomainSong.toInLibrary() = DiscoverBadgeState.InLibrary(song = this)
	private fun DomainAlbum.toInLibrary() = DiscoverBadgeState.InLibrary(albumId = id)
	private fun DomainArtist.toInLibrary() = DiscoverBadgeState.InLibrary(artistId = id)

	suspend fun getJson(path: String, block: HttpRequestBuilder.() -> Unit = {}): JsonElement =
		parse(request(path, block = block))

	suspend fun request(
		path: String,
		block: HttpRequestBuilder.() -> Unit
	): HttpResponse = withContext(IoDispatcher) {
		val url = "$baseUrl/${path.trimStart('/')}"
		val auth: HttpRequestBuilder.() -> Unit = {
			lidarrHeaders()
			block()
		}
		client.get(url, auth)
	}

	private suspend fun parse(response: HttpResponse): JsonElement {
		val text = response.bodyAsText()
		if (!response.status.isSuccess()) {
			throw LidarrException(statusMessage(response.status, text), response.status)
		}
		return json.parseToJsonElement(text)
	}

	/** The API key plus custom and inherited headers; read per request so edits apply next call. */
	private fun HttpRequestBuilder.lidarrHeaders() {
		val key = preferenceManager.lidarrApiKey
		if (key.isNotBlank()) header("X-Api-Key", key)
		preferenceManager.customHeadersMap(preferenceManager.lidarrCustomHeaders).forEach { (k, v) ->
			header(k, v)
		}
		if (preferenceManager.lidarrInheritServerHeaders) {
			preferenceManager.customHeadersMap().forEach { (k, v) -> header(k, v) }
		}
	}

	/** A human-readable reason for a non-OK status, using the API's JSON error when present. */
	private fun statusMessage(status: HttpStatusCode, body: String): String =
		runCatching {
			json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.contentOrNull
		}.getOrNull() ?: status.description

	private companion object {
		const val TAG = "LidarrManager"
	}
}
