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
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Instant

/** The largest page Koito serves; asking for more gets the default instead. */
private const val PAGE_SIZE = 500

/** How far back to page before giving up, so an odd `has_next_page` cannot loop forever. */
private const val MAX_PAGES = 6

/**
 * Maps one Koito listen. Null without a timestamp, a title or an artist — Koito fills all
 * three in, but a partial response should not put a blank row into the ranking. Pure, so it
 * can be tested without the network.
 */
fun koitoListenOf(item: JsonObject): RemoteListen? {
	val time = item["time"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
		?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
	val track = item["track"] as? JsonObject ?: return null
	val title = track["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
	val artist = (track["artists"] as? JsonArray)?.firstNotNullOfOrNull { entry ->
		(entry as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
	} ?: return null
	return RemoteListen(title = title, artist = artist, at = time.epochSeconds)
}

/**
 * Koito, the self-hosted scrobbler: reads the user's own listens off its web API.
 *
 * Koito is *not* a ListenBrainz server — it accepts listens over ListenBrainz's submit
 * endpoint but only ever answers on its own `/apis/web/v1/listens`, paged by hand. Its
 * listens carry a title and artists but no MusicBrainz id, so matching falls back to the
 * name index.
 *
 * [engine] defaults to Ktor's platform engine; tests can inject a MockEngine.
 */
class KoitoHistorySource(
	private val preferenceManager: PreferenceManager,
	engine: HttpClientEngine? = null
) : RemoteHistorySource {
	private val json = Json { ignoreUnknownKeys = true }

	private val client = engine?.let { HttpClient(it) { userAgentConfig() } }
		?: HttpClient { userAgentConfig() }

	private fun HttpClientConfig<*>.userAgentConfig() {
		defaultRequest { header("User-Agent", USER_AGENT) }
	}

	override val id = "koito"

	override val isConfigured get() = preferenceManager.koitoUrl.isNotBlank() &&
		preferenceManager.koitoApiKey.isNotBlank()

	override suspend fun recentListens(from: Long, to: Long, limit: Int): List<RemoteListen>? =
		withContext(IoDispatcher) {
			val base = preferenceManager.koitoUrl.trim().trimEnd('/')
			val key = preferenceManager.koitoApiKey.trim()
			if (base.isBlank() || key.isBlank()) return@withContext null

			/** Null on anything that stops the page: logged here, so the reason is not lost. */
			suspend fun fetchPage(number: Int): JsonObject? {
				val response = try {
					client.get("$base/apis/web/v1/listens") {
						header(HttpHeaders.Authorization, "Token $key")
						parameter("limit", minOf(limit, PAGE_SIZE))
						parameter("page", number)
						parameter("from", from)
						parameter("to", to)
					}
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					Logger.w(TAG, "could not read listens from Koito", e)
					return null
				}
				// Koito is behind whatever the user put in front of it: a wrong URL, a reverse
				// proxy that does not know the host, a key it will not take
				if (!response.status.isSuccess()) {
					Logger.w(TAG, "Koito answered ${response.status} for listens")
					return null
				}
				return runCatching { json.parseToJsonElement(response.bodyAsText()) }
					.getOrNull() as? JsonObject
					?: run {
						Logger.w(TAG, "Koito answered something that is not a listens page")
						null
					}
			}

			val listens = mutableListOf<RemoteListen>()
			var page = 1
			while (listens.size < limit && page <= MAX_PAGES) {
				// the first page never arrived: the source cannot be read at all. A later one
				// failing still leaves the pages before it.
				val body = fetchPage(page) ?: return@withContext if (page == 1) null else listens.take(limit)
				val items = (body["items"] as? JsonArray)
					?.mapNotNull { (it as? JsonObject)?.let(::koitoListenOf) }
					.orEmpty()
				listens += items
				if (items.isEmpty() || body["has_next_page"]?.jsonPrimitive?.booleanOrNull != true) break
				page++
			}
			listens.take(limit)
		}

	private companion object {
		const val TAG = "KoitoHistorySource"
	}
}
