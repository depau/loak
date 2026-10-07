package eu.depau.loak.data.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import eu.depau.loak.data.database.entities.DownloadCollectionEntity
import eu.depau.loak.data.database.entities.DownloadCollectionType
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadCollectionDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsert(entry: DownloadCollectionEntity)

	@Query("SELECT * FROM DownloadCollectionEntity")
	fun observeAll(): Flow<List<DownloadCollectionEntity>>

	@Query("SELECT * FROM DownloadCollectionEntity")
	suspend fun getAll(): List<DownloadCollectionEntity>

	@Query("SELECT * FROM DownloadCollectionEntity WHERE collectionId = :collectionId LIMIT 1")
	suspend fun getById(collectionId: String): DownloadCollectionEntity?

	/** How many songs each pinned collection has been seen with; playlist totals update on refresh. */
	@Query("DELETE FROM DownloadCollectionEntity WHERE collectionId = :collectionId")
	suspend fun delete(collectionId: String)

	@Query("DELETE FROM DownloadCollectionEntity")
	suspend fun clearAll()
}
