package eu.depau.loak.data.database.entities

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

/** What kind of thing was downloaded. */
enum class DownloadCollectionType { ALBUM, ARTIST, PLAYLIST }

/**
 * An explicit "download" of an album, artist or playlist: the collection id. Downloaded
 * collections stay fresh on every library sync (see [DownloadManager.onLibrarySynced]).
 * Data lives per server-owned id, and ids only collide within one server; a server switch
 * clears the table.
 */
@Serializable
@Entity
data class DownloadCollectionEntity(
	@PrimaryKey val collectionId: String,
	val type: DownloadCollectionType,
	val createdAt: Long = 0
)
