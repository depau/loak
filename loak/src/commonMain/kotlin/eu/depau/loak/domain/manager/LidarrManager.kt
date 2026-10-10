package eu.depau.loak.domain.manager

import eu.depau.loak.di.PlatformType
import eu.depau.loak.di.recordPii
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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

/**
 * Lidarr's own REST API (/api/v1, the one its web UI uses), for what Subsonic can't do:
 * discovering music to grab. Talks to an extra Lidarr instance with its own API key.
 */
class LidarrManager(
	private val preferenceManager: PreferenceManager,
	private val platformType: PlatformType
) {
	private val json = Json { ignoreUnknownKeys = true }
	private val client = HttpClient()

	val info: StateFlow<LidarrInfo?>
		field = MutableStateFlow<LidarrInfo?>(null)

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
