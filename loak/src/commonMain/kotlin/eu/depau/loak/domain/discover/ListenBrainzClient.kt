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
import kotlinx.serialization.json.JsonPrimitive
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

/** The Cover Art Archive image attached to a ListenBrainz release or playlist track. */
private fun coverArtUrlOf(item: JsonObject): String? {
	val releaseMbid = item["caa_release_mbid"]?.jsonPrimitive?.contentOrNull
	val id = item["caa_id"]?.jsonPrimitive?.contentOrNull
	return if (releaseMbid.isNullOrBlank() || id.isNullOrBlank()) null
	else "https://coverartarchive.org/release/$releaseMbid/$id-250.jpg"
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
	val coverUrl = coverArtUrlOf(item)
	return DiscoverAlbum(
		id = releaseGroup,
		title = title,
		artist = artist,
		coverUrl = coverUrl,
		year = item["release_date"]?.jsonPrimitive?.contentOrNull?.take(4)?.toIntOrNull(),
		recordType = item["release_group_primary_type"]?.jsonPrimitive?.contentOrNull,
		mbid = releaseGroup
	)
}

/** A ListenBrainz "made for you" playlist the user got on their creator. */
data class ListenBrainzPlaylist(
	val mbid: String,
	val title: String,
	val creator: String,
	val trackCount: Int
)

/** The recording mbid in a JSPF track's `identifier`, read as a path segment. */
private val RECORDING_MBID = Regex("""^https://musicbrainz\.org/recording/([0-9a-fA-F-]{36})$""")
/** The playlist mbid in a JSPF playlist's `identifier`. */
private val PLAYLIST_MBID = Regex("""^https://listenbrainz\.org/playlist/([0-9a-fA-F-]{36})$""")
private const val JSPF_TRACK_EXTENSION = "https://musicbrainz.org/doc/jspf#track"

/**
 * Maps one JSPF track (inside a `GET /playlist/<mbid>` payload) to a [DiscoverSong].
 * Title and creator are required; the recording mbid is taken from the `identifier`
 * field (an array) where present, and the album comes along when the API fills it.
 * Pure, so it can be tested without the network.
 */
fun listenBrainzTrackOf(item: JsonObject): DiscoverSong? {
	val title = item["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
	val artist = item["creator"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
	val recordingMbid = (item["identifier"] as? JsonArray).orEmpty()
		.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
		.firstNotNullOfOrNull { RECORDING_MBID.matchEntire(it)?.groupValues?.get(1) }
	val metadata = ((item["extension"] as? JsonObject)?.get(JSPF_TRACK_EXTENSION) as? JsonObject)
		?.get("additional_metadata") as? JsonObject
	return DiscoverSong(
		id = recordingMbid ?: "$title $artist",
		title = title,
		artist = artist,
		album = item["album"]?.jsonPrimitive?.contentOrNull,
		coverUrl = metadata?.let(::coverArtUrlOf),
		mbid = recordingMbid
	)
}

/**
 * Maps one "made for you" playlist (one element of a `GET /user/<u>/playlists/createdfor`
 * payload, where each entry wraps the actual playlist in a `playlist` key) to a
 * [ListenBrainzPlaylist]. The mbid comes from the `identifier` URL, the count from the
 * `track` array (persistently empty in createdfor responses). Null without an mbid; a
 * blank title still maps (title is printed as-is). Pure, so it can be tested without
 * the network.
 */
fun createdForPlaylistOf(item: JsonObject): ListenBrainzPlaylist? {
	val playlist = item["playlist"] as? JsonObject ?: return null
	val mbid = (playlist["identifier"] as? JsonPrimitive)?.contentOrNull
		?.let { PLAYLIST_MBID.matchEntire(it)?.groupValues?.get(1) } ?: return null
	val title = playlist["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
	val creator = playlist["creator"]?.jsonPrimitive?.contentOrNull.orEmpty()
	val trackCount = (playlist["track"] as? JsonArray)?.size ?: 0
	return ListenBrainzPlaylist(mbid, title, creator, trackCount)
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
	 * True when ListenBrainz knows [username] ("made for you" playlists are public, so just
	 * a 200 from a one-item query). False on 404 (unknown user) and any other failure.
	 */
	suspend fun checkUser(username: String): Boolean = withContext(IoDispatcher) {
		if (username.isBlank()) return@withContext false
		try {
			client.get("$baseUrl/user/$username/playlists/createdfor") {
				parameter("count", 1)
			}.status.isSuccess()
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			false
		}
	}

	/**
	 * The "made for you" playlists the creator built for [username] (Weekly Exploration,
	 * Weekly Jams, Daily Jams…), newest first; the caller's `take()` keeps the newest.
	 * Unknown user or failure → empty.
	 */
	suspend fun getCreatedForPlaylists(username: String): List<ListenBrainzPlaylist> =
		withContext(IoDispatcher) {
			try {
				val root = client.get("$baseUrl/user/$username/playlists/createdfor") {
					parameter("count", 10)
				}.takeIf { it.status.isSuccess() }
					?.let { json.parseToJsonElement(it.bodyAsText()) as? JsonObject }
					?: return@withContext emptyList()
				(root["playlists"] as? JsonArray)
					?.mapNotNull { (it as? JsonObject)?.let(::createdForPlaylistOf) }
					.orEmpty()
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				emptyList()
			}
		}

	/**
	 * The tracks of one "made for you" playlist (JSPF). Songs carry no preview URL — the
	 * caller resolves those lazily via Deezer so the ListenBrainz quota isn't spent here.
	 * Unknown playlist or failure → empty.
	 */
	suspend fun getPlaylistTracks(playlistMbid: String): List<DiscoverSong> =
		withContext(IoDispatcher) {
			try {
				val root = client.get("$baseUrl/playlist/$playlistMbid")
					.takeIf { it.status.isSuccess() }
					?.let { json.parseToJsonElement(it.bodyAsText()) as? JsonObject }
					?: return@withContext emptyList()
				((root["playlist"] as? JsonObject)?.get("track") as? JsonArray)
					?.mapNotNull { (it as? JsonObject)?.let(::listenBrainzTrackOf) }
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
