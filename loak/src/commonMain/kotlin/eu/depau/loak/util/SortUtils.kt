package eu.depau.loak.util

import androidx.room3.RoomRawQuery
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongListType

// TODO: sort with sql instead
fun ImmutableList<DomainSong>.sortedByListType(
	listType: DomainSongListType,
	albums: List<DomainAlbum>
): ImmutableList<DomainSong> {
	return when (listType) {
		DomainSongListType.FrequentlyPlayed -> sortedByDescending { it.playCount }
		DomainSongListType.Newest -> sortedByDescending {
			albums
				.firstOrNull { album -> album.id == it.albumId }
				?.createdAt
		}

		DomainSongListType.Random -> shuffled()

		DomainSongListType.Rating -> sortedByDescending { it.userRating ?: 0 }
		DomainSongListType.Year -> sortedByDescending { it.year }

		is DomainSongListType.ByArtist -> filter {
			it.artistId == listType.artistId
		}.sortedByDescending { it.playCount }

		is DomainSongListType.ByGenre -> filter {
			it.genre == listType.genre
				|| listType.genre in it.genres
		}.sortedByDescending { it.playCount }
	}.toImmutableList()
}

fun DomainAlbumListType.toSqlQuery(): RoomRawQuery {
	var where: String? = null
	var orderBy: String
	val args = mutableListOf<Any>()

	when (this) {
		DomainAlbumListType.AlphabeticalByArtist -> orderBy = "LOWER(artistName) ASC"
		DomainAlbumListType.AlphabeticalByName -> orderBy = "LOWER(name) ASC"
		// ponytail: limit overview queries to 30 items so Room does not fetch thousands of song relations across worker RPC
		DomainAlbumListType.Frequent -> {
			where = "playCount != 0"
			orderBy = "playCount DESC LIMIT 30"
		}

		DomainAlbumListType.Highest -> orderBy = "userRating DESC LIMIT 30"
		DomainAlbumListType.Newest -> orderBy = "createdAt DESC LIMIT 30"
		DomainAlbumListType.Random -> orderBy = "RANDOM() LIMIT 30"
		DomainAlbumListType.Recent -> orderBy = "COALESCE(lastPlayedAt, createdAt) DESC LIMIT 30"

		DomainAlbumListType.Year -> {
			orderBy = "year DESC"
		}

		is DomainAlbumListType.ByGenre -> {
			where = "genre = ?"
			orderBy = "LOWER(name) ASC"
			args.add(genre)
		}

		is DomainAlbumListType.ByYear -> {
			where = "COALESCE(year, 0) BETWEEN ? AND ?"
			orderBy = "LOWER(name) ASC"
			args.add(fromYear)
			args.add(toYear)
		}
	}

	val whereClause = where?.let { " WHERE $it" } ?: ""
	val sql = "SELECT * FROM AlbumEntity$whereClause ORDER BY $orderBy"

	return RoomRawQuery(sql) { statement ->
		args.forEachIndexed { index, arg ->
			val bindIndex = index + 1
			when (arg) {
				is String -> statement.bindText(bindIndex, arg)
				is Int -> statement.bindInt(bindIndex, arg)
				is Long -> statement.bindLong(bindIndex, arg)
				is Float -> statement.bindFloat(bindIndex, arg)
				is Double -> statement.bindDouble(bindIndex, arg)
				is Boolean -> statement.bindInt(bindIndex, if (arg) 1 else 0)
			}
		}
	}
}
