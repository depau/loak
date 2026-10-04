package eu.depau.loak.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import eu.depau.loak.data.database.entities.NetworkStatsEntity
import eu.depau.loak.data.database.entities.SyncRunEntity
import eu.depau.loak.data.database.entities.TransferCategory
import kotlin.time.Instant

@Dao
interface NetworkStatsDao {
	@Query(
		"INSERT INTO NetworkStatsEntity VALUES (:hour, :metered, :category, :bytes, :requests) " +
			"ON CONFLICT(hour, metered, category) DO UPDATE SET " +
			"bytes = bytes + excluded.bytes, requests = requests + excluded.requests"
	)
	suspend fun add(
		hour: Long,
		metered: Boolean,
		category: TransferCategory,
		bytes: Long,
		requests: Long
	)

	@Query("SELECT * FROM NetworkStatsEntity WHERE hour >= :since ORDER BY hour DESC")
	suspend fun getSince(since: Long): List<NetworkStatsEntity>

	@Query("DELETE FROM NetworkStatsEntity WHERE hour < :before")
	suspend fun pruneTransfers(before: Long)

	@Insert
	suspend fun insertSyncRun(run: SyncRunEntity)

	@Query("SELECT * FROM SyncRunEntity ORDER BY start DESC LIMIT :limit")
	suspend fun getSyncRuns(limit: Int): List<SyncRunEntity>

	@Query("DELETE FROM SyncRunEntity WHERE start < :before")
	suspend fun pruneSyncRuns(before: Instant)
}
