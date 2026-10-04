package eu.depau.loak.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import eu.depau.loak.data.database.entities.SongEntity
import eu.depau.loak.util.Logger
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

@Dao
interface SongDao {
	@Query("SELECT * FROM SongEntity WHERE songId = :songId LIMIT 1")
	suspend fun getSongById(songId: String): SongEntity?

	@Query("UPDATE SongEntity SET starredAt = :starredAt WHERE songId = :songId")
	suspend fun updateSongStarredAt(songId: String, starredAt: Instant?)

	@Query("UPDATE SongEntity SET userRating = :userRating WHERE songId = :songId")
	suspend fun updateSongRating(songId: String, userRating: Int)

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertSongsIgnoringConflicts(songs: List<SongEntity>)

	@Update
	suspend fun updateSongs(songs: List<SongEntity>)

	/**
	 * Insert or update in place, so a re-sync refreshes stars, play counts, ratings and tags.
	 * Not REPLACE: that deletes the row first, and the delete cascades to the song's playlist
	 * links. (Room's @Upsert degrades to a bare INSERT on web.)
	 */
	@Transaction
	suspend fun upsertSongs(songs: List<SongEntity>) {
		insertSongsIgnoringConflicts(songs)
		updateSongs(songs)
	}

	@Query("SELECT * FROM SongEntity")
	suspend fun getAllSongs(): List<SongEntity>

	@Query("SELECT * FROM SongEntity WHERE belongsToAlbumId = :albumId")
	suspend fun getSongsByAlbumId(albumId: String): List<SongEntity>

	@Query("DELETE FROM SongEntity WHERE songId = :songId")
	suspend fun deleteSong(songId: String)

	// TODO
	@Query("SELECT EXISTS(SELECT 1 FROM SongEntity WHERE songId = :songId AND starredAt IS NOT NULL)")
	suspend fun isSongStarred(songId: String): Boolean

	@Query("SELECT EXISTS(SELECT 1 FROM SongEntity WHERE songId = :songId AND starredAt IS NOT NULL)")
	fun observeSongStarred(songId: String): Flow<Boolean>

	@Query("SELECT userRating FROM SongEntity WHERE songId = :songId")
	suspend fun getSongRating(songId: String): Int?

	@Query("DELETE FROM SongEntity")
	suspend fun clearAllSongs()

	@Query("SELECT songId FROM SongEntity")
	suspend fun getAllSongIds(): List<String>

	@Query("SELECT * FROM SongEntity WHERE songId IN (:ids)")
	suspend fun getSongsByIds(ids: List<String>): List<SongEntity>

	@Query("SELECT * FROM SongEntity ORDER BY RANDOM() LIMIT :count")
	suspend fun getRandomSongs(count: Int): List<SongEntity>

	@Query("SELECT * FROM SongEntity WHERE title LIKE '%' || :query || '%' COLLATE NOCASE")
	suspend fun searchSongsList(query: String): List<SongEntity>

	@Query("SELECT * FROM SongEntity WHERE artistId = :artistId")
	suspend fun getSongsByArtistId(artistId: String): List<SongEntity>

	@Transaction
	suspend fun updateSongsByAlbumId(albumId: String, remoteSongs: List<SongEntity>) {
		val remoteIds = remoteSongs.map { it.songId }.toSet()
		getSongsByAlbumId(albumId).forEach { localSong ->
			if (localSong.songId !in remoteIds) {
				Logger.w("SongDao", "song ${localSong.songId} no longer belongs to album $albumId")
				deleteSong(localSong.songId)
			}
		}
		// Songs already exist from the library sync; on web the Room @Upsert
		// degrades to a bare INSERT so re-inserting trips the PK. IGNORE
		// re-pins identical rows and skips ones already present, which is
		// enough here: data freshness comes from the full sync, this refresh
		// only reconciles membership.
		insertSongsIgnoringConflicts(remoteSongs)
	}

	@Transaction
	suspend fun deleteObsoleteSongs(remoteIds: Set<String>) {
		getAllSongIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("SongDao", "song $localId no longer exists remotely")
				deleteSong(localId)
			}
		}
	}
}
