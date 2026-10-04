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

	@Query("SELECT * FROM AudioFileEntity WHERE songId = :songId")
	suspend fun getForSong(songId: String): List<AudioFileEntity>

	@Query("SELECT * FROM AudioFileEntity WHERE songId = :songId AND quality = :quality")
	suspend fun get(songId: String, quality: String): AudioFileEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(entry: AudioFileEntity)

	@Delete
	suspend fun delete(entries: List<AudioFileEntity>)

	@Query(
		"UPDATE AudioFileEntity SET lastAccessed = :time " +
			"WHERE songId = :songId AND quality = :quality"
	)
	suspend fun touch(songId: String, quality: String, time: Long)

	@Query(
		"UPDATE AudioFileEntity SET pinned = :pinned " +
			"WHERE songId = :songId AND quality = :quality"
	)
	suspend fun setPinned(songId: String, quality: String, pinned: Boolean)

	@Query("UPDATE AudioFileEntity SET pinned = 0 WHERE songId = :songId")
	suspend fun unpin(songId: String)
}
