package eu.depau.loak.data.database.relations

import androidx.room3.Embedded
import androidx.room3.Relation
import eu.depau.loak.data.database.entities.AlbumEntity
import eu.depau.loak.data.database.entities.GenreEntity

/** A genre and its albums, without their songs: genre lists show covers, not tracks. */
data class GenreWithAlbums(
	@Embedded val genre: GenreEntity,
	@Relation(
		parentColumns = ["genreName"],
		entityColumns = ["genre"]
	)
	val albums: List<AlbumEntity>
)
