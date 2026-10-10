package eu.depau.loak.domain.discover

import eu.depau.loak.util.IoDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
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
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock

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

/** The Deezer chart a country line of the picker maps to: its "Top <country>" playlist. */
data class DeezerCountryChart(
	val countryName: String,
	val countryCode: String,
	val playlistId: Long
)

/** English country names as Deezer spells them → ISO 3166-1 alpha-2, for the chart playlists. */
private val COUNTRY_ISO: Map<String, String> = mapOf(
	"Algeria" to "DZ",
	"Argentina" to "AR",
	"Australia" to "AU",
	"Austria" to "AT",
	"Belgium" to "BE",
	"Bolivia" to "BO",
	"Brazil" to "BR",
	"Bulgaria" to "BG",
	"Canada" to "CA",
	"Chile" to "CL",
	"Colombia" to "CO",
	"Costa Rica" to "CR",
	"Croatia" to "HR",
	"Czech Republic" to "CZ",
	"Denmark" to "DK",
	"Ecuador" to "EC",
	"Egypt" to "EG",
	"El Salvador" to "SV",
	"Estonia" to "EE",
	"Finland" to "FI",
	"France" to "FR",
	"Germany" to "DE",
	"Guatemala" to "GT",
	"Honduras" to "HN",
	"Hungary" to "HU",
	"Indonesia" to "ID",
	"Ireland" to "IE",
	"Israel" to "IL",
	"Italy" to "IT",
	"Ivory Coast" to "CI",
	"Jamaica" to "JM",
	"Japan" to "JP",
	"Jordan" to "JO",
	"Kenya" to "KE",
	"Latvia" to "LV",
	"Lebanon" to "LB",
	"Lithuania" to "LT",
	"Malaysia" to "MY",
	"Mexico" to "MX",
	"Morocco" to "MA",
	"Netherlands" to "NL",
	"Nigeria" to "NG",
	"Norway" to "NO",
	"Paraguay" to "PY",
	"Peru" to "PE",
	"Philippines" to "PH",
	"Poland" to "PL",
	"Portugal" to "PT",
	"Romania" to "RO",
	"Russia" to "RU",
	"Saudi Arabia" to "SA",
	"Senegal" to "SN",
	"Serbia" to "RS",
	"Singapore" to "SG",
	"Slovakia" to "SK",
	"Slovenia" to "SI",
	"South Africa" to "ZA",
	"South Korea" to "KR",
	"Spain" to "ES",
	"Sweden" to "SE",
	"Switzerland" to "CH",
	"Thailand" to "TH",
	"Tunisia" to "TN",
	"Turkey" to "TR",
	"UK" to "GB",
	"USA" to "US",
	"Ukraine" to "UA",
	"United Arab Emirates" to "AE",
	"Venezuela" to "VE",
	"Worldwide" to "XW"
)

/** The English name of a country code in the chart table; null when unknown (e.g. a detected ISO not on it). */
fun isoCountryName(iso: String): String? = COUNTRY_ISO.entries.firstOrNull { it.value == iso }?.key

/** The ISO code of an English country name in the chart table; null when [name] isn't a chart country. */
fun countryNameToIso(name: String): String? = COUNTRY_ISO[name]

/**
 * Deezer's "Deezer Charts" account (id 637006841) titles its charts "Top <country>" (and
 * keeps yearly recaps plus a few editorial "Women/Mujeres/SongCatcher" variants). Maps each
 * playlist whose title matches `Top (.+)` to its country, dropping the variants: titles
 * with a year, "Mujeres"/"Mulheres"/"Women"/"Femmes", and "SongCatcher"/"Songcatcher"
 * (case-folded here, they appear under both spellings). Titles the ISO table doesn't know
 * are skipped. Pure, so it can be tested without the network.
 */
fun parseCountryCharts(root: JsonObject): List<DeezerCountryChart> {
	val raw = root["data"] as? JsonArray ?: return emptyList()
	return raw.mapNotNull { it.jsonObject }.mapNotNull { playlist ->
		val playlistId = playlist["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
		val title = playlist["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
		val name = Regex("""^Top (.+)$""").matchEntire(title.trim())?.groupValues?.get(1) ?: return@mapNotNull null
		val lower = name.lowercase()
		if (Regex("""20\d\d""").containsMatchIn(name)) return@mapNotNull null
		if (lower.contains("mujeres") || lower.contains("mulheres") || lower.contains("women") ||
			lower.contains("femmes") || lower.contains("songcatcher")
		) return@mapNotNull null
		val iso = COUNTRY_ISO[name] ?: return@mapNotNull null
		DeezerCountryChart(name, iso, playlistId)
	}
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

	/**
	 * Refetches one track by its Deezer id, to get a fresh preview URL. Deezer's preview
	 * links go stale after a while, so this is how a previously-seen song gets a new one.
	 * Returns null when the id is bogus or the call fails (quota, network, deleted track).
	 */
	suspend fun getTrack(trackId: Long): DiscoverSong? {
		if (trackId <= 0L) return null
		return withContext(IoDispatcher) {
			try {
				discoverSongOf(fetchRaw("track/$trackId"))
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				null
			}
		}
	}

	/** The album's UPC (barcode), for its MusicBrainz identity; null when unknown or the call fails. */
	suspend fun getAlbumUpc(albumId: Long): String? = withContext(IoDispatcher) {
		try {
			fetchRaw("album/$albumId")["upc"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			null
		}
	}

	/** The three searches in parallel, in the order the search screen shows them. */
	suspend fun searchAll(query: String): DiscoverSearchResults = coroutineScope {
		val songs = async { search(query) }
		val albums = async { searchAlbums(query) }
		val artists = async { searchArtists(query) }
		DiscoverSearchResults(songs.await(), albums.await(), artists.await())
	}

	/** The artist named [name] (once normalised); null when Deezer has none or the call fails. */
	suspend fun findArtist(name: String): DiscoverArtist? = orNull {
		searchArtists(name).firstOrNull { normalizeArtist(it.name) == normalizeArtist(name) }
	}

	/** The album [title] by [artist] (once normalised); null when Deezer has none or the call fails. */
	suspend fun findAlbum(title: String, artist: String): DiscoverAlbum? = orNull {
		searchAlbums("$artist $title").firstOrNull {
			normalizeTitle(it.title) == normalizeTitle(title) && normalizeArtist(it.artist) == normalizeArtist(artist)
		}
	}

	// ponytail: exact-title/day cache for preview lookups; a generic TTL cache only pays off with more hits
	private val previewUrlCache = HashMap<String, String?>()

	/**
	 * The preview URL of [title] by [artist], found by a Deezer track search
	 * (`track:"title" artist:"artist"`) and cached per exact title+artist for the session.
	 * Null when Deezer has no match or the call fails. Used by the "made for you"
	 * playlists, whose ListenBrainz tracks carry no preview.
	 */
	suspend fun resolveSongPreview(title: String, artist: String): String? {
		previewUrlCache["$title\u0000$artist"]?.let { return it }
		val url = orNull {
			search("""track:"$title" artist:"$artist"""")
				.firstOrNull { song -> song.previewUrl != null }?.previewUrl
		}
		previewUrlCache["$title\u0000$artist"] = url
		return url
	}

	private suspend fun <T> orNull(block: suspend () -> T?): T? = try {
		block()
	} catch (e: CancellationException) {
		throw e
	} catch (_: Exception) {
		null
	}

	/** An album's full page: its details and complete tracklist (with preview URLs). Null when the call fails. */
	suspend fun getAlbum(albumId: Long): Pair<DiscoverAlbum, List<DiscoverSong>>? =
		withContext(IoDispatcher) {
			try {
				discoverAlbumWithTracksOf(fetchRaw("album/$albumId"))
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				null
			}
		}

	/** An artist's full page. Null when the call fails. */
	suspend fun getArtist(artistId: Long): DiscoverArtist? = withContext(IoDispatcher) {
		try {
			discoverArtistOf(fetchRaw("artist/$artistId"))
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			null
		}
	}

	/** An artist's most popular songs. Empty when the call fails. */
	suspend fun getArtistTopTracks(artistId: Long, limit: Int = 10): List<DiscoverSong> =
		withContext(IoDispatcher) {
			try {
				topKind("artist/$artistId/top", limit) { discoverSongOf(it) }
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				emptyList()
			}
		}

	/** An artist's discography (albums, EPs, singles). Empty when the call fails. */
	suspend fun getArtistAlbums(artistId: Long): List<DiscoverAlbum> =
		withContext(IoDispatcher) {
			try {
				topKind("artist/$artistId/albums", null) { discoverAlbumOf(it) }
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				emptyList()
			}
		}

	// Deezer's "Deezer Charts" account, id 637006841; the list fits in two ~100-item pages
	private companion object {
		const val CHART_PLAYLIST_USER = "637006841"
		const val CHART_PLAYLIST_LIMIT = 100
		const val CHART_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
		const val TRACKS_CACHE_TTL_MS = 60 * 60 * 1000L

		fun nowMs() = Clock.System.now().toEpochMilliseconds()
	}

	// ponytail: per-field TTL caches (charts one day, tracks one hour); a generic cache util only pays off with more entries.
	private var chartsValue: List<DeezerCountryChart>? = null
	private var chartsFetchedAtMs = 0L
	private val chartTracksCache = HashMap<Long, Pair<List<DiscoverSong>, Long>>()
	private val genreTracksCache = HashMap<Long, Pair<List<DiscoverSong>, Long>>()

	/** The "Top <country>" charts of Deezer's Charts account, cached for a day. Empty when the call fails. */
	suspend fun getCountryCharts(): List<DeezerCountryChart> {
		chartsValue?.takeIf { nowMs() - chartsFetchedAtMs < CHART_CACHE_TTL_MS }?.let { return it }
		val fresh = fetchChartPlaylists()
		// don't cache an empty page (a failed call would look cached for a day)
		if (fresh.isNotEmpty() || chartsValue == null) {
			synchronized(this) {
				chartsValue = fresh
				chartsFetchedAtMs = nowMs()
			}
		}
		return fresh
	}

	/** The tracks of one country chart, cached for an hour. Empty when the call fails. */
	suspend fun getChartTracks(playlistId: Long): List<DiscoverSong> {
		chartTracksCache[playlistId]?.takeIf { nowMs() - it.second < TRACKS_CACHE_TTL_MS }?.let { return it.first }
		val tracks = withContext(IoDispatcher) {
			try {
				topKind("playlist/$playlistId/tracks", CHART_PLAYLIST_LIMIT) { discoverSongOf(it) }
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				emptyList()
			}
		}
		synchronized(chartTracksCache) { chartTracksCache[playlistId] = tracks to nowMs() }
		return tracks
	}

	/** The ISO code Deezer infers from the caller's IP (`/infos` → `country_iso`); "" when unknown or it fails. */
	suspend fun getDetectedCountryIso(): String = withContext(IoDispatcher) {
		try {
			fetchRaw("infos")["country_iso"]?.jsonPrimitive?.contentOrNull.orEmpty()
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			""
		}
	}

	/** Deezer's genres (`/genre`), for the "Browse genres" shelf (id 0, "Tutti", kept). Empty when the call fails. */
	suspend fun getGenres(): List<DiscoverGenre> = withContext(IoDispatcher) {
		try {
			val root = fetchRaw("genre")
			(root["data"] as? JsonArray)
				?.map { discoverGenreOf(it.jsonObject) }
				.orEmpty()
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			emptyList()
		}
	}

	/** The tracks of one genre's chart (`/editorial/<id>/charts`), cached for an hour. */
	suspend fun getGenreTracks(genreId: Long): List<DiscoverSong> {
		genreTracksCache[genreId]?.takeIf { nowMs() - it.second < TRACKS_CACHE_TTL_MS }?.let { return it.first }
		val tracks = withContext(IoDispatcher) {
			try {
				// editorial charts fold the genre's top songs under "tracks.data" (alongside albums/artists)
				val root = fetchRaw("editorial/$genreId/charts") { parameter("limit", CHART_PLAYLIST_LIMIT) }
				(root["tracks"] as? JsonObject)?.get("data")?.jsonArray
					?.map { discoverSongOf(it.jsonObject) }
					.orEmpty()
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				emptyList()
			}
		}
		synchronized(genreTracksCache) { genreTracksCache[genreId] = tracks to nowMs() }
		return tracks
	}

	/** The playlist pages of the Charts account, folded into one `data` array for [parseCountryCharts]. */
	private suspend fun fetchChartPlaylists(): List<DeezerCountryChart> {
		val all = mutableListOf<JsonObject>()
		var index = 0
		while (true) {
			val root = withContext(IoDispatcher) {
				fetchRaw("user/$CHART_PLAYLIST_USER/playlists") {
					parameter("limit", CHART_PLAYLIST_LIMIT)
					parameter("index", index)
				}
			}
			val data = (root["data"] as? JsonArray).orEmpty().map { it.jsonObject }
			all += data
			val next = root["next"]?.jsonPrimitive?.contentOrNull
			if (next == null || data.isEmpty()) break
			index += data.size
		}
		return parseCountryCharts(JsonObject(mapOf("data" to JsonArray(all))))
	}

	private suspend fun <T> searchKind(path: String, query: String, map: (JsonObject) -> T): List<T> =
		withContext(IoDispatcher) {
			val root = fetch(path, query)
			(root["data"] as? JsonArray)
				?.map { map(it.jsonObject) }
				.orEmpty()
		}

	/** List endpoints that take an optional `limit` query param instead of a `q`. */
	private suspend fun <T> topKind(path: String, limit: Int?, map: (JsonObject) -> T): List<T> =
		withContext(IoDispatcher) {
			val root = if (limit == null) fetchRaw(path)
			else fetchRaw(path) { parameter("limit", limit) }
			(root["data"] as? JsonArray)
				?.map { map(it.jsonObject) }
				.orEmpty()
		}

	/** One GET, throwing [DeezerException] on error and retrying once after a quota hit. */
	private suspend fun fetch(path: String, query: String): JsonObject =
		fetchRaw(path) { parameter("q", query) }

	private suspend fun fetchRaw(path: String, block: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {}): JsonObject {
		var attemptsLeft = 2 // first try + one quota retry
		while (true) {
			val response = client.get("$baseUrl/$path", block)
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
