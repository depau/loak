package eu.depau.loak.shared

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import eu.depau.loak.R
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.DownloadCollectionDao
import eu.depau.loak.data.database.dao.GenreDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.RadioDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.mappers.toDomainModel
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
	private val context: Context by inject()

	fun root(): MediaItem = item(ROOT, "", browsable = true)

	// Layout follows Android Auto's media guidelines: artwork-led lists (albums, playlists,
	// artists) as grids, folders of folders as category lists with small tinted icons.
	// ponytail: whole lists from the DB per request, sliced into pages; DB paging if a big
	// library proves slow
	suspend fun children(id: String): List<MediaItem> = when (id) {
		ROOT -> listOf(
			folder(HOME, Res.string.title_home, R.drawable.auto_home, browsable = GRID),
			folder(STARRED, Res.string.title_starred, R.drawable.auto_star, browsable = CATEGORY),
			folder(LIBRARY, Res.string.title_library, R.drawable.auto_library, browsable = CATEGORY),
			folder(DOWNLOADS, Res.string.title_downloads, R.drawable.auto_download, browsable = GRID)
		)
		HOME -> home()
		STARRED -> listOf(
			folder(STARRED_SONGS, Res.string.title_songs, R.drawable.auto_song),
			folder(STARRED_ALBUMS, Res.string.title_albums, R.drawable.auto_album, browsable = GRID),
			folder(STARRED_ARTISTS, Res.string.title_artists, R.drawable.auto_artist, browsable = GRID)
		)
		LIBRARY -> listOf(
			folder(PLAYLISTS, Res.string.title_playlists, R.drawable.auto_playlist, browsable = GRID),
			folder(ARTISTS, Res.string.title_artists, R.drawable.auto_artist, browsable = GRID),
			folder(ALBUMS, Res.string.title_albums, R.drawable.auto_album, browsable = GRID),
			folder(GENRES, Res.string.title_genres, R.drawable.auto_genre, browsable = CATEGORY),
			folder(RADIOS, Res.string.title_radios, R.drawable.auto_radio, playable = CATEGORY)
		)
		// a category tile among the downloaded albums and playlists
		DOWNLOADS -> listOf(
			folder(DOWNLOADED_SONGS, Res.string.title_songs, R.drawable.auto_song, single = CATEGORY_GRID)
		) + downloadCollectionDao.getAll().mapNotNull { collectionItem(it.collectionId) }
		PLAYLISTS -> playlistDao.getAllPlaylistsByName().map { playlistItem(it.toDomainModel()) }
		ARTISTS -> artistDao.getArtistsAlphabeticalByName().map { artistItem(it.toDomainModel()) }
		STARRED_ARTISTS -> artistDao.getArtistsStarred().map { artistItem(it.toDomainModel()) }
		ALBUMS -> albumDao.getAllAlbumsList().map { albumItem(it.toDomainModel()) }
		STARRED_ALBUMS -> albumDao.getAllAlbumsList().map { it.toDomainModel() }
			.filter { it.starredAt != null }.sortedByDescending { it.starredAt }.map(::albumItem)
		GENRES -> genreDao.getGenres().map {
			folder("$GENRE|${it.genreName}", it.genreName, R.drawable.auto_genre, browsable = GRID)
		}
		RADIOS -> radioDao.getRadios().map {
			item(
				"$RADIO|${it.radioId}", it.name, icon = R.drawable.auto_radio, playable = true,
				type = MediaMetadata.MEDIA_TYPE_RADIO_STATION
			)
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
		return listOf(
			folder(QUICK_PICKS, Res.string.title_quick_picks, R.drawable.auto_quick_picks, single = CATEGORY_GRID)
		) +
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

	// small tinted rows above the songs, not artwork tiles
	private suspend fun actions(folder: String) = listOf(
		item(
			"$PLAY|$folder", getString(Res.string.action_play_all), icon = R.drawable.auto_play,
			playable = true, extras = style(single = CATEGORY)
		),
		item(
			"$SHUFFLE|$folder", getString(Res.string.action_shuffle), icon = R.drawable.auto_shuffle,
			playable = true, extras = style(single = CATEGORY)
		)
	)

	/** A folder; [browsable], [playable] and [single] are content styles, see [style]. */
	private suspend fun folder(
		id: String,
		title: StringResource,
		icon: Int,
		browsable: Int? = null,
		playable: Int? = null,
		single: Int? = null
	) = folder(id, getString(title), icon, browsable, playable, single)

	private fun folder(
		id: String,
		title: String,
		icon: Int,
		browsable: Int? = null,
		playable: Int? = null,
		single: Int? = null
	) = item(id, title, icon = icon, browsable = true, extras = style(browsable, playable, single))

	private fun albumItem(album: DomainAlbum, group: String? = null) = item(
		"$ALBUM|${album.id}", album.name.orEmpty(), album.artistName, album.coverArtId,
		browsable = true, type = MediaMetadata.MEDIA_TYPE_ALBUM, extras = style(group = group)
	)

	private fun playlistItem(playlist: DomainPlaylist, group: String? = null) = item(
		"$PLAYLIST|${playlist.id}", playlist.name.orEmpty(), null, playlist.coverArtId,
		browsable = true, type = MediaMetadata.MEDIA_TYPE_PLAYLIST, extras = style(group = group)
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

	/** [coverArtId] for artwork, else the vector [icon], which hosts tint to their theme. */
	private fun item(
		id: String,
		title: String,
		subtitle: String? = null,
		coverArtId: String? = null,
		icon: Int? = null,
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
				.setArtworkUri(coverArtId?.let(CoverArtProvider::uri) ?: icon?.let(::iconUri))
				.setIsBrowsable(browsable)
				.setIsPlayable(playable)
				.setMediaType(type)
				// songs and their albums and playlists can join the queue from the car's item menu
				.setSupportedCommands(
					if (id.substringBefore('|') in QUEUEABLE) listOf(PLAY_NEXT, ADD_TO_QUEUE) else emptyList()
				)
				.setExtras(extras)
				.build()
		)
		.build()

	/**
	 * Content style hints: how the item's [browsable] and [playable] children lay out, how the
	 * item itself does ([single]), and the subheader it sits under ([group]).
	 */
	private fun style(
		browsable: Int? = null,
		playable: Int? = null,
		single: Int? = null,
		group: String? = null
	) = Bundle().apply {
		browsable?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, it) }
		playable?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, it) }
		single?.let { putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, it) }
		group?.let { putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, it) }
	}

	/** A drawable as hosts load icons: `android.resource://<package>/drawable/<name>`. */
	fun iconUri(id: Int): Uri = Uri.Builder()
		.scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
		.authority(context.packageName)
		.appendPath(context.resources.getResourceTypeName(id))
		.appendPath(context.resources.getResourceEntryName(id))
		.build()

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
		private val QUEUEABLE = setOf(SONG, ALBUM, PLAYLIST)

		/** Session commands of the car's per-item menu; the item's id comes in their args. */
		const val PLAY_NEXT = "PLAY_NEXT"
		const val ADD_TO_QUEUE = "ADD_TO_QUEUE"

		private const val GRID = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
		private const val CATEGORY = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM
		private const val CATEGORY_GRID = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_GRID_ITEM

		/** Root extras: grid-or-list defaults and search support, read by Android Auto. */
		val rootExtras = Bundle().apply {
			putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
			putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
			putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
		}
	}
}
