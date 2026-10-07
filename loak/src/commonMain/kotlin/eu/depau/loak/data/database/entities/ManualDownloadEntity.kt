package eu.depau.loak.data.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Songs pinned by the user *not* through a whole-collection download (single "download" on a
 * song, or the whole library): orphan-unpinning must never undo these.
 */
@Serializable
@Entity
data class ManualDownloadEntity(
	@PrimaryKey val songId: String
)
