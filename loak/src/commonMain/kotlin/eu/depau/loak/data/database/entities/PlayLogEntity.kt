package eu.depau.loak.data.database.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * One play of one song, as this device counted it — once it counted as listened to, not the
 * moment playback started. Kept for months rather than days: Quick picks decay these into
 * heat, and the recap counts them.
 */
@Entity(indices = [Index("playedAt"), Index(value = ["songId", "playedAt"])])
data class PlayLogEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val songId: String,
	/** Epoch seconds, so a play's age can be measured without a date type. */
	val playedAt: Long
)

/** How many times one song was played since a cutoff. */
data class SongPlayCount(val songId: String, val count: Int)
