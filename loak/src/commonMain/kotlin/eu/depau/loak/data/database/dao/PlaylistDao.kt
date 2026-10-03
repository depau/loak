package eu.depau.loak.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow
import eu.depau.loak.data.database.entities.PlaylistEntity
import eu.depau.loak.data.database.entities.PlaylistSongCrossRef
import eu.depau.loak.data.database.relations.PlaylistWithSongs
import eu.depau.loak.util.Logger

@Dao
interface PlaylistDao {
	@Insert(onConflict = OnConflictStrategy.IGNORE)
	suspend fun insertPlaylistsIgnoringConflicts(playlists: List<PlaylistEntity>)

	@Update
	suspend fun updatePlaylists(playlists: List<PlaylistEntity>)

	suspend fun insertPlaylist(playlist: PlaylistEntity) = insertPlaylists(listOf(playlist))

	/**
	 * Insert or update in place. Not REPLACE: that deletes the row first, and the delete
	 * cascades to the playlist's song links, emptying the playlist until its songs are
	 * fetched again. (Room's @Upsert degrades to a bare INSERT on web.)
	 */
	@Transaction
	suspend fun insertPlaylists(playlists: List<PlaylistEntity>) {
		insertPlaylistsIgnoringConflicts(playlists)
		updatePlaylists(playlists)
	}

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun insertPlaylistSongCrossRefs(crossRefs: List<PlaylistSongCrossRef>)

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY name COLLATE NOCASE ASC")
	suspend fun getAllPlaylistsByName(): List<PlaylistWithSongs>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY createdAt ASC")
	suspend fun getAllPlaylistsByDateAdded(): List<PlaylistWithSongs>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY duration ASC")
	suspend fun getAllPlaylistsByDuration(): List<PlaylistWithSongs>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY RANDOM()")
	suspend fun getAllPlaylistsRandom(): List<PlaylistWithSongs>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity ORDER BY name COLLATE NOCASE ASC")
	fun getAllPlaylistsFlow(): Flow<List<PlaylistWithSongs>>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity WHERE playlistId = :playlistId LIMIT 1")
	suspend fun getPlaylistById(playlistId: String): PlaylistWithSongs?

	@Query("DELETE FROM PlaylistEntity WHERE playlistId = :playlistId")
	suspend fun deletePlaylist(playlistId: String)

	@Query("DELETE FROM PlaylistSongCrossRef WHERE playlistId = :playlistId")
	suspend fun deletePlaylistSongCrossRefs(playlistId: String)

	@Transaction
	suspend fun replacePlaylistSongs(playlistId: String, crossRefs: List<PlaylistSongCrossRef>) {
		deletePlaylistSongCrossRefs(playlistId)
		insertPlaylistSongCrossRefs(crossRefs)
	}

	@Query("SELECT COUNT(*) FROM PlaylistEntity")
	suspend fun getPlaylistCount(): Int

	@Query("DELETE FROM PlaylistEntity")
	suspend fun clearAllPlaylists()

	@Query("SELECT playlistId FROM PlaylistEntity")
	suspend fun getAllPlaylistIds(): List<String>

	@Transaction
	@Query("SELECT * FROM PlaylistEntity WHERE name LIKE '%' || :query || '%' COLLATE NOCASE")
	suspend fun searchPlaylistsList(query: String): List<PlaylistWithSongs>

	@Transaction
	suspend fun updateAllPlaylists(remotePlaylists: List<PlaylistEntity>) {
		val remoteIds = remotePlaylists.map { it.playlistId }.toSet()
		getAllPlaylistIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("PlaylistDao", "playlist $localId no longer exists remotely")
				deletePlaylist(localId)
			}
		}
		insertPlaylists(remotePlaylists)
	}

	@Transaction
	suspend fun deleteObsoletePlaylists(remoteIds: Set<String>) {
		getAllPlaylistIds().forEach { localId ->
			if (localId !in remoteIds) {
				Logger.w("PlaylistDao", "playlist $localId no longer exists remotely")
				deletePlaylist(localId)
			}
		}
	}
}
