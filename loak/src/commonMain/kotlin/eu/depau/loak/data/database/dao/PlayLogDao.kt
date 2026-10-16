package eu.depau.loak.data.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import eu.depau.loak.data.database.entities.PlayLogEntity
import eu.depau.loak.data.database.entities.SongPlayCount

/** This device's plays, one row each. Written on every counted play, read a few hundred at a time. */
@Dao
interface PlayLogDao {
	@Insert
	suspend fun insert(play: PlayLogEntity)

	/** Every play since [since], oldest first. */
	@Query("SELECT * FROM PlayLogEntity WHERE playedAt >= :since ORDER BY playedAt")
	suspend fun playsSince(since: Long): List<PlayLogEntity>

	/** How many times each song was played since [since]. */
	@Query(
		"SELECT songId, COUNT(*) AS count FROM PlayLogEntity WHERE playedAt >= :since GROUP BY songId"
	)
	suspend fun playCountsSince(since: Long): List<SongPlayCount>

	@Query("SELECT * FROM PlayLogEntity ORDER BY playedAt DESC LIMIT 1")
	suspend fun lastPlay(): PlayLogEntity?

	@Query("DELETE FROM PlayLogEntity WHERE playedAt < :before")
	suspend fun prune(before: Long)
}
