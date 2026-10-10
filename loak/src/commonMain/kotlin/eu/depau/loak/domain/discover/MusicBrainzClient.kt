package eu.depau.loak.domain.discover

import eu.depau.loak.util.IoDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.concurrent.Volatile
import kotlin.time.Clock

/**
 * MusicBrainz identity lookup for Discover (Milestone M6): maps Deezer tracks
 * (by ISRC) and albums (by UPC) to MusicBrainz release groups, and artist names
 * to MusicBrainz artists, so items can be handed to Lidarr.
 *
 * Follows MusicBrainz's API policy: 1 request/s per IP enforced by one
 * process-wide [Mutex] + last-request timestamp, a descriptive User-Agent on
 * every call, a single retry after a pause, and answers cached in memory.
 *
 * MusicBrainz's isrc endpoint does not reliably return releases/release-groups,
 * so the ISRC path takes the recording's artist credit and falls back to an
 * exact release-group search (score >= 90), which is the spec's own fallback.
 *
 * The selection logic ([selectDefaultReleaseGroup]) is pure, so it is unit-testable.
 */
class MusicBrainzClient {
	private val json = Json { ignoreUnknownKeys = true }
	private val client = HttpClient()
	private val baseUrl = "https://musicbrainz.org/ws/2"

	private val rateLimitMutex = Mutex()

	@Volatile
	private var lastRequestMs = 0L

	// ponytail: insertion-order eviction (LinkedHashMap's access order is JVM-only); fine for a session cache
	private val cache = LinkedHashMap<String, JsonObject>()

	companion object {
		private const val USER_AGENT = "Lo'ak/1.0.0 ( https://github.com/depau/loak )"
		private const val MIN_INTERVAL_MS = 1_000L
		private const val RETRY_DELAY_MS = 1_500L
	}

	// ---------------- Public API ----------------

	/**
	 * Maps a track ISRC to candidate release groups: reads the artist off the ISRC
	 * recording(s), then does an exact title+artist release-group search per recording.
	 */
	suspend fun resolveReleaseGroupsByIsrc(isrc: String): List<MusicBrainzReleaseGroup> {
		val lookup = getJson("/isrc/$isrc") ?: return emptyList()
		val seen = linkedSetOf<MusicBrainzReleaseGroup>()
		for (recording in lookup["recordings"]?.jsonArray.orEmpty()) {
			val rec = recording.jsonObject
			val title = rec["title"]?.jsonPrimitive?.contentOrNull ?: continue
			val artist = rec["artist-credit"]?.jsonArray?.firstOrNull()?.jsonObject
				?.get("artist")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: continue
			searchReleaseGroup(title, artist)?.let(seen::add)
		}
		return seen.toList()
	}

	/** Maps a Deezer album's UPC to a release group, if any release carries it. */
	suspend fun resolveReleaseGroupByUpc(upc: String): MusicBrainzReleaseGroup? {
		val lookup = getJson("/release", mapOf("query" to "barcode:$upc")) ?: return null
		// search answers embed each release's group
		return lookup["releases"]?.jsonArray.orEmpty().firstNotNullOfOrNull {
			it.jsonObject["release-group"]?.jsonObject?.toReleaseGroup()?.takeIf { group -> group.id.isNotEmpty() }
		}
	}

	/** The earliest release group with an exact title+artist match (score >= 90). */
	suspend fun searchReleaseGroup(title: String, artist: String): MusicBrainzReleaseGroup? {
		val query = "releasegroup:\"$title\" AND artist:\"$artist\""
		val result = getJson("/release-group", mapOf("query" to query)) ?: return null
		return result["release-groups"]?.jsonArray.orEmpty()
			.map { it.jsonObject.toReleaseGroup() }
			.filter { (it.score ?: 0) >= 90 }
			.sortedBy { it.firstReleaseDate }
			.firstOrNull()
	}

	/**
	 * The song's recording(s) and the release groups of their official releases: one recording
	 * search by [isrc], else (or when the ISRC finds nothing) by exact title and artist.
	 */
	suspend fun songReleases(isrc: String?, title: String, artist: String): SongReleases {
		suspend fun search(query: String) =
			getJson("/recording", mapOf("query" to query, "limit" to "100"))?.let(::songReleasesOf)
		val byIsrc = isrc?.takeIf { it.isNotBlank() }?.let { search("isrc:$it") }
		if (byIsrc != null && byIsrc.groups.isNotEmpty()) return byIsrc
		return search("recording:\"$title\" AND artist:\"$artist\"") ?: SongReleases(null, null, emptyList())
	}

	/** The exact artist by name (score == 100), per spec. */
	suspend fun searchArtist(name: String): MusicBrainzArtist? {
		val result = getJson("/artist", mapOf("query" to "artist:\"$name\"")) ?: return null
		return result["artists"]?.jsonArray.orEmpty()
			.firstOrNull { it.jsonObject["score"]?.jsonPrimitive?.intOrNull == 100 }
			?.jsonObject
			?.let { MusicBrainzArtist(it["id"]?.jsonPrimitive?.contentOrNull.orEmpty(), it["name"]?.jsonPrimitive?.contentOrNull.orEmpty()) }
	}

	/** An artist's discography: albums, EPs and singles, up to 100. */
	suspend fun getArtistReleaseGroups(artistMbid: String): List<MusicBrainzReleaseGroup> {
		val result = getJson(
			"/release-group",
			mapOf("artist" to artistMbid, "type" to "album|ep|single", "limit" to "100")
		) ?: return emptyList()
		return result["release-groups"]?.jsonArray.orEmpty().map { it.jsonObject.toReleaseGroup() }
	}

	/** The releases of a release group, resolved through the representative release. */
	suspend fun getReleaseGroupReleases(rgMbid: String): List<MusicBrainzRelease> {
		val result = getJson("/release-group/$rgMbid", mapOf("inc" to "releases+artist-credits")) ?: return emptyList()
		return result["releases"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toRelease() }
	}

	/** Deezer-disabled fallback search (M3); identity work lives in [MusicBrainzClient]. */
	suspend fun searchMusicBrainz(query: String): Pair<List<DiscoverAlbum>, List<DiscoverArtist>> =
		Pair(emptyList<DiscoverAlbum>(), emptyList<DiscoverArtist>())

	// ---------------- Internals ----------------

	/** One rate-limited, cached, retried GET; null instead of throwing on a bad answer. */
	private suspend fun getJson(path: String, params: Map<String, String> = emptyMap()): JsonObject? {
		val key = path + params.entries.sortedBy { it.key }.joinToString("&", prefix = "?") { "${it.key}=${it.value}" }
		cache[key]?.let { return it }
		val result = rateLimited { fetch(path, params) }
		if (result != null) {
			cache[key] = result
			if (cache.size > 300) cache.remove(cache.keys.first())
		}
		return result
	}

	/** The raw request; retries once after [RETRY_DELAY_MS] when the first call fails. */
	private suspend fun fetch(path: String, params: Map<String, String>): JsonObject? {
		suspend fun request(): JsonObject? = try {
			// every path starts with "/"; without fmt=json MusicBrainz answers in XML
			client.get("$baseUrl$path") {
				header("User-Agent", USER_AGENT)
				parameter("fmt", "json")
				params.forEach { (k, v) -> parameter(k, v) }
			}.takeIf { it.status.isSuccess() }
				?.let { json.parseToJsonElement(it.bodyAsText()) as? JsonObject }
		} catch (_: Exception) {
			null
		}
		var result = request()
		if (result == null) {
			delay(RETRY_DELAY_MS)
			result = request()
		}
		return result
	}

	/** Enforces the 1 s minimum gap between MusicBrainz calls, process-wide. Simplest ceiling: one global mutex. */
	private suspend fun <T> rateLimited(block: suspend () -> T): T = withContext(IoDispatcher) {
		rateLimitMutex.withLock {
			val now = Clock.System.now().toEpochMilliseconds()
			val wait = MIN_INTERVAL_MS - (now - lastRequestMs)
			if (wait > 0) delay(wait)
			lastRequestMs = Clock.System.now().toEpochMilliseconds()
			block()
		}
	}
}

private fun JsonObject.toReleaseGroup() = MusicBrainzReleaseGroup(
	id = this["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
	title = this["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
	score = this["score"]?.jsonPrimitive?.intOrNull,
	primaryType = this["primary-type"]?.jsonPrimitive?.contentOrNull,
	secondaryTypes = this["secondary-types"]?.jsonArray?.map { it.jsonPrimitive.content },
	firstReleaseDate = this["first-release-date"]?.jsonPrimitive?.contentOrNull,
	artistName = this["artist-credit"]?.jsonArray?.firstOrNull()?.jsonObject
		?.get("artist")?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
)

private fun JsonObject.toRelease(): MusicBrainzRelease? {
	val id = this["id"]?.jsonPrimitive?.contentOrNull ?: return null
	return MusicBrainzRelease(
		id = id,
		title = this["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
		date = this["date"]?.jsonPrimitive?.contentOrNull,
		status = this["status"]?.jsonPrimitive?.contentOrNull,
		media = this["media"]?.jsonArray?.firstOrNull()?.jsonObject,
		tracks = this["media"]?.jsonArray?.firstOrNull()?.jsonObject
			?.get("tracks")?.jsonArray?.map { it.jsonObject }
	)
}

/** A MusicBrainz release group (album / EP / single). */
data class MusicBrainzReleaseGroup(
	val id: String,
	val title: String,
	val score: Int?,
	val primaryType: String?,
	val secondaryTypes: List<String>?,
	val firstReleaseDate: String?,
	val artistName: String?,
	/** Tracks on its earliest release, when known (recording search answers carry it). */
	val trackCount: Int? = null
)

/** What a song is on: its artist (from the recording's credit) and the release groups. */
data class SongReleases(
	val artistMbid: String?,
	val artistName: String?,
	val groups: List<MusicBrainzReleaseGroup>
)

/**
 * Folds a `/recording` search answer into [SongReleases]: recordings scoring 90+, their
 * Official releases grouped by release group (dated by, and counting the tracks of, the
 * earliest release), oldest first. Pure, so it is unit-testable.
 */
fun songReleasesOf(result: JsonObject): SongReleases {
	val recordings = result["recordings"]?.jsonArray.orEmpty().map { it.jsonObject }
		.filter { (it["score"]?.jsonPrimitive?.intOrNull ?: 100) >= 90 }
	val artist = recordings.firstOrNull()?.get("artist-credit")?.jsonArray?.firstOrNull()?.jsonObject
		?.get("artist")?.jsonObject
	val releases = recordings.flatMap { it["releases"]?.jsonArray.orEmpty().map { r -> r.jsonObject } }
		.filter { it["status"]?.jsonPrimitive?.contentOrNull == "Official" }
	val groups = releases
		.groupBy { it["release-group"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull }
		.mapNotNull { (id, inGroup) ->
			id ?: return@mapNotNull null
			// undated releases last
			val earliest = inGroup.minBy { it["date"]?.jsonPrimitive?.contentOrNull ?: "9999" }
			val group = earliest["release-group"]!!.jsonObject
			MusicBrainzReleaseGroup(
				id = id,
				title = group["title"]?.jsonPrimitive?.contentOrNull.orEmpty(),
				score = null,
				primaryType = group["primary-type"]?.jsonPrimitive?.contentOrNull,
				secondaryTypes = group["secondary-types"]?.jsonArray?.map { it.jsonPrimitive.content },
				firstReleaseDate = earliest["date"]?.jsonPrimitive?.contentOrNull,
				artistName = artist?.get("name")?.jsonPrimitive?.contentOrNull,
				trackCount = earliest["track-count"]?.jsonPrimitive?.intOrNull
			)
		}
		.sortedBy { it.firstReleaseDate ?: "9999" }
	return SongReleases(
		artistMbid = artist?.get("id")?.jsonPrimitive?.contentOrNull,
		artistName = artist?.get("name")?.jsonPrimitive?.contentOrNull,
		groups = groups
	)
}

/** Compilations, live recordings and DJ mixes: the releases a song's add sheet tucks under "More". */
fun MusicBrainzReleaseGroup.isSecondary() =
	secondaryTypes.orEmpty().any { it == "Compilation" || it == "Live" || it == "DJ-mix" }

/** A MusicBrainz release in its simplest useful form (media/tracks kept as raw JSON). */
data class MusicBrainzRelease(
	val id: String,
	val title: String,
	val date: String?,
	val status: String?,
	val media: JsonObject?,
	val tracks: List<JsonObject>?
)

/** A MusicBrainz artist, identified by its MBID. */
data class MusicBrainzArtist(val id: String, val name: String)

/**
 * Picks the release group to add to Lidarr, per spec: the earliest studio Album
 * (primary type "Album", no secondary types), else the earliest Single, else the
 * earliest EP. Pure, so it is unit-testable without a network or Ktor.
 */
fun selectDefaultReleaseGroup(groups: List<MusicBrainzReleaseGroup>): MusicBrainzReleaseGroup? {
	fun earliest(type: String): MusicBrainzReleaseGroup? =
		groups.filter { it.primaryType == type && it.secondaryTypes.isNullOrEmpty() }
			.sortedBy { it.firstReleaseDate }
			.firstOrNull()
	return earliest("Album") ?: earliest("Single") ?: earliest("EP")
}
