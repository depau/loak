package eu.depau.loak.data.database.relations

import androidx.room3.Embedded
import androidx.room3.Relation
import eu.depau.loak.data.database.entities.PlaylistEntity
import eu.depau.loak.data.database.entities.PlaylistSongCrossRef
import eu.depau.loak.data.database.entities.SongEntity

data class PlaylistWithSongs(
	@Embedded val playlist: PlaylistEntity,
	@Relation(
		entity = PlaylistSongCrossRef::class,
		parentColumns = ["playlistId"],
		entityColumns = ["playlistId"]
	)
	val songs: List<PlaylistSong>
)

data class PlaylistSong(
	@Embedded val crossRef: PlaylistSongCrossRef,
	// The song is fetched as a collection on purpose, and it must stay one. Room fetches a
	// relation of more than 999 keys through `recursiveFetchMap`, whose single-valued branch does
	// `map.putAll(batch)` while iterating `map.keys`; on Kotlin/Native that `putAll` sometimes
	// rebuilds the backing table and invalidates the iterator, so the query dies with
	// `ConcurrentModificationException` — for some key counts only, which makes it look like a data
	// problem instead of a code one. A collection relation never puts the batch back into the
	// parent map, so it cannot trip this. Read the song with `.songs.first()`: a `songId` is a
	// primary key, so there is exactly one. See PlaylistRelationBatchingTest and gotcha 13 in
	// AGENTS.md before turning this into a single-valued @Relation over `SongEntity` again.
	@Relation(
		parentColumns = ["songId"],
		entityColumns = ["songId"]
	)
	val songs: List<SongEntity>
)
