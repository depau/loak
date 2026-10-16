package eu.depau.loak.domain.discover

import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.entities.AlbumEntity
import eu.depau.loak.data.database.entities.ArtistEntity
import eu.depau.loak.data.database.entities.SongEntity
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainSong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ponytail: name matching misses heavily retitled releases; add MBID release-group matching if users report false negatives.

/**
 * Drops everything from the first feat./ft. credit on, so version-dropping and name matching
 * never see "feat. Somebody" as part of the title.
 */
private val FEAT_CREDIT = Regex("""\s+f(?:ea)?t\..*""", RegexOption.IGNORE_CASE)

/** Strips "(...)", "[...]" groups that are usually edition markers ("(Remastered 2011)", "[Live]"). */
private val BRACKETED = Regex("""[(\[][^)\]]*[)\]]""")

private val NON_WORD = Regex("""[^a-z0-9]+""")
private val WHITESPACE = Regex("""\s+""")

/** Accented → plain, lower-cased; both cases included because we fold case *after* mapping. */
private val ACCENTS: Map<Char, String> = buildMap {
	// single-letter replacements (verified 1:1)
	fun putAll(src: String, dst: String) = src.zip(dst).forEach { (c, d) ->
		put(c, d.toString())
		put(c.uppercaseChar(), d.toString())
	}
	putAll("àáâãäåèéêëìíîïñòóôõöøùúûüýÿçśźżćńąłęšžčďňřťůđ", "aaaaaaeeeeiiiinoooooouuuuyycszzcnlaeszdnrtud")
	// multi-character digraphs
	put('æ', "ae"); put('Æ', "ae")
	put('œ', "oe"); put('Œ', "oe")
	put('ð', "d"); put('Ð', "d")
	put('þ', "th"); put('Þ', "th")
	put('ß', "ss")
}

private fun String.normalized(dropFeat: Boolean, dropBracketed: Boolean): String {
	var s = trim().lowercase()
	if (dropFeat) s = s.replaceFirst(FEAT_CREDIT, "")
	if (dropBracketed) s = s.replace(BRACKETED, " ")
	val sb = StringBuilder(s.length)
	for (c in s) sb.append(ACCENTS[c] ?: c)
	return sb.toString().replace(NON_WORD, " ").replace(WHITESPACE, " ").trim()
}

/**
 * A title as it compares against the library: lowercase, accent/punctuation free, with
 * feat. credits and edition markers ("(Remastered 2011)") stripped. "Daft Punk - Get Lucky
 * (feat. Pharrell) (2011 Remaster)" → "daft punk get lucky".
 */
fun normalizeTitle(title: String): String = title.normalized(dropFeat = true, dropBracketed = true)

/** An artist name as it compares: lowercase, accents and punctuation gone, no version markers. */
fun normalizeArtist(artist: String): String = artist.normalized(dropFeat = false, dropBracketed = false)

/** The whole library, folded into lookup indexes once per session. */
private class LibraryIndex(
	val songByIsrc: Map<String, SongEntity>,
	val songByMbid: Map<String, SongEntity>,
	val songByName: Map<String, List<SongEntity>>,
	val albumByMbid: Map<String, AlbumEntity>,
	val albumByName: Map<String, List<AlbumEntity>>,
	val artistByMbid: Map<String, ArtistEntity>,
	val artistByName: Map<String, List<ArtistEntity>>
)

/**
 * Whether a Discover track/album/artist already lives in the Subsonic library (the Room cache),
 * matching first by exact identifiers (ISRC, MusicBrainz ID) and then by normalised name.
 * The whole library is loaded and indexed once per library sync, so repeated lookups are O(1).
 */
class LibraryMatcher(
	private val songDao: SongDao,
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao
) {
	private val gate = Mutex()
	private var index: LibraryIndex? = null

	/** Bumped by [invalidate], so statuses on screen re-resolve against the fresh library. */
	val generation: StateFlow<Int>
		field = MutableStateFlow(0)

	/** Drops the index after a library sync; the next lookup rebuilds it. */
	fun invalidate() {
		index = null
		generation.update { it + 1 }
	}

	private suspend fun ensureIndex(): LibraryIndex {
		index?.let { return it }
		gate.withLock {
			index?.let { return it }
			val songs = songDao.getAllSongs()
			val albums = albumDao.getAllAlbumEntities()
			val artists = artistDao.getAllArtistsList()
			index = LibraryIndex(
				songByIsrc = songs.flatMap { s -> s.isrc.map { it to s } }.toMap(),
				songByMbid = songs.mapNotNull { s -> s.musicBrainzId?.let { it to s } }.toMap(),
				songByName = songs.groupBy { normalizeTitle(it.title) },
				albumByMbid = albums.mapNotNull { a -> a.musicBrainzId?.let { it to a } }.toMap(),
				albumByName = albums.groupBy { normalizeTitle(it.name.orEmpty()) },
				artistByMbid = artists.mapNotNull { a -> a.musicBrainzId?.let { it to a } }.toMap(),
				artistByName = artists.groupBy { normalizeArtist(it.name) }
			)
			return index!!
		}
	}

	private fun List<SongEntity>.byName(discoverTitle: String, discoverArtist: String): SongEntity? {
		val artist = normalizeArtist(discoverArtist)
		return firstOrNull { normalizeArtist(it.artistName.orEmpty()) == artist }
	}

	private fun List<AlbumEntity>.byName(title: String, artist: String): AlbumEntity? {
		val artistName = normalizeArtist(artist)
		return firstOrNull { normalizeArtist(it.artistName.orEmpty()) == artistName }
	}

	/** The library song matching [song], if any — mapped so it can be played. */
	suspend fun songInLibrary(song: DiscoverSong): DomainSong? {
		val idx = ensureIndex()
		val byIsrc = song.isrc?.let { idx.songByIsrc[it] }
		val byMbid = song.mbid?.let { idx.songByMbid[it] }
		val hit = byIsrc ?: byMbid ?: idx.songByName[normalizeTitle(song.title)]?.byName(song.title, song.artist)
		return hit?.toDomainModel()
	}

	/**
	 * The id of the library song this title and artist are, if any — for the listening
	 * history sources, which describe a track with a name and sometimes an identifier
	 * rather than as a [DiscoverSong]. Same indexes, same normalisation, same order:
	 * ISRC, then MusicBrainz id, then the name index with the artist checked.
	 */
	suspend fun songIdOf(
		title: String,
		artist: String,
		mbid: String? = null,
		isrc: String? = null
	): String? {
		val idx = ensureIndex()
		val byIsrc = isrc?.let { idx.songByIsrc[it] }
		val byMbid = mbid?.let { idx.songByMbid[it] }
		val hit = byIsrc ?: byMbid ?: idx.songByName[normalizeTitle(title)]?.byName(title, artist)
		return hit?.songId
	}

	/** The library album matching [album], if any — mapped (relation loaded) so it can be played. */
	suspend fun albumInLibrary(album: DiscoverAlbum): DomainAlbum? {
		val idx = ensureIndex()
		val byMbid = album.mbid?.let { idx.albumByMbid[it] }
		val hit = byMbid ?: idx.albumByName[normalizeTitle(album.title)]?.byName(album.title, album.artist)
		return hit?.let { albumDao.getAlbumById(it.albumId)?.toDomainModel() }
	}

	/** The library artist matching [artist], if any — mapped so it can be played. */
	suspend fun artistInLibrary(artist: DiscoverArtist): DomainArtist? {
		val idx = ensureIndex()
		val byMbid = artist.mbid?.let { idx.artistByMbid[it] }
		val hit = byMbid ?: idx.artistByName[normalizeArtist(artist.name)]?.firstOrNull()
		return hit?.toDomainModel()
	}
}
