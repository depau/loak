package eu.depau.loak.shared

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.DownloadCollectionDao
import eu.depau.loak.data.database.dao.GenreDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.RadioDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.di.COVER_ART_MEDIUM
import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.HomeRepository
import eu.depau.loak.domain.repositories.SearchRepository
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_play_all
import eu.depau.loak.generated.resources.action_shuffle
import eu.depau.loak.generated.resources.lens_recently_added
import eu.depau.loak.generated.resources.title_albums
import eu.depau.loak.generated.resources.title_artists
import eu.depau.loak.generated.resources.title_downloads
import eu.depau.loak.generated.resources.title_forgotten_favourites
import eu.depau.loak.generated.resources.title_genres
import eu.depau.loak.generated.resources.title_home
import eu.depau.loak.generated.resources.title_library
import eu.depau.loak.generated.resources.title_made_for_you
import eu.depau.loak.generated.resources.title_never_played
import eu.depau.loak.generated.resources.title_playlists
import eu.depau.loak.generated.resources.title_quick_picks
import eu.depau.loak.generated.resources.title_radios
import eu.depau.loak.generated.resources.title_songs
import eu.depau.loak.generated.resources.title_starred
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * The tree media browsers (Android Auto, Android Automotive) show, and what their picks play.
 * Ids are opaque to the host: folders are fixed names or `kind|id`; a song is
 * `song|<songId>|<folder>`, so picking it plays the folder from there.
 */
@UnstableApi
internal class PlaybackLibrary : KoinComponent {
	private val sessionManager: SessionManager by inject()
	private val songDao: SongDao by inject()
	private val albumDao: AlbumDao by inject()
	private val artistDao: ArtistDao by inject()
	private val playlistDao: PlaylistDao by inject()
	private val genreDao: GenreDao by inject()
	private val radioDao: RadioDao by inject()
	private val downloadCollectionDao: DownloadCollectionDao by inject()
	private val homeRepository: HomeRepository by inject()
	private val searchRepository: SearchRepository by inject()
	private val audioStore: AudioStore by inject()

	fun root(): MediaItem = folder(ROOT, "")

	// ponytail: whole lists from the DB per request, sliced into pages; DB paging if a big
	// library proves slow
	suspend fun children(id: String): List<MediaItem> = when (id) {
		ROOT -> listOf(
			folder(HOME, Res.string.title_home),
			folder(STARRED, Res.string.title_starred),
			folder(LIBRARY, Res.string.title_library),
			folder(DOWNLOADS, Res.string.title_downloads)
		)
		HOME -> home()
		STARRED -> listOf(
			folder(STARRED_SONGS, Res.string.title_songs),
			folder(STARRED_ALBUMS, Res.string.title_albums, grid = true),
			folder(STARRED_ARTISTS, Res.string.title_artists)
		)
		LIBRARY -> listOf(
			folder(PLAYLISTS, Res.string.title_playlists, grid = true),
			folder(ARTISTS, Res.string.title_artists),
			folder(ALBUMS, Res.string.title_albums, grid = true),
			folder(GENRES, Res.string.title_genres),
			folder(RADIOS, Res.string.title_radios)
		)
		DOWNLOADS -> listOf(folder(DOWNLOADED_SONGS, Res.string.title_songs)) +
			downloadCollectionDao.getAll().mapNotNull { collectionItem(it.collectionId) }
		PLAYLISTS -> playlistDao.getAllPlaylistsByName().map { playlistItem(it.toDomainModel()) }
		ARTISTS -> artistDao.getArtistsAlphabeticalByName().map { artistItem(it.toDomainModel()) }
		STARRED_ARTISTS -> artistDao.getArtistsStarred().map { artistItem(it.toDomainModel()) }
		ALBUMS -> albumDao.getAllAlbumsList().map { albumItem(it.toDomainModel()) }
		STARRED_ALBUMS -> albumDao.getAllAlbumsList().map { it.toDomainModel() }
			.filter { it.starredAt != null }.sortedByDescending { it.starredAt }.map(::albumItem)
		GENRES -> genreDao.getGenres().map { folder("$GENRE|${it.genreName}", it.genreName, grid = true) }
		RADIOS -> radioDao.getRadios().map {
			item("$RADIO|${it.radioId}", it.name, playable = true, type = MediaMetadata.MEDIA_TYPE_RADIO_STATION)
		}
		else -> when (id.substringBefore('|')) {
			// a whole discography or genre is too long a list: its albums, and actions for all
			ARTIST, GENRE -> actions(id) + albums(id).map(::albumItem)
			else -> songs(id).let { songs ->
				val downloaded = audioStore.downloadedIds()
				if (songs.isEmpty()) emptyList()
				else actions(id) + songs.map { songItem(it, id, it.id in downloaded) }
			}
		}
	}

	private suspend fun home(): List<MediaItem> {
		val library = homeRepository.library()
		fun List<DomainAlbum>.group(title: String) = map { albumItem(it, group = title) }
		return listOf(folder(QUICK_PICKS, Res.string.title_quick_picks)) +
			homeRepository.madeForYou().map {
				playlistItem(it, group = getString(Res.string.title_made_for_you))
			} +
			homeRepository.recentlyAdded(library, null).group(getString(Res.string.lens_recently_added)) +
			homeRepository.forgottenFavourites(library, null)
				.group(getString(Res.string.title_forgotten_favourites)) +
			homeRepository.neverPlayed(library).group(getString(Res.string.title_never_played))
	}

	/** Browse search: the albums, artists, songs and playlists matching [query]. */
	suspend fun search(query: String): List<MediaItem> = searchRepository.search(query).mapNotNull {
		when (it) {
			is DomainSong -> songItem(it, SINGLE, downloaded = false)
			is DomainAlbum -> albumItem(it)
			is DomainArtist -> artistItem(it)
			is DomainPlaylist -> playlistItem(it)
			else -> null
		}
	}

	/**
	 * What picking [item] plays, and where it starts: a song plays with its folder, an action
	 * plays its folder, a voice request plays the best match. Null when nothing matches.
	 */
	suspend fun play(item: MediaItem): Pair<List<MediaItem>, Int>? {
		item.requestMetadata.searchQuery?.let { return playSearch(it) }
		val id = item.mediaId
		val (songs, start) = when (id.substringBefore('|')) {
			SONG -> {
				val (songId, folder) = id.removePrefix("$SONG|").split('|', limit = 2)
				val songs = songs(folder).ifEmpty {
					listOfNotNull(songDao.getSongById(songId)?.toDomainModel())
				}
				songs to songs.indexOfFirst { it.id == songId }.coerceAtLeast(0)
			}
			PLAY -> songs(id.removePrefix("$PLAY|")) to 0
			SHUFFLE -> songs(id.removePrefix("$SHUFFLE|")).shuffled() to 0
			RADIO -> {
				val radio = radioDao.getRadios().find { it.radioId == id.removePrefix("$RADIO|") }
					?: return null
				listOf(radioSong("radio_${radio.name.hashCode()}", radio.name, radio.streamUrl)) to 0
			}
			else -> songs(id) to 0
		}
		if (songs.isEmpty()) return null
		return songs.map { it.toMediaItem(sessionManager) } to start
	}

	/** "Play X": a playlist, artist, album or song named X, else the first song or album found. */
	private suspend fun playSearch(query: String): Pair<List<MediaItem>, Int>? {
		if (query.isBlank()) return null
		val results = searchRepository.search(query)
		fun Any.named() = when (this) {
			is DomainPlaylist -> name
			is DomainArtist -> name
			is DomainAlbum -> name
			is DomainSong -> title
			else -> null
		}.equals(query, ignoreCase = true)
		val order = listOf(DomainPlaylist::class, DomainArtist::class, DomainAlbum::class, DomainSong::class)
		val best = order.firstNotNullOfOrNull { kind -> results.find { kind.isInstance(it) && it.named() } }
			?: results.firstOrNull { it is DomainSong }
			?: results.firstOrNull { it is DomainAlbum }
			?: results.firstOrNull { it is DomainArtist }
			?: return null
		val songs = when (best) {
			is DomainPlaylist -> songs("$PLAYLIST|${best.id}")
			is DomainArtist -> songs("$ARTIST|${best.id}").shuffled()
			is DomainAlbum -> songs("$ALBUM|${best.id}")
			// the song first, then the other songs found
			else -> listOf(best as DomainSong) + results.filterIsInstance<DomainSong>().filter { it.id != best.id }
		}
		return songs.ifEmpty { return null }.map { it.toMediaItem(sessionManager) } to 0
	}

	/** The songs folder [id] plays, in order; empty for folders of folders. */
	private suspend fun songs(id: String): List<DomainSong> = when (id) {
		STARRED_SONGS -> songDao.getAllSongs().map { it.toDomainModel() }
			.filter { it.starredAt != null }.sortedByDescending { it.starredAt }
		DOWNLOADED_SONGS -> songDao.getSongsByIds(audioStore.downloadedIds().toList())
			.map { it.toDomainModel() }
			.sortedWith(compareBy({ it.artistName }, { it.albumTitle }, { it.discNumber }, { it.trackNumber }))
		QUICK_PICKS -> homeRepository.quickPicks(homeRepository.library(), null, rebuild = false)
		else -> {
			val arg = id.substringAfter('|')
			when (id.substringBefore('|')) {
				ALBUM -> albumDao.getAlbumById(arg)?.toDomainModel()?.ordered().orEmpty()
				PLAYLIST -> playlistDao.getPlaylistById(arg)?.toDomainModel()?.songs.orEmpty()
				ARTIST, GENRE -> albums(id).flatMap { it.ordered() }
				else -> emptyList()
			}
		}
	}

	private suspend fun albums(id: String): List<DomainAlbum> {
		val arg = id.substringAfter('|')
		return when (id.substringBefore('|')) {
			ARTIST -> albumDao.getAlbumsByArtist(arg).first()
			GENRE -> albumDao.getAlbumsByGenre(arg).first()
			else -> emptyList()
		}.map { it.toDomainModel() }
	}

	private fun DomainAlbum.ordered() = songs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))

	/** The songs [id] stands for, for the queue actions: a song alone, a folder in its order. */
	suspend fun songsOf(id: String): List<DomainSong> =
		if (id.substringBefore('|') == SONG) {
			listOfNotNull(songDao.getSongById(id.split('|')[1])?.toDomainModel())
		} else {
			songs(id)
		}

	private suspend fun collectionItem(id: String): MediaItem? =
		albumDao.getAlbumById(id)?.let { albumItem(it.toDomainModel()) }
			?: playlistDao.getPlaylistById(id)?.let { playlistItem(it.toDomainModel()) }

	private suspend fun actions(folder: String) = listOf(
		item("$PLAY|$folder", getString(Res.string.action_play_all), playable = true),
		item("$SHUFFLE|$folder", getString(Res.string.action_shuffle), playable = true)
	)

	private suspend fun folder(id: String, title: StringResource, grid: Boolean = false) =
		folder(id, getString(title), grid)

	private fun folder(id: String, title: String, grid: Boolean = false) = item(
		id, title, browsable = true, extras = if (grid) style(browsable = GRID) else null
	)

	private fun albumItem(album: DomainAlbum, group: String? = null) = item(
		"$ALBUM|${album.id}", album.name.orEmpty(), album.artistName, album.coverArtId,
		browsable = true, type = MediaMetadata.MEDIA_TYPE_ALBUM, extras = groupExtras(group)
	)

	private fun playlistItem(playlist: DomainPlaylist, group: String? = null) = item(
		"$PLAYLIST|${playlist.id}", playlist.name.orEmpty(), null, playlist.coverArtId,
		browsable = true, type = MediaMetadata.MEDIA_TYPE_PLAYLIST, extras = groupExtras(group)
	)

	private fun artistItem(artist: DomainArtist) = item(
		"$ARTIST|${artist.id}", artist.name, null, artist.coverArtId,
		browsable = true, type = MediaMetadata.MEDIA_TYPE_ARTIST, extras = style(browsable = GRID)
	)

	private fun songItem(song: DomainSong, folder: String, downloaded: Boolean) = item(
		"$SONG|${song.id}|$folder", song.title,
		song.artists.joinToString { it.name }.ifBlank { song.artistName }, song.coverArtId,
		playable = true, type = MediaMetadata.MEDIA_TYPE_MUSIC,
		extras = Bundle().apply {
			if (song.explicitStatus == DomainExplicitStatus.Explicit) {
				putLong(MediaConstants.EXTRAS_KEY_IS_EXPLICIT, MediaConstants.EXTRAS_VALUE_ATTRIBUTE_PRESENT)
			}
			if (downloaded) {
				putLong(MediaConstants.EXTRAS_KEY_DOWNLOAD_STATUS, MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED)
			}
		}
	)

	private fun item(
		id: String,
		title: String,
		subtitle: String? = null,
		coverArtId: String? = null,
		browsable: Boolean = false,
		playable: Boolean = false,
		type: Int = if (browsable) MediaMetadata.MEDIA_TYPE_FOLDER_MIXED else MediaMetadata.MEDIA_TYPE_MUSIC,
		extras: Bundle? = null
	) = MediaItem.Builder()
		.setMediaId(id)
		.setMediaMetadata(
			MediaMetadata.Builder()
				.setTitle(title)
				.setSubtitle(subtitle)
				.setArtist(subtitle)
				.setArtworkUri(coverArtId?.let(CoverArtProvider::uri))
				.setIsBrowsable(browsable)
				.setIsPlayable(playable)
				.setMediaType(type)
				// songs and what holds them can join the queue from the car's item menu
				.setSupportedCommands(
					if (id.substringBefore('|') in QUEUEABLE) listOf(PLAY_NEXT, ADD_TO_QUEUE) else emptyList()
				)
				.setExtras(extras)
				.build()
		)
		.build()

	private fun style(browsable: Int) = Bundle().apply {
		putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, browsable)
	}

	private fun groupExtras(group: String?) = group?.let {
		Bundle().apply { putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) }
	}

	companion object {
		const val ROOT = "root"
		private const val HOME = "home"
		private const val STARRED = "starred"
		private const val LIBRARY = "library"
		private const val DOWNLOADS = "downloads"
		private const val PLAYLISTS = "playlists"
		private const val ARTISTS = "artists"
		private const val ALBUMS = "albums"
		private const val GENRES = "genres"
		private const val RADIOS = "radios"
		private const val STARRED_SONGS = "starred_songs"
		private const val STARRED_ALBUMS = "starred_albums"
		private const val STARRED_ARTISTS = "starred_artists"
		private const val DOWNLOADED_SONGS = "downloaded_songs"
		private const val QUICK_PICKS = "quick_picks"

		/** A song found by search: it plays alone, and Autoplay carries on. */
		private const val SINGLE = "single"

		private const val ALBUM = "album"
		private const val PLAYLIST = "playlist"
		private const val ARTIST = "artist"
		private const val GENRE = "genre"
		private const val RADIO = "radio"
		private const val SONG = "song"
		private const val PLAY = "play"
		private const val SHUFFLE = "shuffle"
		private val QUEUEABLE = setOf(SONG, ALBUM, PLAYLIST, ARTIST, GENRE)

		/** Session commands of the car's per-item menu; the item's id comes in their args. */
		const val PLAY_NEXT = "PLAY_NEXT"
		const val ADD_TO_QUEUE = "ADD_TO_QUEUE"

		private const val GRID = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM

		/** Root extras: grid-or-list defaults and search support, read by Android Auto. */
		val rootExtras = Bundle().apply {
			putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
			putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
			putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
		}
	}
}
