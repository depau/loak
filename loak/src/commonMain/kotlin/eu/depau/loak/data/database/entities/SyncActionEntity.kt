package eu.depau.loak.data.database.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Instant

enum class SyncActionType {
	STAR, UNSTAR, DELETE_PLAYLIST, SCROBBLE,

	/**
	 * With a [PlaylistEdit] payload: itemId is the playlist id. Without one (queued by older
	 * versions): itemId is "<playlistId>:<song index>".
	 */
	REMOVE_FROM_PLAYLIST,

	// this is dumb but it works so whatever
	STAR_0, STAR_1, STAR_2, STAR_3, STAR_4, STAR_5,

	/** With a [PlaylistEdit] payload; itemId is the playlist id (`local:…` until created). */
	ADD_TO_PLAYLIST, CREATE_PLAYLIST, UPDATE_PLAYLIST
}

@Entity
data class SyncActionEntity(
	@PrimaryKey(autoGenerate = true) val id: Int = 0,
	val actionType: SyncActionType,
	val itemId: String,
	@ColumnInfo(defaultValue = "CURRENT_TIMESTAMP")
	val time: Instant = Clock.System.now(),
	/** JSON [PlaylistEdit] for playlist edits. */
	val payload: String? = null
)

/** A playlist edit made locally, replayed on the server by song id (see SyncManager). */
@Serializable
data class PlaylistEdit(
	/** CREATE/ADD: the songs to append. REMOVE: the one song removed. */
	val songIds: List<String> = emptyList(),
	/** REMOVE: which of that song's occurrences in the playlist (0 = first), as seen locally. */
	val occurrence: Int = 0,
	/** CREATE/UPDATE */
	val name: String? = null,
	/** UPDATE */
	val comment: String? = null,
	val public: Boolean? = null
)

/** Prefix of the ids of playlists created offline, until the server gives them a real one. */
const val LOCAL_PLAYLIST_PREFIX = "local:"
