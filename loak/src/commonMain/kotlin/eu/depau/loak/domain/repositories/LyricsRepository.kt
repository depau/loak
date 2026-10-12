package eu.depau.loak.domain.repositories

import com.russhwolf.settings.Settings
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import eu.depau.loak.data.database.dao.LyricDao
import eu.depau.loak.data.database.entities.LyricEntity
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.lyrics.LyricsConfig
import eu.depau.loak.domain.models.lyrics.LyricsLine
import eu.depau.loak.domain.models.lyrics.LyricsProvider
import eu.depau.loak.domain.models.lyrics.LyricsResult
import eu.depau.loak.domain.parser.LyricsContentParser
import eu.depau.loak.util.Logger
import kotlin.time.Duration.Companion.milliseconds

/** Provider name of lyrics the user saved on this device (offset baked in). */
const val LOCAL_PROVIDER = "Lo'ak"

class LyricsRepository(
	private val lyricDao: LyricDao,
	private val settings: Settings,
	private val sessionManager: SessionManager,
	private val connectivityManager: ConnectivityManager
) {

	private val client = HttpClient {
		install(HttpTimeout) {
			requestTimeoutMillis = 40000
			connectTimeoutMillis = 40000
			socketTimeoutMillis = 40000
		}
	}
	private val json = Json { ignoreUnknownKeys = true }

	// regex for finding parenthesis as they are often for crediting producers which can make lrclib
	// miss a search. example "songTitle (prod. producer123)"
	private companion object {
		const val MUSIXMATCH_UA =
			"Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36"
		/** The token is anonymous and reusable; refresh it only when the API asks us to. */
		var musixmatchToken: String? = null

		val PARENTHETICAL = Regex("""\([^)]*\)""")
		val REPEATED_WHITESPACE = Regex("""\s+""")
	}

	private fun getConfig(): LyricsConfig {
		try {
			val raw = settings.getStringOrNull(LyricsConfig.KEY)
				?: return LyricsConfig.Default
			val config: LyricsConfig = json.decodeFromString(raw)
			return config.takeIf { it.version == LyricsConfig.VERSION }
				?: LyricsConfig.Default
		} catch (ex: Exception) {
			Logger.w("LyricsRepository", "failed to load config", ex)
			return LyricsConfig.Default
		}
	}

	/** Persist [rawContent] (already offset-adjusted) so it wins over every provider next time. */
	suspend fun saveLyrics(song: DomainSong, rawContent: String) {
		try {
			lyricDao.insertLyrics(
				LyricEntity(
					songId = song.id,
					providerName = LOCAL_PROVIDER,
					rawContent = rawContent
				)
			)
		} catch (e: Exception) {
			Logger.e("LyricRepository", "Failed to save lyrics for ${song.title}", e)
		}
	}

	private suspend fun getCached(song: DomainSong): LyricEntity? = try {
		lyricDao.getLyrics(song.id)
	} catch (ex: Exception) {
		Logger.w("LyricsRepository", "failed getting cached lyrics", ex)
		null
	}

	/**
	 * Fetch lyrics for [song] from every enabled provider, in priority order.
	 * Lyrics the user saved on this device (offset baked in) are pinned first so they keep
	 * priority, and the auto-cache from the last run is returned when offline.
	 */
	suspend fun fetchAllLyrics(song: DomainSong): List<LyricsResult> {
		val cached = getCached(song)
		val saved = cached?.takeIf { it.providerName == LOCAL_PROVIDER }
		val parse = { entity: LyricEntity ->
			LyricsContentParser.parse(entity.rawContent)?.let { lines ->
				LyricsResult(lines, entity.providerName, entity.rawContent)
			}
		}

		// offline: show the last-known lyrics only (saved copy, or the last provider cache)
		if (!connectivityManager.isOnline.value) {
			val entity = saved ?: cached ?: return emptyList()
			return parse(entity)?.let { listOf(it) } ?: emptyList()
		}

		val currentConfig = getConfig()
		val results = mutableListOf<LyricsResult>()
		saved?.let { s -> parse(s)?.let { results.add(it) } }

		for (provider in currentConfig.providers.filter { it.enabled }) {
			try {
				val parsed = fetchFromProvider(provider, song, currentConfig) ?: continue
				if (saved == null && results.isEmpty()) {
					// cache the highest-priority provider only, as today
					try {
						lyricDao.insertLyrics(
							LyricEntity(
								songId = song.id,
								providerName = provider.id.name,
								rawContent = parsed.rawContent!!
							)
						)
					} catch (e: Exception) {
						Logger.e("LyricRepository", "Failed to cache lyrics for ${song.title}", e)
					}
				}
				results.add(parsed)
			} catch (e: Exception) {
				Logger.e("LyricRepository", "Provider ${provider.id.name} failed!", e)
			}
		}
		return results
	}

	/** Convenience for single-result callers (downloads, the lyrics screen's current view). */
	suspend fun fetchLyrics(song: DomainSong): LyricsResult? = fetchAllLyrics(song).firstOrNull()

	private suspend fun fetchFromProvider(
		provider: LyricsProvider,
		song: DomainSong,
		config: LyricsConfig
	): LyricsResult? {
		var rawContentToCache: String? = null

		val parsedLyrics = when (provider.id) {
			LyricsProvider.Id.LYRICS_PLUS -> {
				val raw = fetchRawLyricsPlus(song, config)
				rawContentToCache = raw
				raw?.let { LyricsContentParser.parse(it) }
			}

			LyricsProvider.Id.LRCLIB -> {
				val raw = fetchRawLrcLib(song, config)
				rawContentToCache = raw
				raw?.let { LyricsContentParser.parse(it) }
			}

			LyricsProvider.Id.MUSIXMATCH -> {
				val raw = fetchRawMusixmatch(song)
				rawContentToCache = raw
				raw?.let { LyricsContentParser.parse(it) }
			}

			LyricsProvider.Id.SUBSONIC -> {
				val subsonicLyrics = sessionManager.api.getLyrics(song.id).firstOrNull()

				val lines = subsonicLyrics?.lines?.flatMap { line ->
					if (!subsonicLyrics.synced && line.value.contains("\n")) {
						line.value.lineSequence()
							.filter { it.isNotBlank() }
							.map { LyricsLine(time = null, text = it.trim()) }
							.toList()
					} else {
						val time =
							if (subsonicLyrics.synced) line.start?.milliseconds else null
						listOf(LyricsLine(time = time, text = line.value))
					}
				}

				if (!lines.isNullOrEmpty()) {
					rawContentToCache = lines.joinToString("\n") { l ->
						val t = l.time
						if (t != null) {
							val m = t.inWholeMinutes.toString().padStart(2, '0')
							val s = (t.inWholeSeconds % 60).toString().padStart(2, '0')
							val ms = ((t.inWholeMilliseconds % 1000) / 10).toString()
								.padStart(2, '0')
							"[$m:$s.$ms]${l.text}"
						} else l.text
					}
				}
				lines
			}
		}

		if (parsedLyrics.isNullOrEmpty()) return null
		return LyricsResult(
			lines = parsedLyrics,
			providerName = provider.id.name,
			rawContent = rawContentToCache
		)
	}

	private suspend fun fetchRawLrcLib(song: DomainSong, config: LyricsConfig): String? {
		return try {
			val response = client.get(config.lrcLibBaseUrl) {
				// using /api/search with q param to search for lyrics more loosely, finds lyrics
				// more consistently because of how one may tag their music. Also looking at how i.e
				// navidrome tags albumless songs with [Unknown Album] which will never find a result
				parameter("q", "${song.title.withoutParentheticals()} ${song.artistName}")
				accept(ContentType.Application.Json)
			}
			if (!response.status.isSuccess()) {
				throw Exception("unsuccessful status code ${response.status.value}")
			}

			// lrclib returns up to 20 results in the form of a json object, select the first one
			// with synced lyrics, or first one with unsynced lyrics if none have any synced. Better
			// matching can be added further to then look for songs that do have a similar album or
			// duration though first result should be fine?
			val results = json.parseToJsonElement(response.bodyAsText()).jsonArray
			val match = results.firstOrNull { it.lyricsOrNull("syncedLyrics") != null }
				?: results.firstOrNull { it.lyricsOrNull("plainLyrics") != null }

			match?.toString()
		} catch (ex: Exception) {
			Logger.w(
				"LyricsRepository",
				"failed fetching from lrclib",
				ex
			)
			null
		}
	}

	private fun JsonElement.lyricsOrNull(key: String): String? =
		jsonObject[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

	// remove parts of song title as defined by regex PARENTHETICAL
	private fun String.withoutParentheticals(): String =
		replace(PARENTHETICAL, " ")
			.replace(REPEATED_WHITESPACE, " ")
			.trim()
			.ifEmpty { this }

	private suspend fun fetchRawLyricsPlus(song: DomainSong, config: LyricsConfig): String? =
		coroutineScope {
			val resultChannel = Channel<String>(Channel.UNLIMITED)
			val jobs = config.lyricsPlusMirrors.map { baseUrl ->
				launch {
					try {
						val response = client.get("$baseUrl/v2/lyrics/get") {
							parameter("title", song.title)
							parameter("artist", song.artistName)
							parameter("album", song.albumTitle)
							parameter("duration", song.duration)
							accept(ContentType.Application.Json)
						}
						if (response.status.isSuccess()) {
							resultChannel.send(response.bodyAsText())
						} else {
							throw Exception("unsuccessful status code ${response.status.value}")
						}
					} catch (ex: Exception) {
						Logger.w("LyricsRepository", "failed fetching from provider $baseUrl", ex)
					}
				}
			}

			launch {
				jobs.joinAll()
				resultChannel.close()
			}

			val result = resultChannel.receiveCatching().getOrNull()
			jobs.forEach { it.cancel() }
			result
		}

	/**
	 * Musixmatch's undocumented client API, reachable without an API key since 2024
	 * (the desktop app's old endpoint returns a canned response; this Android app id works).
	 * A fresh anonymous token is requested once and reused; it's only refreshed when the API
	 * asks for a renew.
	 */
	private suspend fun fetchRawMusixmatch(song: DomainSong): String? {
		val token = musixmatchToken ?: fetchMusixmatchToken() ?: return null
		val response = try {
			client.get("https://apic.musixmatch.com/ws/1.1/macro.subtitles.get") {
				parameter("app_id", "android-player-v1.0")
				parameter("usertoken", token)
				parameter("format", "json")
				parameter("namespace", "lyrics_richsynched")
				parameter("subtitle_format", "lrc")
				parameter("q_artist", song.artistName ?: "")
				parameter("q_track", song.title.withoutParentheticals())
				parameter("q_album", song.albumTitle ?: "")
				header(HttpHeaders.UserAgent, MUSIXMATCH_UA)
				accept(ContentType.Application.Json)
			}
		} catch (ex: Exception) {
			Logger.w("LyricsRepository", "failed fetching from musixmatch", ex)
			return null
		}
		if (!response.status.isSuccess()) {
			// 401/403 = the anonymous token got revoked, get a fresh one next time
			if (response.status.value == 401 || response.status.value == 403) {
				musixmatchToken = null
			}
			return null
		}

		val message = try {
			json.parseToJsonElement(response.bodyAsText()).field("message")
		} catch (ex: Exception) {
			Logger.w("LyricsRepository", "failed parsing musixmatch response", ex)
			null
		} ?: return null

		// the anonymous token was rejected (even on a 200 shell): drop it so the next
		// song requests a fresh one
		if (message.field("header").field("hint").string() == "renew") {
			musixmatchToken = null
			return null
		}
		return musixmatchSubtitle(message)
	}

	private suspend fun fetchMusixmatchToken(): String? = try {
		val response = client.get("https://apic.musixmatch.com/ws/1.1/token.get") {
			parameter("user_language", "en")
			parameter("app_id", "android-player-v1.0")
			header(HttpHeaders.UserAgent, MUSIXMATCH_UA)
		}
		if (!response.status.isSuccess()) return null
		val token = json.parseToJsonElement(response.bodyAsText())
			.jsonObject["message"]?.jsonObject
			?.get("body")?.jsonObject
			?.get("user_token")?.jsonPrimitive?.contentOrNull
			?.takeIf { it.isNotBlank() && it.first() != '0' }
		if (token == null) return null
		musixmatchToken = token
		token
	} catch (ex: Exception) {
		Logger.w("LyricsRepository", "failed fetching musixmatch token", ex)
		null
	}

}

// Musixmatch answers `[]` where an object is empty ("body": [] when nothing matched), so
// every step tolerates a non-object instead of throwing like `.jsonObject` would.
private fun JsonElement?.field(key: String): JsonElement? = (this as? JsonObject)?.get(key)

private fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull

/** The LRC body of a `macro.subtitles.get` answer's `message`, or null when it has none. */
internal fun musixmatchSubtitle(message: JsonElement): String? =
	(message.field("body").field("macro_calls").field("track.subtitles.get")
		.field("message").field("body").field("subtitle_list") as? JsonArray)
		?.firstOrNull().field("subtitle").field("subtitle_body").string()
		?.takeIf { it.isNotBlank() }
