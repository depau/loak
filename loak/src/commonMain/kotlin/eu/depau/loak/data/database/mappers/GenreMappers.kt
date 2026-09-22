package eu.depau.loak.data.database.mappers

import eu.depau.loak.data.database.entities.GenreEntity
import eu.depau.loak.data.database.relations.GenreWithAlbums
import eu.depau.loak.domain.models.DomainGenre
import dev.zt64.subsonic.api.model.Genre as ApiGenre

fun ApiGenre.toEntity() = GenreEntity(
	genreName = name,
	albumCount = albumCount,
	songCount = songCount
)

fun GenreWithAlbums.toDomainModel() = DomainGenre(
	name = genre.genreName,
	albumCount = genre.albumCount,
	songCount = genre.songCount,
	albums = albums.map { it.toDomainModel() }
)

fun DomainGenre.toEntity() = GenreEntity(
	genreName = name,
	albumCount = albumCount,
	songCount = songCount
)
