package eu.depau.loak.domain.models

/**
 * Filters Home and the listening history share. They live here rather than in one repository
 * because the Quick picks ranking is a pure function over songs and needs the same idea of
 * "disliked" and "in this genre" the rest of the app uses.
 */

/** 1 or 2 stars: the user said not this one. */
fun DomainSong.isDisliked() = (userRating ?: 0) in 1..2

/** Every genre tag the song carries, its plain `genre` field included. */
fun DomainSong.genreNames() = (genres + listOfNotNull(genre)).distinct()

/** Whether the song belongs to [genre]; a null [genre] matches everything. */
fun DomainSong.inGenre(genre: String?) =
	genre == null || genreNames().any { it.equals(genre, ignoreCase = true) }

/** Whether the album belongs to [genre]; a null [genre] matches everything. */
fun DomainAlbum.inGenre(genre: String?) = genre == null ||
	(genres + listOfNotNull(this.genre)).any { it.equals(genre, ignoreCase = true) }
