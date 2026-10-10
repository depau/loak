package eu.depau.loak.domain.discover

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
	val mbid: String? = null
)

/** An album Deezer found that isn't in the library (yet). */
data class DiscoverAlbum(
	val id: String,
	val title: String,
	val artist: String,
	val coverUrl: String? = null,
	val year: Int? = null,
	val recordType: String? = null,
	val deezerId: Long? = null,
	val mbid: String? = null
)

/** An artist Deezer found that isn't in the library (yet). */
data class DiscoverArtist(
	val id: String,
	val name: String,
	val pictureUrl: String? = null,
	val deezerId: Long? = null,
	val mbid: String? = null
)

/** The three kinds of Deezer search results, as the search screen shows them. */
data class DiscoverSearchResults(
	val songs: List<DiscoverSong>,
	val albums: List<DiscoverAlbum>,
	val artists: List<DiscoverArtist>
)

private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.deezerId(key: String = "id"): Long? =
	this[key]?.jsonPrimitive?.longOrNull?.takeIf { it != 0L }

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
		deezerId = item.deezerId()
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
		deezerId = item.deezerId()
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
