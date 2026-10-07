package eu.depau.loak.data.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

/** What kind of thing was downloaded. */
enum class DownloadCollectionType { ALBUM, ARTIST, PLAYLIST }

/**
 * An explicit "download" of an album, artist or playlist: the collection id and its optional
 * auto-refresh schedule. Rows are per collection; a playlist re-syncs its current songs when
 * [scheduleEnabled] and [CronSchedule] says so. Data lives per server-owned id, and ids only
 * collide within one server; a server switch clears the table.
 */
@Serializable
@Entity
data class DownloadCollectionEntity(
	@PrimaryKey val collectionId: String,
	val type: DownloadCollectionType,
	val scheduleCron: String? = null,
	val scheduleEnabled: Boolean = false,
	val createdAt: Long = 0,
	/** Epoch millis of the last scheduled refresh run; anchors the next-next run. */
	val lastRunAt: Long = 0
)
