package eu.depau.loak.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlin.time.Instant
import eu.depau.loak.data.database.entities.SyncActionEntity

@Dao
interface SyncActionDao {
	@Insert
	suspend fun enqueue(action: SyncActionEntity): Long

	@Query("UPDATE SyncActionEntity SET time = :time WHERE id = :id")
	suspend fun setTime(id: Int, time: Instant)

	@Transaction
	@Query("SELECT * FROM SyncActionEntity ORDER BY id ASC")
	suspend fun getPendingActions(): List<SyncActionEntity>

	@Query("DELETE FROM SyncActionEntity WHERE id = :id")
	suspend fun removeAction(id: Int)

	@Query("DELETE FROM SyncActionEntity")
	suspend fun clearAllActions()

	/** Playlists with edits or a delete not on the server yet: syncs must leave them alone. */
	@Query(
		"SELECT DISTINCT itemId FROM SyncActionEntity " +
			"WHERE payload IS NOT NULL OR actionType = 'DELETE_PLAYLIST'"
	)
	suspend fun pendingPlaylistIds(): List<String>

	/** A newer name/description/visibility edit replaces the queued one. */
	@Query(
		"DELETE FROM SyncActionEntity WHERE actionType = 'UPDATE_PLAYLIST' AND itemId = :playlistId"
	)
	suspend fun removePlaylistUpdates(playlistId: String)

	/** A playlist created offline got its real id. */
	@Query("UPDATE SyncActionEntity SET itemId = :newId WHERE itemId = :oldId")
	suspend fun renamePlaylist(oldId: String, newId: String)
}
