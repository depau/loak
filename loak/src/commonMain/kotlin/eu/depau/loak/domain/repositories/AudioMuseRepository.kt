package eu.depau.loak.domain.repositories

import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.util.IoDispatcher
import io.ktor.client.request.parameter
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What a song alchemy is made of: added (or taken away) songs, artists, playlists, moods, radios. */
@Serializable
data class AlchemyIngredient(
	val id: String,
	val type: Type,
	val label: String,
	val detail: String? = null,
	val coverArtId: String? = null,
	val add: Boolean = true
) {
	@Serializable
	enum class Type(val api: String) { Song("song"), Artist("artist"), Playlist("playlist"), Mood("mood"), Radio("anchor") }
}

/** An alchemy's songs, plus what AudioMuse-AI needs to keep it as a radio. */
class AlchemyResult(val songs: List<DomainSong>, val raw: JsonObject)

/**
 * The AudioMuse-AI features that return songs: alchemy, its ingredients, and saving the
 * results. Results come back as the music server's song ids, read from the local library.
 */
class AudioMuseRepository(
	private val audioMuse: AudioMuseManager,
	private val sessionManager: SessionManager,
	private val songDao: SongDao,
	private val dbRepository: DbRepository
) {
	/** Songs in [ids]' order; ones the local library doesn't have (yet) are left out. */
	suspend fun songs(ids: List<String>): List<DomainSong> = withContext(IoDispatcher) {
		val byId = songDao.getSongsByIds(ids).associateBy { it.songId }
		ids.mapNotNull { byId[it]?.toDomainModel() }
	}

	private fun JsonElement.ids(list: String = "results") =
		(this as? JsonArray ?: this.jsonObject[list]!!.jsonArray).mapNotNull {
			it.jsonObject["item_id"]?.jsonPrimitive?.contentOrNull
		}

	/** Analysed songs matching [query] by title, artist or album. */
	suspend fun searchSongs(query: String): List<DomainSong> =
		songs(audioMuse.getJson("api/search_tracks") {
			parameter("search_query", query)
			parameter("end", 30)
		}.ids())

	suspend fun searchArtists(query: String): List<AlchemyIngredient> =
		audioMuse.getJson("api/search_artists") { parameter("query", query) }.jsonArray.map {
			val o = it.jsonObject
			AlchemyIngredient(
				id = o["artist_id"]?.jsonPrimitive?.contentOrNull ?: o["artist"]!!.jsonPrimitive.content,
				type = AlchemyIngredient.Type.Artist,
				label = o["artist"]!!.jsonPrimitive.content,
				detail = o["track_count"]?.jsonPrimitive?.intOrNull?.toString()
			)
		}

	suspend fun searchPlaylists(query: String): List<AlchemyIngredient> =
		audioMuse.getJson("api/search_playlists") { parameter("query", query) }.jsonArray.map {
			val o = it.jsonObject
			AlchemyIngredient(
				id = o["id"]!!.jsonPrimitive.content,
				type = AlchemyIngredient.Type.Playlist,
				label = o["name"]!!.jsonPrimitive.content,
				detail = o["count"]?.jsonPrimitive?.intOrNull?.toString()
			)
		}

	/** One ingredient per mood (its strongest cluster). */
	suspend fun moods(): List<AlchemyIngredient> =
		audioMuse.getJson("api/mood_centroids").jsonObject.entries
			.filter { (_, centroids) -> centroids.jsonArray.isNotEmpty() }
			.map { (mood, centroids) ->
				val first = centroids.jsonArray.first().jsonObject
				AlchemyIngredient(
					id = "$mood:${first["index"]?.jsonPrimitive?.intOrNull ?: 0}",
					type = AlchemyIngredient.Type.Mood,
					label = mood.replaceFirstChar { it.uppercase() },
					detail = first["top_tags"]?.jsonArray?.take(3)?.joinToString(", ") { it.jsonPrimitive.content }
				)
			}
			.sortedBy { it.label }

	/** Songs that sound like [query] (CLAP, on AudioMuse-AI's server: no AI service involved). */
	suspend fun soundSearch(query: String, limit: Int = 50, steering: List<Pair<String, Boolean>> = emptyList()): List<DomainSong> =
		songs(audioMuse.postJson("api/clap/search", buildJsonObject {
			put("query", JsonPrimitive(query))
			put("limit", JsonPrimitive(limit))
			if (steering.isNotEmpty()) put("steering", JsonArray(steering.map { (term, more) ->
				buildJsonObject {
					put("term", JsonPrimitive(term))
					put("weight", JsonPrimitive(3))
					put("direction", JsonPrimitive(if (more) "more" else "less"))
				}
			}))
		}).ids())

	/** Songs whose lyrics are about [query]. */
	suspend fun lyricsSearch(query: String, limit: Int = 50): List<DomainSong> =
		songs(audioMuse.postJson("api/lyrics/search/text", buildJsonObject {
			put("query", JsonPrimitive(query))
			put("limit", JsonPrimitive(limit))
		}).ids())

	/** Loads the sound model ahead of a search; it unloads after a while idle. */
	suspend fun warmUpSoundSearch() {
		runCatching { audioMuse.request("api/clap/warmup", post = true) {} }
	}

	/** Example queries AudioMuse-AI ships with. */
	suspend fun soundIdeas(): List<String> =
		audioMuse.getJson("api/clap/top_queries").jsonObject["queries"]?.jsonArray
			?.map { it.jsonPrimitive.content }.orEmpty()

	/** "More or less of" terms: a few from each kind (instrument, vocals, mood, tempo…). */
	suspend fun soundConcepts(): List<String> =
		audioMuse.getJson("api/clap/concepts").jsonObject["categories"]?.jsonArray
			?.flatMap { c -> c.jsonObject["terms"]!!.jsonArray.take(4).map { it.jsonObject["term"]!!.jsonPrimitive.content } }
			.orEmpty()

	/** Songs leading from [from] to [to] in [steps] steps; [exact] keeps exactly that many. */
	suspend fun songPath(from: String, to: String, steps: Int, exact: Boolean): List<DomainSong> =
		songs(audioMuse.getJson("api/find_path") {
			parameter("start_song_id", from)
			parameter("end_song_id", to)
			parameter("max_steps", steps)
			if (exact) parameter("path_fix_size", true)
		}.ids("path"))

	/** Songs close to [songId] in both lyrics and sound, the song first. */
	suspend fun similarLyricsAndSound(songId: String, limit: Int = 50): List<DomainSong> =
		songs(audioMuse.postJson("api/sem_grove/search", buildJsonObject {
			put("item_id", JsonPrimitive(songId))
			put("limit", JsonPrimitive(limit))
		}).ids())

	/** POST /api/alchemy. Needs at least one added ingredient. */
	suspend fun alchemy(ingredients: List<AlchemyIngredient>, songs: Int, temperature: Float): AlchemyResult {
		val body = buildJsonObject {
			put("items", JsonArray(ingredients.map {
				buildJsonObject {
					put("id", JsonPrimitive(it.id))
					put("op", JsonPrimitive(if (it.add) "ADD" else "SUBTRACT"))
					put("type", JsonPrimitive(it.type.api))
				}
			}))
			put("n", JsonPrimitive(songs))
			put("temperature", JsonPrimitive(temperature))
		}
		val result = audioMuse.postJson("api/alchemy", body).jsonObject
		return AlchemyResult(songs(result.ids()), result)
	}

	/**
	 * Saves [songIds] as a playlist owned by the user (their music server login goes along).
	 * AudioMuse-AI names it "[name]_instant": it never touches it again.
	 */
	suspend fun savePlaylist(name: String, songIds: List<String>) {
		audioMuse.postJson("api/create_playlist", buildJsonObject {
			put("playlist_name", JsonPrimitive(name))
			put("track_ids", JsonArray(songIds.map(::JsonPrimitive)))
			put("user_creds", buildJsonObject {
				put("user", JsonPrimitive(sessionManager.username))
				put("password", JsonPrimitive(sessionManager.password))
			})
		})
		dbRepository.syncPlaylists()
	}

	/**
	 * Keeps an alchemy as a radio: its blend as an anchor, then a radio on it. AudioMuse-AI
	 * fills a playlist called [name] on its radio schedule; this asks for a first fill now.
	 */
	suspend fun saveRadio(name: String, result: AlchemyResult, songs: Int, temperature: Float) {
		val anchor = audioMuse.postJson("api/anchors", buildJsonObject {
			put("name", JsonPrimitive(name))
			put("centroid", result.raw["add_centroid_vector"] ?: JsonArray(emptyList()))
			result.raw["inclusions"]?.takeIf { it is JsonArray }?.let { put("inclusions", it) }
			result.raw["inclusions_embedding"]?.let { put("inclusions_embedding", it) }
			result.raw["exclusions"]?.takeIf { it is JsonArray }?.let { put("exclusions", it) }
		}).jsonObject["anchor"]!!.jsonObject["id"]!!.jsonPrimitive.content.toInt()
		audioMuse.postJson("api/radios", buildJsonObject {
			put("anchor_id", JsonPrimitive(anchor))
			put("temperature", JsonPrimitive(temperature))
			put("n_results", JsonPrimitive(songs))
			put("enabled", JsonPrimitive(true))
		})
		audioMuse.refreshRadios()
		// ponytail: runs every enabled radio, which is what the schedule does anyway
		runCatching { audioMuse.request("api/radios/run", post = true) {} }
		dbRepository.syncPlaylists()
	}
}
