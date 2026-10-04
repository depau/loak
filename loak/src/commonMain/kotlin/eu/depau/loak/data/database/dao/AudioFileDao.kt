package eu.depau.loak.data.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import eu.depau.loak.data.database.entities.AudioFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioFileDao {
	@Query("SELECT * FROM AudioFileEntity")
	suspend fun getAll(): List<AudioFileEntity>

	@Query("SELECT * FROM AudioFileEntity")
	fun observeAll(): Flow<List<AudioFileEntity>>

	@Query("SELECT * FROM AudioFileEntity WHERE server = :server AND songId = :songId")
	suspend fun getForSong(server: String, songId: String): List<AudioFileEntity>

	@Query("SELECT * FROM AudioFileEntity WHERE server = :server AND pinned = 1")
	suspend fun getPinned(server: String): List<AudioFileEntity>

	@Query(
		"SELECT * FROM AudioFileEntity " +
			"WHERE server = :server AND songId = :songId AND quality = :quality"
	)
	suspend fun get(server: String, songId: String, quality: String): AudioFileEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(entry: AudioFileEntity)

	@Delete
	suspend fun delete(entries: List<AudioFileEntity>)

	@Query(
		"UPDATE AudioFileEntity SET lastAccessed = :time " +
			"WHERE server = :server AND songId = :songId AND quality = :quality"
	)
	suspend fun touch(server: String, songId: String, quality: String, time: Long)

	@Query(
		"UPDATE AudioFileEntity SET pinned = :pinned " +
			"WHERE server = :server AND songId = :songId AND quality = :quality"
	)
	suspend fun setPinned(server: String, songId: String, quality: String, pinned: Boolean)

	@Query("UPDATE AudioFileEntity SET pinned = 0 WHERE server = :server AND songId = :songId")
	suspend fun unpin(server: String, songId: String)

	/** Assigns files stored before the store told servers apart to [server]. */
	@Query("UPDATE AudioFileEntity SET server = :server WHERE server = ''")
	suspend fun claimUnscoped(server: String)
}
