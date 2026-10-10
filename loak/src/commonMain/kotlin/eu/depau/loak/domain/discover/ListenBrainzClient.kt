package eu.depau.loak.domain.discover

import eu.depau.loak.util.IoDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** The algorithm the labs similar-artists endpoint scores with (no auth needed). */
private const val SIMILAR_ARTIST_ALGORITHM =
	"session_based_days_7500_session_300_contribution_5_threshold_10_limit_100_filter_True_skip_30"

/** Maps one ListenBrainz similar-artists entry. Null when either identity field is missing. */
fun similarArtistOf(item: JsonObject): DiscoverArtist? {
	val mbid = item["artist_mbid"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
	val name = item["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
	return DiscoverArtist(id = mbid, name = name, mbid = mbid)
}

/**
 * Maps one ListenBrainz fresh-release entry to a [DiscoverAlbum], keyed on the release
 * group MBID; the cover points at Cover Art Archive. Null when any identity field is
 * missing.
 */
fun freshReleaseAlbumOf(item: JsonObject): DiscoverAlbum? {
	val releaseGroup = item["release_group_mbid"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
		?: return null
	val title = item["release_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
	val artist = item["artist_credit_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
		?: return null
	val coverUrl = buildString {
		val caaReleaseMbid = item["caa_release_mbid"]?.jsonPrimitive?.contentOrNull
		val caaId = item["caa_id"]?.jsonPrimitive?.contentOrNull
		if (caaReleaseMbid != null && caaId != null) {
			append("https://coverartarchive.org/release/$caaReleaseMbid/$caaId-250.jpg")
		}
	}.takeIf { it.isNotEmpty() }
	return DiscoverAlbum(
		id = releaseGroup,
		title = title,
		artist = artist,
		coverUrl = coverUrl,
		mbid = releaseGroup
	)
}

/**
 * ListenBrainz API client (no auth needed for what Discover uses): fresh releases off the
 * main API and similar artists off the Labs API, keyed on the "session based" algorithm.
 */
class ListenBrainzClient {
	private val json = Json { ignoreUnknownKeys = true }
	private val client = HttpClient()
	private val baseUrl = "https://api.listenbrainz.org/1"
	private val labsBaseUrl = "https://labs.api.listenbrainz.org"

	/**
	 * Albums released in the last two weeks, newest first. A failure (network, non-200,
	 * unexpected payload) yields an empty list, so a broken shelf degrades to nothing.
	 */
	suspend fun getFreshReleases(): List<DiscoverAlbum> = withContext(IoDispatcher) {
		try {
			val root = client.get("$baseUrl/explore/fresh-releases/") {
				parameter("days", 14)
				parameter("sort", "release_date")
				parameter("past", true)
				parameter("future", false)
			}.takeIf { it.status.isSuccess() }
				?.let { json.parseToJsonElement(it.bodyAsText()) as? JsonObject }
				?: return@withContext emptyList()
			(root["payload"] as? JsonObject)
				?.get("releases")?.jsonArray?.mapNotNull { freshReleaseAlbumOf(it.jsonObject) }
				.orEmpty()
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			emptyList()
		}
	}

	/**
	 * Artists similar to [artistMbid], highest score first (the API already sorts by
	 * score). A failure (network, non-200, unexpected payload) yields an empty list, so
	 * a broken similar-artists shelf degrades to "no suggestions".
	 */
	suspend fun getSimilarArtists(artistMbid: String): List<DiscoverArtist> =
		withContext(IoDispatcher) {
			val list = try {
				fetch("/similar-artists/json", artistMbid)
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				JsonArray(emptyList())
			}
			list.mapNotNull { (it as? JsonObject)?.let(::similarArtistOf) }
		}

	private suspend fun fetch(path: String, artistMbid: String): JsonArray {
		val body = client.get("$labsBaseUrl$path") {
			parameter("artist_mbids", artistMbid)
			parameter("algorithm", SIMILAR_ARTIST_ALGORITHM)
		}
		if (!body.status.isSuccess()) throw RuntimeException("ListenBrainz HTTP ${body.status.value}")
		return json.parseToJsonElement(body.bodyAsText()).jsonArray
	}
}
