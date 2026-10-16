package eu.depau.loak.domain.history

import eu.depau.loak.domain.discover.USER_AGENT
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Lo'ak's own Last.fm API key, registered to the app rather than to a user.
 *
 * Not a secret: it travels in the URL of every call, which is why the app can ship with one
 * and users only type their username. The matching shared secret, which the write (scrobble)
 * flow would need, is deliberately not in the app — Lo'ak only ever reads.
 */
private const val API_KEY = "2b996bc6f20144c8e9896f44b4a38767" // gitleaks:allow

private const val ENDPOINT = "https://ws.audioscrobbler.com/2.0/"

/** The most recent tracks one page holds — the API's own maximum. */
private const val PAGE_SIZE = 200

/** How far back to page before giving up, so a bad `totalPages` cannot loop forever. */
private const val MAX_PAGES = 6

/**
 * Maps one Last.fm recent track. Null without a timestamp, a title or an artist: the track
 * playing right now has no `date` until it is scrobbled, and Lo'ak has its own play log for
 * that. Pure, so it can be tested without the network.
 */
fun lastFmListenOf(item: JsonObject): RemoteListen? {
	val at = item["date"]?.jsonObject?.get("uts")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
		?: return null
	val title = item["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
	val artist = (item["artist"] as? JsonObject)?.get("#text")?.jsonPrimitive?.contentOrNull
		?.takeIf { it.isNotBlank() } ?: return null
	return RemoteListen(
		title = title,
		artist = artist,
		at = at,
		// filled in far more often than not, and empty rather than absent when it is not
		mbid = item["mbid"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
	)
}

/**
 * Last.fm's recent tracks: a username and the app's own API key, no user authentication —
 * reading someone's scrobbles is public, which is why this is the simplest of the sources.
 *
 * [engine] defaults to Ktor's platform engine; tests can inject a MockEngine.
 */
class LastFmHistorySource(
	private val preferenceManager: PreferenceManager,
	engine: HttpClientEngine? = null
) : RemoteHistorySource {
	private val json = Json { ignoreUnknownKeys = true }

	private val client = engine?.let { HttpClient(it) { userAgentConfig() } }
		?: HttpClient { userAgentConfig() }

	private fun HttpClientConfig<*>.userAgentConfig() {
		defaultRequest { header("User-Agent", USER_AGENT) }
	}

	override val id = "lastfm"

	override val isConfigured get() = preferenceManager.lastFmUsername.isNotBlank()

	override suspend fun recentListens(from: Long, to: Long, limit: Int): List<RemoteListen> =
		withContext(IoDispatcher) {
			if (!isConfigured) return@withContext emptyList()
			val listens = mutableListOf<RemoteListen>()
			var page = 1
			while (listens.size < limit && page <= MAX_PAGES) {
				val body = try {
					client.get(ENDPOINT) {
						parameter("method", "user.getrecenttracks")
						parameter("user", preferenceManager.lastFmUsername.trim())
						parameter("api_key", API_KEY)
						parameter("format", "json")
						parameter("limit", PAGE_SIZE)
						parameter("from", from)
						parameter("to", to)
						parameter("page", page)
					}.takeIf { it.status.isSuccess() }
						?.let { json.parseToJsonElement(it.bodyAsText()) as? JsonObject }
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					Logger.w(TAG, "could not read scrobbles from Last.fm", e)
					null
				}

				val recent = body?.get("recenttracks") as? JsonObject
				// a single scrobble comes back as an object rather than a one-element array
				val items = when (val tracks = recent?.get("track")) {
					is JsonArray -> tracks.mapNotNull { (it as? JsonObject)?.let(::lastFmListenOf) }
					is JsonObject -> listOfNotNull(lastFmListenOf(tracks))
					else -> emptyList()
				}
				listens += items
				val totalPages = recent?.get("@attr")?.jsonObject?.get("totalPages")
					?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
				if (items.isEmpty() || page >= totalPages) break
				page++
				// the API rate-limits per IP, and it is easy to be polite about it
				delay(PAGE_PAUSE_MS)
			}
			listens.take(limit)
		}

	private companion object {
		const val TAG = "LastFmHistorySource"

		/** Between pages: the API allows a handful of calls a second, this stays well under it. */
		const val PAGE_PAUSE_MS = 250L
	}
}
