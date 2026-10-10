package eu.depau.loak.domain.discover

import eu.depau.loak.util.IoDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A Deezer API error. [errorMsg] is the message part of the payload (commonly missing). */
class DeezerException(
	val type: String,
	val code: Int,
	val errorMsg: String
) : Exception("Deezer error $type ($code): $errorMsg")

/**
 * Extracts Deezer's error-in-200 object, when present. Successful payloads have no
 * top-level "error" and return null; anything else is left for the caller to decode.
 * Pure, so it can be tested without the network.
 */
fun deezerErrorOf(root: JsonObject): DeezerException? {
	val error = root["error"] ?: return null
	val obj = error as? JsonObject ?: return null
	return DeezerException(
		type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
		code = obj["code"]?.jsonPrimitive?.intOrNull ?: -1,
		errorMsg = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
	)
}

/**
 * Deezer's public search API. No auth, no key; errors come back as HTTP 200 with an
 * "error" object, and code 4 (quota) gets one retry after a short pause.
 */
class DeezerClient {
	private val json = Json { ignoreUnknownKeys = true }
	private val client = HttpClient()
	private val baseUrl = "https://api.deezer.com"

	suspend fun search(query: String): List<DiscoverSong> =
		searchKind("search", query) { discoverSongOf(it) }

	suspend fun searchAlbums(query: String): List<DiscoverAlbum> =
		searchKind("search/album", query) { discoverAlbumOf(it) }

	suspend fun searchArtists(query: String): List<DiscoverArtist> =
		searchKind("search/artist", query) { discoverArtistOf(it) }

	/** The three searches in parallel, in the order the search screen shows them. */
	suspend fun searchAll(query: String): DiscoverSearchResults = coroutineScope {
		val songs = async { search(query) }
		val albums = async { searchAlbums(query) }
		val artists = async { searchArtists(query) }
		DiscoverSearchResults(songs.await(), albums.await(), artists.await())
	}

	private suspend fun <T> searchKind(path: String, query: String, map: (JsonObject) -> T): List<T> =
		withContext(IoDispatcher) {
			val root = fetch(path, query)
			(root["data"] as? JsonArray)
				?.map { map(it.jsonObject) }
				.orEmpty()
		}

	/** One GET, throwing [DeezerException] on error and retrying once after a quota hit. */
	private suspend fun fetch(path: String, query: String): JsonObject {
		var attemptsLeft = 2 // first try + one quota retry
		while (true) {
			val response = client.get("$baseUrl/$path") { parameter("q", query) }
			val text = response.bodyAsText()
			if (!response.status.isSuccess()) {
				throw DeezerException("Http", response.status.value, response.status.description)
			}
			val root = json.parseToJsonElement(text).jsonObject
			val error = deezerErrorOf(root)
			if (error != null) {
				if (error.code == 4 && attemptsLeft-- > 1) {
					delay(5_000)
					continue
				}
				throw error
			}
			return root
		}
	}
}
