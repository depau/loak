package eu.depau.loak.data.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlin.time.Duration
import kotlin.time.Instant

enum class TransferCategory {
	STREAM, DOWNLOAD, API, IMAGE,

	/** Audio served from the local store instead of the network (not recorded yet). */
	CACHE_HIT
}

/** Bytes received in one hour, per network kind and category. */
@Entity(primaryKeys = ["hour", "metered", "category"])
data class NetworkStatsEntity(
	/** Epoch millis of the start of the hour. */
	val hour: Long,
	val metered: Boolean,
	val category: TransferCategory,
	val bytes: Long,
	val requests: Long
)

/** One full library pull. */
@Entity
data class SyncRunEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val start: Instant,
	val duration: Duration,
	val ok: Boolean,
	val items: Int
)
