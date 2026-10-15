package eu.depau.loak.domain.discover

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A track Deezer found that isn't in the library (yet). */
data class DiscoverSong(
	val id: String,
	val title: String,
	val artist: String,
	val album: String? = null,
	val duration: Int = 0,
	val previewUrl: String? = null,
	val coverUrl: String? = null,
	val isrc: String? = null,
	val deezerId: Long? = null,
	val mbid: String? = null,
	val albumDeezerId: Long? = null,
	val artistDeezerId: Long? = null
)

/** An album Deezer found that isn't in the library (yet). */
data class DiscoverAlbum(
	val id: String,
	val title: String,
	val artist: String,
	val coverUrl: String? = null,
	val year: Int? = null,
	val recordType: String? = null,
	val upc: String? = null,
	val deezerId: Long? = null,
	val mbid: String? = null,
	val artistDeezerId: Long? = null
)

/** An artist Deezer found that isn't in the library (yet). */
data class DiscoverArtist(
	val id: String,
	val name: String,
	val pictureUrl: String? = null,
	val deezerId: Long? = null,
	val mbid: String? = null
)

/** A Deezer genre, for the "Browse genres" shelf and its chart list. Id 0 is "Tutti" (all). */
data class DiscoverGenre(
	val id: Long,
	val name: String,
	val pictureUrl: String? = null
)

/** A Deezer radio: Deezer's mood/sub-genre layer, e.g. "Grime" under Rap/Hip Hop. Playable via `radio/{id}/tracks`. */
data class DiscoverRadio(
	val id: Long,
	val title: String,
	val pictureUrl: String? = null
)

/** A Deezer editorial playlist (a mood/activities chart, e.g. "Acoustic Hits"), playable via `playlist/{id}/tracks`. */
data class DiscoverPlaylist(
	val id: Long,
	val title: String,
	val pictureUrl: String? = null,
	val trackCount: Int = 0
)

/** The three kinds of Deezer search results, as the search screen shows them. */
data class DiscoverSearchResults(
	val songs: List<DiscoverSong>,
	val albums: List<DiscoverAlbum>,
	val artists: List<DiscoverArtist>
)

/**
 * The card above the sections: the song or album [query] names, if there is one, else the first
 * song, album or artist, in that order. Searching for an album by its title must not answer with
 * one of its tracks, whose card only looks like the album because it prints the album's name.
 */
fun DiscoverSearchResults.topResult(query: String): Any? {
	val wanted = normalizeTitle(query)
	fun <T> List<T>.named(title: (T) -> String) =
		firstOrNull { wanted.isNotEmpty() && normalizeTitle(title(it)) == wanted }

	return songs.named { it.title }
		?: albums.named { it.title }
		?: songs.firstOrNull()
		?: albums.firstOrNull()
		?: artists.firstOrNull()
}

private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.deezerId(key: String = "id"): Long? =
	this[key]?.jsonPrimitive?.longOrNull?.takeIf { it != 0L }

// ponytail: rewrites Deezer's CDN size segment; map cover_xl/picture_xl into the models if that ever changes
/** [url] (a Deezer cover or picture, 250 px by default) at [px] pixels square; other URLs as they are. */
fun deezerImageUrl(url: String?, px: Int): String? = url?.replace("/250x250-", "/${px}x$px-")

/**
 * Maps one Deezer track search result. Cover art and preview urls are the raw Deezer
 * values ([DiscoverSong.coverUrl] prefilled from the 250 px square, sizable if needed).
 */
fun discoverSongOf(item: JsonObject): DiscoverSong {
	val artist = item["artist"]?.jsonObject
	val album = item["album"]?.jsonObject
	return DiscoverSong(
		id = item.str("id") ?: "",
		title = item.str("title") ?: "",
		artist = artist?.str("name").orEmpty(),
		album = album?.str("title"),
		duration = item["duration"]?.jsonPrimitive?.intOrNull ?: 0,
		previewUrl = item.str("preview"),
		coverUrl = album?.str("cover_medium") ?: album?.str("cover_big"),
		isrc = item.str("isrc"),
		deezerId = item.deezerId(),
		albumDeezerId = album?.deezerId(),
		artistDeezerId = artist?.deezerId()
	)
}

/** Maps one Deezer album search result; the year comes from `release_date`. */
fun discoverAlbumOf(item: JsonObject): DiscoverAlbum {
	val artist = item["artist"]?.jsonObject
	return DiscoverAlbum(
		id = item.str("id") ?: "",
		title = item.str("title") ?: "",
		artist = artist?.str("name").orEmpty(),
		coverUrl = item.str("cover_medium") ?: item.str("cover_big"),
		year = item.str("release_date")?.take(4)?.toIntOrNull(),
		recordType = item.str("record_type"),
		upc = item.str("upc")?.takeIf { it.isNotBlank() },
		deezerId = item.deezerId(),
		artistDeezerId = artist?.deezerId()
	)
}

/** Maps one Deezer artist search result. */
fun discoverArtistOf(item: JsonObject): DiscoverArtist {
	return DiscoverArtist(
		id = item.str("id") ?: "",
		name = item.str("name") ?: "",
		pictureUrl = item.str("picture_medium") ?: item.str("picture_big"),
		deezerId = item.deezerId()
	)
}

/** Maps one Deezer genre search result (`/genre`'s `data`). */
fun discoverGenreOf(item: JsonObject): DiscoverGenre {
	return DiscoverGenre(
		id = item.deezerId() ?: 0L,
		name = item.str("name").orEmpty(),
		pictureUrl = item.str("picture_medium") ?: item.str("picture_big")
	)
}

/** Maps one Deezer radio (`genre/{id}/radios`'s `data`). */
fun discoverRadioOf(item: JsonObject): DiscoverRadio {
	return DiscoverRadio(
		id = item.deezerId() ?: 0L,
		title = item.str("title").orEmpty(),
		pictureUrl = item.str("picture_medium") ?: item.str("picture_big")
	)
}

/** Maps one Deezer editorial playlist (`chart/0/playlists`'s or `editorial/{id}/charts`'s `data`). */
fun discoverPlaylistOf(item: JsonObject): DiscoverPlaylist {
	return DiscoverPlaylist(
		id = item.deezerId() ?: 0L,
		title = item.str("title").orEmpty(),
		pictureUrl = item.str("picture_medium") ?: item.str("picture_big"),
		trackCount = item["nb_tracks"]?.jsonPrimitive?.intOrNull ?: 0
	)
}

/**
 * The local plays per genre name, summed over the songs' genre tags. [plays] is the device play
 * log (song id to plays) and [songs] the library's matching songs; each genre name gets the play
 * count of any song carrying it (a song in several genres counts for each). Keys are lowercased.
 * Pure, so it can be tested without the network or the DB.
 */
fun genrePlayCounts(plays: Map<String, Int>, songs: Collection<GenrePlayableSong>): Map<String, Int> {
	val result = mutableMapOf<String, Int>()
	for (song in songs) {
		val count = plays[song.songId] ?: 0
		for (genre in song.genreNames) {
			val key = genre.lowercase()
			result[key] = (result[key] ?: 0) + count
		}
	}
	return result
}

/** What [genrePlayCounts] needs from a library song. */
interface GenrePlayableSong {
	val songId: String
	val genre: String?
	val genres: List<String>
	val genreNames: List<String>
		get() = (genres + listOfNotNull(genre)).distinct()
}

/**
 * Folds an album detail payload (album fields at the top level plus its `tracks.data`
 * array) into the album page pair; its tracks, which carry no album, get this one's.
 * Pure, so it can be tested without the network.
 */
fun discoverAlbumWithTracksOf(root: JsonObject): Pair<DiscoverAlbum, List<DiscoverSong>> {
	val album = discoverAlbumOf(root)
	val tracks = ((root["tracks"] as? JsonObject)?.get("data") as? JsonArray).orEmpty().map {
		val song = discoverSongOf(it.jsonObject)
		song.copy(
			album = song.album ?: album.title,
			coverUrl = song.coverUrl ?: album.coverUrl,
			albumDeezerId = song.albumDeezerId ?: album.deezerId
		)
	}
	return album to tracks
}
