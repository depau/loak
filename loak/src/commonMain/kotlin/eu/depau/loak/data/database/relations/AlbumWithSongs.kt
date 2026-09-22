package eu.depau.loak.data.database.relations

import androidx.room3.Embedded
import androidx.room3.Relation
import eu.depau.loak.data.database.entities.AlbumEntity
import eu.depau.loak.data.database.entities.SongEntity

data class AlbumWithSongs(
	@Embedded val album: AlbumEntity,
	@Relation(
		parentColumns = ["albumId"],
		entityColumns = ["belongsToAlbumId"]
	)
	val songs: List<SongEntity>
)
