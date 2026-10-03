package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.SmartCriteria
import eu.depau.loak.util.IoDispatcher
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What the server says it is, from ping. */
data class ServerInfo(val type: String?, val version: String?) {
	val isNavidrome get() = type.equals("navidrome", ignoreCase = true)

	/**
	 * Smart playlists can be made and edited here: Navidrome's own API takes "rules" since smart
	 * playlists exist, but partial updates only stop wiping the other fields in 0.62.
	 */
	val canEditSmartPlaylists get() = isNavidrome && versionAtLeast(0, 62)

	private fun versionAtLeast(vararg wanted: Int): Boolean {
		val parts = version?.substringBefore(' ')?.split('.')?.map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
			?: return false
		for ((i, w) in wanted.withIndex()) {
			val have = parts.getOrElse(i) { 0 }
			if (have != w) return have > w
		}
		return true
	}
}

/** A smart playlist as Navidrome's own API returns it. */
data class SmartPlaylistDetails(
	val criteria: SmartCriteria,
	/** Imported from a .nsp file on the server: the next scan would overwrite edits. */
	val fromFile: Boolean
)

/**
 * Navidrome's own REST API (/api, the one its web UI uses), for what Subsonic can't do: smart
 * playlist rules. It logs in with the same username and password as Subsonic, to a JWT.
 */
class NavidromeManager(private val sessionManager: SessionManager) {
	private val json = Json { ignoreUnknownKeys = true }
	private val mutex = Mutex()
	private var info: Pair<String, ServerInfo>? = null
	private var token: Pair<String, String>? = null

	private val baseUrl get() = sessionManager.instanceUrl.trimEnd('/')

	/** Cached per server address. */
	suspend fun serverInfo(): ServerInfo = withContext(IoDispatcher) {
		mutex.withLock {
			val url = baseUrl
			info?.takeIf { it.first == url }?.second?.let { return@withContext it }
			val response = runCatching {
				val body = sessionManager.api.httpClient.get("ping.view").bodyAsText()
				json.parseToJsonElement(body).jsonObject["subsonic-response"]?.jsonObject
			}.getOrNull()
			val found = ServerInfo(
				type = (response?.get("type") as? JsonPrimitive)?.content,
				version = (response?.get("serverVersion") as? JsonPrimitive)?.content
			)
			// don't remember a failure: the next call may reach the server
			if (response != null) info = url to found
			found
		}
	}

	suspend fun smartPlaylist(id: String): SmartPlaylistDetails? = withContext(IoDispatcher) {
		val body = request { sessionManager.api.httpClient.get("$baseUrl/api/playlist/$id") { it() } }
		val playlist = json.parseToJsonElement(body).jsonObject
		val rules = playlist["rules"] as? JsonObject ?: return@withContext null
		SmartPlaylistDetails(
			criteria = SmartCriteria.parse(rules),
			fromFile = (playlist["path"] as? JsonPrimitive)?.content.orEmpty().endsWith(".nsp", ignoreCase = true)
		)
	}

	/** Creates a smart playlist; returns its id. */
	suspend fun createSmartPlaylist(
		name: String,
		comment: String,
		public: Boolean,
		criteria: SmartCriteria
	): String = withContext(IoDispatcher) {
		val body = request {
			sessionManager.api.httpClient.post("$baseUrl/api/playlist") {
				it()
				jsonBody(name, comment, public, criteria)
			}
		}
		json.parseToJsonElement(body).jsonObject["id"]!!.jsonPrimitive.content
	}

	suspend fun updateSmartPlaylist(
		id: String,
		name: String,
		comment: String,
		public: Boolean,
		criteria: SmartCriteria
	) {
		withContext(IoDispatcher) {
			request {
				sessionManager.api.httpClient.put("$baseUrl/api/playlist/$id") {
					it()
					jsonBody(name, comment, public, criteria)
				}
			}
		}
	}

	private fun HttpRequestBuilder.jsonBody(name: String, comment: String, public: Boolean, criteria: SmartCriteria) {
		// exactly application/json: anything else is taken for an M3U import
		contentType(ContentType.Application.Json)
		setBody(buildJsonObject {
			put("name", JsonPrimitive(name))
			put("comment", if (comment.isEmpty()) JsonNull else JsonPrimitive(comment))
			put("public", JsonPrimitive(public))
			put("rules", criteria.toJson())
		}.toString())
	}

	/**
	 * Runs [call] with the JWT, logging in first or again once it expired. [call] gets a block
	 * that adds the auth header to its request.
	 */
	private suspend fun request(call: suspend (HttpRequestBuilder.() -> Unit) -> HttpResponse): String {
		suspend fun attempt(fresh: Boolean): HttpResponse {
			val jwt = mutex.withLock { currentToken(fresh) }
			return call { header(AUTH_HEADER, "Bearer $jwt") }
		}

		var response = attempt(fresh = false)
		if (response.status == HttpStatusCode.Unauthorized) response = attempt(fresh = true)
		if (!response.status.isSuccess()) {
			throw IllegalStateException("Navidrome answered ${response.status}: ${response.bodyAsText().take(200)}")
		}
		// Navidrome hands back a refreshed token on every answer
		response.headers[AUTH_HEADER]?.removePrefix("Bearer ")?.let { refreshed ->
			mutex.withLock { token = baseUrl to refreshed }
		}
		return response.bodyAsText()
	}

	private suspend fun currentToken(fresh: Boolean): String {
		val url = baseUrl
		if (!fresh) token?.takeIf { it.first == url }?.second?.let { return it }
		val response = sessionManager.api.httpClient.post("$url/auth/login") {
			contentType(ContentType.Application.Json)
			setBody(buildJsonObject {
				put("username", JsonPrimitive(sessionManager.username))
				put("password", JsonPrimitive(sessionManager.password))
			}.toString())
		}
		if (!response.status.isSuccess()) throw IllegalStateException("Navidrome login failed: ${response.status}")
		val jwt = json.parseToJsonElement(response.bodyAsText()).jsonObject["token"]!!.jsonPrimitive.content
		token = url to jwt
		return jwt
	}

	private companion object {
		const val AUTH_HEADER = "x-nd-authorization"
	}
}
