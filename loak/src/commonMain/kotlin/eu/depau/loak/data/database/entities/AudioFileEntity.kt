package eu.depau.loak.data.database.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity

/** A song's audio at one quality in the AudioStore. Pinned = downloaded, never evicted. */
@Entity(primaryKeys = ["server", "songId", "quality"])
data class AudioFileEntity(
	val songId: String,
	/** [eu.depau.loak.domain.models.AudioQuality.key]. */
	val quality: String,
	/** Inside the store directory; `<fileName>.partial` until complete. */
	val fileName: String,
	/** Size on disk once complete; for a partial file, as of when it was opened. */
	val bytes: Long,
	/** Full length when the server sent one (transcodes often don't). */
	val expectedBytes: Long?,
	val complete: Boolean,
	val pinned: Boolean,
	/** Epoch millis, for LRU eviction. */
	val lastAccessed: Long,
	val created: Long,
	/** Which server and user [songId] belongs to: ids are only unique per server. */
	@ColumnInfo(defaultValue = "")
	val server: String = ""
)
