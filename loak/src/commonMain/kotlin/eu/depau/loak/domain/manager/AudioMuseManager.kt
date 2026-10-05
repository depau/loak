package eu.depau.loak.domain.manager

import eu.depau.loak.di.recordPii
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What the connected AudioMuse-AI can do, read once per connection. */
data class AudioMuseInfo(
	/** "NONE" when no AI service is set up: Ask AI and AI names are hidden then. */
	val aiProvider: String,
	val soundSearch: Boolean,
	val lyricsSearch: Boolean,
	/** Schedules, rebuilding AI playlists and starting analysis need an admin. */
	val isAdmin: Boolean,
	val alchemyDefaultSongs: Int,
	val alchemyMaxSongs: Int
) {
	val canAsk get() = !aiProvider.equals("NONE", ignoreCase = true) && aiProvider.isNotBlank()

	/** The AI service's name as people know it. */
	val aiProviderName get() = when (aiProvider.uppercase()) {
		"OPENAI" -> "OpenAI"
		"GEMINI" -> "Gemini"
		"MISTRAL" -> "Mistral"
		"OLLAMA" -> "Ollama"
		else -> aiProvider.lowercase().replaceFirstChar { it.uppercase() }
	}
}

/** An alchemy recipe AudioMuse-AI refills on its radio schedule, saved as a playlist of the same name. */
data class AudioMuseRadio(
	val id: Int,
	val anchorId: Int,
	val name: String,
	val temperature: Double,
	val songs: Int,
	val enabled: Boolean
)

class AudioMuseException(message: String, val status: HttpStatusCode? = null) : Exception(message)

/**
 * AudioMuse-AI's own REST API, for what Subsonic can't do: alchemy, radios, sound and lyrics
 * search, Ask AI, song paths and its schedules. Signs in with its own username and password
 * (POST /auth, an 8 h cookie, signed in again on 401) or an API token (Bearer, always admin);
 * with sign-in turned off on the server, neither is needed. Track ids are the music server's.
 */
class AudioMuseManager(private val preferenceManager: PreferenceManager) {
	private val json = Json { ignoreUnknownKeys = true }
	private val mutex = Mutex()
	private val client = HttpClient { install(HttpCookies) { storage = AcceptAllCookiesStorage() } }

	val info: StateFlow<AudioMuseInfo?>
		field = MutableStateFlow<AudioMuseInfo?>(null)

	val radios: StateFlow<List<AudioMuseRadio>>
		field = MutableStateFlow<List<AudioMuseRadio>>(emptyList())

	val isConfigured get() = preferenceManager.audioMuseUrl.isNotBlank()

	private val baseUrl get() = preferenceManager.audioMuseUrl.trimEnd('/')
	private var signedIn = false

	/**
	 * Checks [url] with these credentials and, if it answers, keeps them. [token] wins over
	 * [username]/[password]; all empty is fine for a server with sign-in off.
	 */
	suspend fun connect(url: String, username: String, password: String, token: String): AudioMuseInfo {
		val old = listOf(preferenceManager.audioMuseUrl, preferenceManager.audioMuseUsername,
			preferenceManager.audioMusePassword, preferenceManager.audioMuseToken)
		preferenceManager.audioMuseUrl = url.trim().trimEnd('/')
		preferenceManager.audioMuseUsername = username.trim()
		preferenceManager.audioMusePassword = password
		preferenceManager.audioMuseToken = token.trim()
		// its errors go to Sentry too; censor its server address up front
		recordPii(url.trim().trimEnd('/'))
		signedIn = false
		return try {
			refresh() ?: throw AudioMuseException("No answer from AudioMuse-AI")
		} catch (e: Exception) {
			preferenceManager.audioMuseUrl = old[0]
			preferenceManager.audioMuseUsername = old[1]
			preferenceManager.audioMusePassword = old[2]
			preferenceManager.audioMuseToken = old[3]
			info.value = null
			throw e
		}
	}

	fun disconnect() {
		preferenceManager.audioMuseUrl = ""
		preferenceManager.audioMuseUsername = ""
		preferenceManager.audioMusePassword = ""
		preferenceManager.audioMuseToken = ""
		signedIn = false
		info.value = null
		radios.value = emptyList()
	}

	/** Reads what the server can do and its radios. Null when not configured. */
	suspend fun refresh(): AudioMuseInfo? {
		if (!isConfigured) return null
		val config = getJson("api/config").jsonObject
		val provider = config["ai_model_provider"]?.jsonPrimitive?.contentOrNull ?: "NONE"
		// CLAP and lyrics aren't in /api/config: their own endpoints say whether they're on
		val sound = runCatching {
			getJson("api/clap/top_queries").jsonObject["ready"]?.jsonPrimitive?.contentOrNull == "true"
		}.getOrDefault(false)
		val lyrics = runCatching {
			(getJson("api/lyrics/axes").jsonObject["axes"] as? JsonObject)?.isNotEmpty() == true
		}.getOrDefault(false)
		val admin = runCatching { request("api/cron") { }.status.isSuccess() }.getOrDefault(false)
		val found = AudioMuseInfo(
			aiProvider = provider,
			soundSearch = sound,
			lyricsSearch = lyrics,
			isAdmin = admin,
			alchemyDefaultSongs = config["alchemy_default_n_results"]?.jsonPrimitive?.intOrNull ?: 50,
			alchemyMaxSongs = config["alchemy_max_n_results"]?.jsonPrimitive?.intOrNull ?: 200
		)
		info.value = found
		refreshRadios()
		return found
	}

	suspend fun refreshRadios(): List<AudioMuseRadio> {
		if (!isConfigured) return emptyList()
		val list = runCatching {
			getJson("api/radios").jsonObject["radios"]!!.jsonArray.map {
				val r = it.jsonObject
				AudioMuseRadio(
					id = r.int("id"),
					anchorId = r.int("anchor_id"),
					name = r.str("name"),
					temperature = r["temperature"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() ?: 1.0,
					songs = r.int("n_results"),
					enabled = r["enabled"]?.jsonPrimitive?.contentOrNull == "true"
				)
			}
		}.onFailure { Logger.w(TAG, "Couldn't read radios", it) }.getOrNull() ?: return radios.value
		radios.value = list
		return list
	}

	/** Connects in the background at startup, if set up. */
	suspend fun start() {
		runCatching { refresh() }.onFailure { Logger.w(TAG, "AudioMuse-AI unreachable", it) }
	}

	suspend fun getJson(path: String, block: HttpRequestBuilder.() -> Unit = {}): JsonElement =
		parse(request(path, block = block))

	suspend fun postJson(path: String, body: JsonElement): JsonElement = parse(request(path, post = true) {
		contentType(ContentType.Application.Json)
		setBody(body.toString())
	})

	/** Credentials plus extra custom headers for AudioMuse-AI calls, read per request. */
	private fun HttpRequestBuilder.audioMuseAuthHeaders() {
		preferenceManager.audioMuseToken.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
		preferenceManager.customHeadersMap(preferenceManager.audioMuseCustomHeaders).forEach { (key, value) ->
			header(key, value)
		}
		if (preferenceManager.audioMuseInheritServerHeaders) {
			preferenceManager.customHeadersMap().forEach { (key, value) -> header(key, value) }
		}
	}

	/** POSTs [body] and hands each server-sent event's data to [onEvent] as it arrives. */
	suspend fun stream(path: String, body: JsonElement, onEvent: suspend (JsonObject) -> Unit) = withContext(IoDispatcher) {
		if (usesLogin && !signedIn) signIn()
		client.preparePost("$baseUrl/${path.trimStart('/')}") {
			audioMuseAuthHeaders()
			contentType(ContentType.Application.Json)
			setBody(body.toString())
		}.execute { response ->
			if (!response.status.isSuccess()) throw AudioMuseException(response.status.description, response.status)
			val channel = response.bodyAsChannel()
			while (true) {
				val line = channel.readUTF8Line() ?: break
				if (!line.startsWith("data:")) continue
				runCatching { json.parseToJsonElement(line.removePrefix("data:").trim()).jsonObject }
					.getOrNull()?.let { onEvent(it) }
			}
		}
	}

	private suspend fun parse(response: HttpResponse): JsonElement {
		val text = response.bodyAsText()
		if (!response.status.isSuccess()) {
			val message = runCatching {
				json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.contentOrNull
			}.getOrNull() ?: response.status.description
			throw AudioMuseException(message, response.status)
		}
		return json.parseToJsonElement(text)
	}

	/** A request with the credentials; signs in first, or again after a 401. */
	suspend fun request(
		path: String,
		post: Boolean = false,
		put: Boolean = false,
		delete: Boolean = false,
		block: HttpRequestBuilder.() -> Unit
	): HttpResponse = withContext(IoDispatcher) {
		suspend fun send(): HttpResponse {
			val url = "$baseUrl/${path.trimStart('/')}"
			val auth: HttpRequestBuilder.() -> Unit = {
				audioMuseAuthHeaders()
				block()
			}
			return when {
				post -> client.post(url, auth)
				put -> client.put(url, auth)
				delete -> client.delete(url, auth)
				else -> client.get(url, auth)
			}
		}
		if (usesLogin && !signedIn) signIn()
		var response = send()
		if (response.status == HttpStatusCode.Unauthorized && usesLogin) {
			signIn()
			response = send()
		}
		if (response.status == HttpStatusCode.Forbidden && response.bodyAsText().contains("Setup required", true)) {
			throw AudioMuseException("AudioMuse-AI isn't set up yet", response.status)
		}
		response
	}

	private val usesLogin get() = preferenceManager.audioMuseToken.isBlank() && preferenceManager.audioMuseUsername.isNotBlank()

	private suspend fun signIn() = mutex.withLock {
		val response = client.post("$baseUrl/auth") {
			// JSON back instead of a redirect
			header("X-Requested-With", "XMLHttpRequest")
			contentType(ContentType.Application.Json)
			setBody(buildJsonObject {
				put("user", JsonPrimitive(preferenceManager.audioMuseUsername))
				put("password", JsonPrimitive(preferenceManager.audioMusePassword))
			}.toString())
		}
		if (!response.status.isSuccess()) {
			throw AudioMuseException(
				if (response.status == HttpStatusCode.Unauthorized) "Wrong username or password"
				else "Sign-in failed: ${response.status.description}",
				response.status
			)
		}
		signedIn = true
	}

	private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull ?: 0
	private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()

	private companion object {
		const val TAG = "AudioMuseManager"
	}
}
