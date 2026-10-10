package eu.depau.loak.domain.manager

import eu.depau.loak.di.PlatformType
import eu.depau.loak.di.recordPii
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.domain.discover.normalizeArtist
import eu.depau.loak.domain.discover.normalizeTitle
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
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlin.concurrent.Volatile
import kotlin.time.Clock
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

/**
 * The "None" metadata profile: the one whose every `primaryAlbumTypes` entry has
 * `allowed:false` (users can rename or delete Lidarr's built-in one, so it is found by
 * shape, not by name). Null when no profile matches. Pure, so it is unit-testable.
 */
fun noneMetadataProfileOf(profiles: List<JsonObject>): LidarrMetadataProfile? {
	val profile = profiles.firstOrNull { obj ->
		val types = obj["primaryAlbumTypes"]?.jsonArray ?: return@firstOrNull false
		types.isNotEmpty() && types.all { type ->
			(type.jsonObject["allowed"]?.jsonPrimitive?.booleanOrNull ?: false) == false
		}
	} ?: return null
	return LidarrMetadataProfile(
		id = profile["id"]?.jsonPrimitive?.longOrNull ?: return null,
		name = profile["name"]?.jsonPrimitive?.contentOrNull ?: ""
	)
}

/** The artist part of a `POST /album` body, shared by every recipe. For a new artist
 * [id] is null and the rest come from the sheet; for an existing artist the fields are
 * the fresh artist's own (its id, profile, root folder and name). [monitor] is the
 * artist `addOptions.monitor` value ("all" monitors the discography, "none" stops at
 * the added album; the recipes' tails re-monitor the added album explicitly). */
data class AlbumArtistFields(
	val id: Long?,
	val name: String,
	val qualityProfileId: Long,
	val rootFolderPath: String,
	val metadataProfileId: Long,
	val musicBrainzId: String,
	val monitored: Boolean,
	val monitorNewItems: String,
	val monitor: String
)

/** Builds a `POST /album` body (album plus nested artist) per the recipes in §2. */
fun albumAddBody(
	artist: AlbumArtistFields,
	albumTitle: String,
	albumMbId: String
): JsonObject {
	val artistJson = buildJsonObject {
		if (artist.id != null) put("id", JsonPrimitive(artist.id))
		put("artistName", JsonPrimitive(artist.name))
		put("foreignArtistId", JsonPrimitive(artist.musicBrainzId))
		put("qualityProfileId", JsonPrimitive(artist.qualityProfileId))
		put("rootFolderPath", JsonPrimitive(artist.rootFolderPath))
		put("metadataProfileId", JsonPrimitive(artist.metadataProfileId))
		put("monitored", JsonPrimitive(artist.monitored))
		put("monitorNewItems", JsonPrimitive(artist.monitorNewItems))
		put("addOptions", buildJsonObject {
			put("monitor", JsonPrimitive(artist.monitor))
			put("searchForMissingAlbums", JsonPrimitive(false))
		})
		put("tags", buildJsonArray { })
	}
	return buildJsonObject {
		put("title", JsonPrimitive(albumTitle))
		put("foreignAlbumId", JsonPrimitive(albumMbId))
		put("monitored", JsonPrimitive(true))
		// searchForNewAlbum never triggers a search; AlbumSearch is always sent ourselves
		put("addOptions", buildJsonObject {
			put("searchForNewAlbum", JsonPrimitive(false))
		})
		put("artist", artistJson)
	}
}

class LidarrException(message: String, val status: HttpStatusCode? = null) : Exception(message) {
	/** Lidarr's 400 for adding what it already has: the add's goal is met. */
	val alreadyAdded get() = status == HttpStatusCode.BadRequest &&
		message?.contains("already been added", ignoreCase = true) == true
}

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
	val monitored: Boolean,
	/** Lidarr has every track's file: the library gets it on its next scan. */
	val downloaded: Boolean = false
)

/** A Lidarr root folder, with the quality/metadata profiles it defaults to. */
data class LidarrRootFolder(
	val id: Long,
	val name: String,
	val path: String,
	val freeSpace: Long,
	val defaultQualityProfileId: Long,
	val defaultMetadataProfileId: Long,
	/** What new releases of an artist it adds get: "all", "new" or "none". */
	val defaultNewItemMonitorOption: String = "all"
)

/** A Lidarr quality profile (shared by every root folder). */
data class LidarrQualityProfile(
	val id: Long,
	val name: String
)

/** A Lidarr metadata profile (what album types a monitored album can be). */
data class LidarrMetadataProfile(
	val id: Long,
	val name: String
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
 * The Lidarr album a Discover [album] is: by MusicBrainz release group when known, else by
 * normalised title and artist name. Null when Lidarr doesn't have it.
 */
fun LidarrCatalog.albumFor(album: DiscoverAlbum): LidarrAlbumSnapshot? {
	album.mbid?.let { mbid -> albums.firstOrNull { it.musicBrainzId == mbid }?.let { return it } }
	val title = normalizeTitle(album.title)
	val artistIds = artists.filter { normalizeArtist(it.name) == normalizeArtist(album.artist) }.map { it.id }
	return albums.firstOrNull { it.artistId in artistIds && normalizeTitle(it.title) == title }
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
		val artists = try {
			parseArtists(getJson("api/v1/artist"))
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.w(TAG, "Lidarr artists fetch failed", e)
			emptyList()
		}
		val albums = try {
			parseAlbums(getJson("api/v1/album"))
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.w(TAG, "Lidarr albums fetch failed", e)
			emptyList()
		}
		val queue = try {
			parseQueue(getJson("api/v1/queue") {
				parameter("includeAlbum", true)
				parameter("pageSize", 200)
			})
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.w(TAG, "Lidarr queue fetch failed", e)
			emptyList()
		}
		catalog.value = LidarrCatalog(artists, albums, queue)
	}

	/** Refreshes only the queue (the piece that changes while downloading). */
	suspend fun refreshQueue() {
		if (!isConfigured) return
		val queue = try {
			parseQueue(getJson("api/v1/queue") {
				parameter("includeAlbum", true)
				parameter("pageSize", 200)
			})
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.w(TAG, "Lidarr queue fetch failed", e)
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
				monitored = obj["monitored"]?.jsonPrimitive?.booleanOrNull ?: false,
				downloaded = (obj["statistics"]?.jsonObject?.get("percentOfTracks")?.jsonPrimitive?.doubleOrNull ?: 0.0) >= 100.0
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

	// ---------------- M6: adding to Lidarr ----------------

	/** Lidarr's root folders; empty when not configured. */
	suspend fun getRootFolders(): List<LidarrRootFolder> {
		if (!isConfigured) return emptyList()
		val json = runCatching { getJson("api/v1/rootfolder") }.getOrNull() ?: return emptyList()
		return foldAsJsonArray(json) { obj ->
			LidarrRootFolder(
				id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@foldAsJsonArray null,
				name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
				path = obj["path"]?.jsonPrimitive?.contentOrNull ?: return@foldAsJsonArray null,
				freeSpace = obj["freeSpace"]?.jsonPrimitive?.longOrNull ?: 0L,
				defaultQualityProfileId = obj["defaultQualityProfileId"]?.jsonPrimitive?.longOrNull ?: 0L,
				defaultMetadataProfileId = obj["defaultMetadataProfileId"]?.jsonPrimitive?.longOrNull ?: 0L,
				defaultNewItemMonitorOption = obj["defaultNewItemMonitorOption"]?.jsonPrimitive?.contentOrNull ?: "all"
			)
		}
	}

	/** Lidarr's quality profiles; empty when not configured. */
	suspend fun getQualityProfiles(): List<LidarrQualityProfile> {
		if (!isConfigured) return emptyList()
		val json = runCatching { getJson("api/v1/qualityprofile") }.getOrNull() ?: return emptyList()
		return foldAsJsonArray(json) { obj ->
			LidarrQualityProfile(
				id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@foldAsJsonArray null,
				name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
			)
		}
	}

	/** Lidarr's metadata profiles; empty when not configured. */
	suspend fun getMetadataProfiles(): List<LidarrMetadataProfile> {
		if (!isConfigured) return emptyList()
		val json = runCatching { getJson("api/v1/metadataprofile") }.getOrNull() ?: return emptyList()
		return foldAsJsonArray(json) { obj ->
			LidarrMetadataProfile(
				id = obj["id"]?.jsonPrimitive?.longOrNull ?: return@foldAsJsonArray null,
				name = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""
			)
		}
	}

	/**
	 * The "None" metadata profile Lidarr ships with; creates one when it's been renamed or
	 * deleted (every primary/secondary/release-status disallowed except Official).
	 */
	suspend fun findOrCreateNoneProfile(): LidarrMetadataProfile {
		val raw = runCatching { getJson("api/v1/metadataprofile") }
			.getOrNull()?.jsonArray?.map { it.jsonObject } ?: emptyList()
		noneMetadataProfileOf(raw)?.let { return it }
		val created = postJson("api/v1/metadataprofile", buildJsonObject {
			put("name", JsonPrimitive("None (Lo'ak)"))
			put("primaryAlbumTypes", buildJsonArray {
				listOf("Album", "Single", "EP", "Broadcast", "Other").forEach {
					add(buildJsonObject { put("allowed", JsonPrimitive(false)) })
				}
			})
			put("secondaryAlbumTypes", buildJsonArray { })
			put("releaseStatuses", buildJsonArray {
				add(buildJsonObject { put("releaseStatus", JsonPrimitive("Official")); put("allowed", JsonPrimitive(true)) })
			})
		}).jsonObject
		val id = created["id"]?.jsonPrimitive?.longOrNull ?: throw LidarrException("Created metadata profile without an id")
		return LidarrMetadataProfile(id = id, name = "None (Lo'ak)")
	}

	/**
	 * The Lidarr artist for an [artistMbid]; null when the metadata server doesn't know it.
	 * Throws [LidarrException] when the lookup fails (a 503: the metadata server is down).
	 */
	suspend fun lookupArtistByMbId(artistMbid: String): JsonObject? {
		if (!isConfigured) return null
		return getJson("api/v1/artist/lookup") { parameter("term", "lidarr:$artistMbid") }
			.jsonArray.firstOrNull()?.jsonObject
	}

	/**
	 * The Lidarr album for a [releaseGroupMbid]; null when the metadata server doesn't know it.
	 * Throws [LidarrException] when the lookup fails (a 503: the metadata server is down).
	 */
	suspend fun lookupAlbumByMbId(releaseGroupMbid: String): JsonObject? {
		if (!isConfigured) return null
		return getJson("api/v1/album/lookup") { parameter("term", "lidarr:$releaseGroupMbid") }
			.jsonArray.firstOrNull()?.jsonObject
	}

	/**
	 * Adds an album (a MusicBrainz release group) to Lidarr, running one of the three §2
	 * recipes depending on whether the artist already exists in Lidarr (the album lookup's
	 * embedded artist has a real lidarr id) and [alsoGetFutureReleases]; returns the artist id.
	 */
	suspend fun addAlbum(
		releaseGroupMbid: String,
		albumTitle: String,
		alsoGetFutureReleases: Boolean,
		rootFolderPath: String,
		qualityProfileId: Long,
		metadataProfileId: Long
	): Long {
		val album = lookupAlbumByMbId(releaseGroupMbid) ?: throw LidarrException("Not in Lidarr yet")
		val title = album["title"]?.jsonPrimitive?.contentOrNull ?: albumTitle
		val embedded = album["artist"]?.jsonObject
		val embeddedId = embedded?.get("id")?.jsonPrimitive?.longOrNull ?: 0L
		val existing = embeddedId > 0
		// The album lookup only includes a partial artist on some Lidarr releases. The POST
		// model still validates rootFolderPath, so reuse the full existing artist instead.
		val artistJson = if (existing) getJson("api/v1/artist/$embeddedId").jsonObject else embedded
		val artistMbid = artistJson?.get("foreignArtistId")?.jsonPrimitive?.contentOrNull
			?: embedded?.get("foreignArtistId")?.jsonPrimitive?.contentOrNull
			?: throw LidarrException("Metadata for $title has no artist")

		val artist = if (existing) {
			// existing-artist recipe: reuse the artist's own settings, monitor just the added album
			AlbumArtistFields(
				id = embeddedId,
				name = artistJson?.get("artistName")?.jsonPrimitive?.contentOrNull ?: "",
				qualityProfileId = artistJson?.get("qualityProfileId")?.jsonPrimitive?.longOrNull
					?: qualityProfileId,
				rootFolderPath = artistJson?.get("rootFolderPath")?.jsonPrimitive?.contentOrNull
					?: rootFolderPath,
				metadataProfileId = artistJson?.get("metadataProfileId")?.jsonPrimitive?.longOrNull
					?: metadataProfileId,
				musicBrainzId = artistMbid,
				monitored = artistJson?.get("monitored")?.jsonPrimitive?.booleanOrNull ?: true,
				monitorNewItems = artistJson?.get("monitorNewItems")?.jsonPrimitive?.contentOrNull ?: "none",
				monitor = "none"
			)
		} else {
			// new-artist recipe: switch off -> None profile + monitor the discography (harmless,
			// the None profile admits no album); switch on -> the root folder's default profile
			// and just the added album
			AlbumArtistFields(
				id = null,
				name = embedded?.get("artistName")?.jsonPrimitive?.contentOrNull ?: "",
				qualityProfileId = qualityProfileId,
				rootFolderPath = rootFolderPath,
				metadataProfileId = metadataProfileId,
				musicBrainzId = artistMbid,
				monitored = true,
				monitorNewItems = if (alsoGetFutureReleases) "new" else "none",
				monitor = if (alsoGetFutureReleases) "none" else "all"
			)
		}

		val body = albumAddBody(artist, title, releaseGroupMbid)
		val created = try {
			postJson("api/v1/album", body).jsonObject
		} catch (e: LidarrException) {
			if (!e.alreadyAdded) throw e
			refreshCatalog()
			return embeddedId
		}
		val createdArtistId = created["artist"]?.jsonObject?.get("id")?.jsonPrimitive?.longOrNull
			?: created["artistId"]?.jsonPrimitive?.longOrNull
			?: artist.id ?: throw LidarrException("Added album without an artist id")

		// Lidarr refreshes the new artist asynchronously right after the add; wait for it
		waitForArtistRefresh(createdArtistId)

		// Recipe tails differ: switch-on and existing-artist re-monitor the added album (their
		// add unmonitors it); the artist gets re-monitored when switch-on, or when an existing
		// artist was unmonitored. Off + new artist (None profile) needs neither.
		if (alsoGetFutureReleases || existing) {
			created["id"]?.jsonPrimitive?.longOrNull?.let { albumId ->
				putJson("api/v1/album/monitor", buildJsonObject {
					put("albumIds", buildJsonArray { add(JsonPrimitive(albumId)) })
					put("monitored", JsonPrimitive(true))
				})
			}
		}
		if (alsoGetFutureReleases || (existing && !artist.monitored)) {
			// PUT /artist/{id} takes the whole artist
			val full = getJson("api/v1/artist/$createdArtistId").jsonObject
			putJson("api/v1/artist/$createdArtistId", JsonObject(full + ("monitored" to JsonPrimitive(true))))
		}

		triggerAlbumSearch(listOfNotNull(created["id"]?.jsonPrimitive?.longOrNull))
		refreshCatalog()
		return createdArtistId
	}

	/** Adds an artist (by MusicBrainz id) to Lidarr; returns the new artist id. */
	suspend fun addArtist(
		artistMbid: String,
		artistName: String,
		monitor: String,
		searchForMissing: Boolean,
		rootFolderPath: String,
		qualityProfileId: Long,
		metadataProfileId: Long,
		monitorNewItems: String
	): Long {
		val found = lookupArtistByMbId(artistMbid) ?: throw LidarrException("Not in Lidarr yet")
		val body = buildJsonObject {
			put("artistName", JsonPrimitive(found["artistName"]?.jsonPrimitive?.contentOrNull ?: artistName))
			put("foreignArtistId", JsonPrimitive(artistMbid))
			put("qualityProfileId", JsonPrimitive(qualityProfileId))
			put("metadataProfileId", JsonPrimitive(metadataProfileId))
			put("rootFolderPath", JsonPrimitive(rootFolderPath))
			put("monitored", JsonPrimitive(true))
			put("monitorNewItems", JsonPrimitive(monitorNewItems))
			put("addOptions", buildJsonObject {
				put("monitor", JsonPrimitive(monitor))
				put("searchForMissingAlbums", JsonPrimitive(searchForMissing))
			})
			put("tags", buildJsonArray { })
		}
		val created = try {
			postJson("api/v1/artist", body).jsonObject
		} catch (e: LidarrException) {
			if (!e.alreadyAdded) throw e
			refreshCatalog()
			return catalog.value.artists.firstOrNull { it.musicBrainzId == artistMbid }?.id ?: 0L
		}
		val artistId = created["id"]?.jsonPrimitive?.longOrNull
			?: throw LidarrException("Added artist without an id")
		refreshCatalog()
		return artistId
	}

	/** POSTs JSON with the Lidarr headers; throws [LidarrException] on a non-OK answer. */
	private suspend fun postJson(path: String, body: JsonObject): JsonElement {
		if (!isConfigured) throw LidarrException("Lidarr is not connected")
		return withContext(IoDispatcher) {
			val response = client.post("$baseUrl/${path.trimStart('/')}") {
				lidarrHeaders()
				contentType(ContentType.Application.Json)
				setBody(body.toString())
			}
			parse(response)
		}
	}

	/** PUTs JSON with the Lidarr headers; throws [LidarrException] on a non-OK answer. */
	private suspend fun putJson(path: String, body: JsonObject): JsonElement {
		if (!isConfigured) throw LidarrException("Lidarr is not connected")
		return withContext(IoDispatcher) {
			val response = client.put("$baseUrl/${path.trimStart('/')}") {
				lidarrHeaders()
				contentType(ContentType.Application.Json)
				setBody(body.toString())
			}
			parse(response)
		}
	}

	/**
	 * Polls `GET /command` for the artist's asynchronous `RefreshArtist` command to finish
	 * (up to ~2 min); Lidarr's post-add refresh can overwrite the monitor calls otherwise.
	 */
	private suspend fun waitForArtistRefresh(artistId: Long) {
		val deadline = Clock.System.now().toEpochMilliseconds() + 2 * 60 * 1000
		while (Clock.System.now().toEpochMilliseconds() < deadline) {
			val commands = runCatching { getJson("api/v1/command") }.getOrNull()?.jsonArray ?: emptyList()
			val refresh = commands.firstOrNull { cmd ->
				val obj = cmd.jsonObject
				val name = obj["name"]?.jsonPrimitive?.contentOrNull
				if (name != "RefreshArtist") return@firstOrNull false
				val body = obj["body"]?.jsonObject
				val ids = body?.get("artistId")?.jsonPrimitive?.longOrNull?.let { listOf(it) }
					?: body?.get("artistIds")?.jsonArray?.mapNotNull { it.jsonPrimitive.longOrNull }
				ids?.contains(artistId) == true
			}
			if (refresh != null) {
				val status = refresh.jsonObject["status"]?.jsonPrimitive?.contentOrNull
				if (status == "completed" || status == "failed") return
			}
			delay(2_000)
		}
		Logger.w(TAG, "gave up waiting for Lidarr artist $artistId refresh")
	}

	/** Re-sends Lidarr's search for one of its albums ("Search again"). */
	suspend fun searchAlbum(albumId: Long) = triggerAlbumSearch(listOf(albumId))

	/** The album's page in Lidarr's web UI, by its MusicBrainz release group. */
	fun albumPageUrl(foreignAlbumId: String) = "$baseUrl/album/$foreignAlbumId"

	/** Sends the `AlbumSearch` command for the given album ids. */
	private suspend fun triggerAlbumSearch(albumIds: List<Long>) {
		if (albumIds.isEmpty()) return
		postJson("api/v1/command", buildJsonObject {
			put("name", JsonPrimitive("AlbumSearch"))
			put("albumIds", buildJsonArray {
				albumIds.forEach { add(JsonPrimitive(it)) }
			})
		})
	}

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

	/**
	 * A human-readable reason for a non-OK status, using the API's JSON error when present: an
	 * object's `message`, or the first `errorMessage` of a validation failure's array.
	 */
	private fun statusMessage(status: HttpStatusCode, body: String): String =
		runCatching {
			when (val error = json.parseToJsonElement(body)) {
				is JsonArray -> error.firstOrNull()?.jsonObject?.get("errorMessage")
				else -> error.jsonObject["message"]
			}?.jsonPrimitive?.contentOrNull
		}.getOrNull() ?: status.description

	private companion object {
		const val TAG = "LidarrManager"
	}
}
