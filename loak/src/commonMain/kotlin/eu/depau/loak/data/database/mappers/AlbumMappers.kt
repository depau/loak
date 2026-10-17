package eu.depau.loak.data.database.mappers

import eu.depau.loak.data.database.entities.AlbumEntity
import eu.depau.loak.data.database.relations.AlbumWithSongs
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainAlbumInfo
import eu.depau.loak.domain.models.DomainSong
import dev.zt64.subsonic.api.model.Album as ApiAlbum
import dev.zt64.subsonic.api.model.AlbumInfo as ApiAlbumInfo

fun ApiAlbum.toEntity(
	artistIdOverride: String? = null,
	artistNameOverride: String? = null
) = AlbumEntity(
	albumId = this.id,
	name = this.name,
	// TODO: figure out why this can be null and how to handle it
	artistId = artistIdOverride ?: this.artistId ?: "unknown artist",
	artistName = artistNameOverride ?: this.artistName,
	coverArtId = this.coverArtId,
	songCount = this.songCount,
	duration = this.duration,
	year = this.year,
	genre = this.genre,
	starredAt = this.starredAt,
	userRating = this.userRating,
	musicBrainzId = this.musicBrainzId,
	createdAt = this.createdAt,
	lastPlayedAt = this.lastPlayedAt,
	playCount = this.playCount,
	genres = this.genres,
	version = this.version,
	isExternal = this.isExternal
)

/**
 * The album's own fields. [songs] are left empty when the album is read without them, which is
 * what the album shelves do: their list of albums is for choosing what to show, not for reading
 * every song of every album (`HomeRepository.withSongs` fills in the ones on screen).
 */
fun AlbumEntity.toDomainModel(songs: List<DomainSong> = emptyList()) = DomainAlbum(
	id = albumId,
	name = name,
	artistId = artistId,
	artistName = artistName,
	coverArtId = coverArtId,
	songCount = songCount,
	duration = duration,
	year = year,
	genre = genre,
	starredAt = starredAt,
	userRating = userRating,
	musicBrainzId = musicBrainzId,
	createdAt = createdAt,
	lastPlayedAt = lastPlayedAt,
	playCount = playCount,
	genres = genres,
	version = version,
	songs = songs,
	isExternal = isExternal
)

fun AlbumWithSongs.toDomainModel() = album.toDomainModel(
	songs.map { it.toDomainModel() }.sortedBy { it.trackNumber }
)

fun DomainAlbum.toEntity() = AlbumEntity(
	albumId = this.id,
	name = this.name,
	artistId = this.artistId,
	artistName = this.artistName,
	coverArtId = this.coverArtId,
	songCount = this.songCount,
	duration = this.duration,
	year = this.year,
	genre = this.genre,
	starredAt = this.starredAt,
	userRating = this.userRating,
	musicBrainzId = this.musicBrainzId,
	createdAt = this.createdAt,
	lastPlayedAt = this.lastPlayedAt,
	playCount = this.playCount,
	genres = this.genres,
	version = this.version,
	isExternal = this.isExternal
)

fun ApiAlbumInfo.toDomainModel() = DomainAlbumInfo(
	musicBrainzId = musicBrainzId,
	largeImageUrl = largeImageUrl,
	mediumImageUrl = mediumImageUrl,
	smallImageUrl = smallImageUrl,
	lastFmUrl = lastFmUrl,
	notes = notes
)
