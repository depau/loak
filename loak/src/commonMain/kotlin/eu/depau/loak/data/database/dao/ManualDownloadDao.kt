package eu.depau.loak.data.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import eu.depau.loak.data.database.entities.ManualDownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualDownloadDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(entry: ManualDownloadEntity)

	@Query("SELECT songId FROM ManualDownloadEntity")
	fun observeIds(): Flow<List<String>>

	@Query("SELECT songId FROM ManualDownloadEntity")
	suspend fun getAllIds(): List<String>

	@Query("DELETE FROM ManualDownloadEntity WHERE songId = :songId")
	suspend fun delete(songId: String)

	@Query("DELETE FROM ManualDownloadEntity")
	suspend fun clearAll()
}
