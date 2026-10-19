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
import eu.depau.loak.util.toHoursMinutesSeconds
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
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.info_offline
import eu.depau.loak.domain.manager.ConnectivityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
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
	private val connectivityManager: ConnectivityManager by inject()
	private val context: Context by inject()

	fun root(): MediaItem = item(ROOT, "", browsable = true)

	// Layout follows Android Auto's media guidelines: artwork-led lists (albums, playlists,
	// artists) as grids, folders of folders as category lists with small tinted icons, and
	// short lists: Home is a handful of shelves, not every album it could show.
	// ponytail: whole lists from the DB per request, sliced into pages; DB paging if a big
	// library proves slow
	suspend fun children(id: String): List<MediaItem> = when (id) {
		// Downloads only when it has something: an empty tab is a dead end in the car
		ROOT -> listOfNotNull(
			folder(HOME, Res.string.title_home, R.drawable.auto_home),
			folder(LIBRARY, Res.string.title_library, R.drawable.auto_library, browsable = CATEGORY),
			folder(DOWNLOADS, Res.string.title_downloads, R.drawable.auto_download)
				.takeIf { hasDownloads.first() }
		)
		HOME -> home()
		LIBRARY -> listOf(
			folder(PLAYLISTS, Res.string.title_playlists, R.drawable.auto_playlist),
			folder(ALBUMS, Res.string.title_albums, R.drawable.auto_album),
			folder(ARTISTS, Res.string.title_artists, R.drawable.auto_artist),
			folder(STARRED, Res.string.title_starred, R.drawable.auto_star),
			folder(GENRES, Res.string.title_genres, R.drawable.auto_genre, browsable = CATEGORY),
			folder(RADIOS, Res.string.title_radios, R.drawable.auto_radio, playable = CATEGORY)
		)
		STARRED -> starred()
		DOWNLOADS -> listOfNotNull(
			playAll(DOWNLOADED_SONGS, Res.string.title_songs, icon = R.drawable.auto_download)
		) + downloadCollectionDao.getAll().mapNotNull { collectionItem(it.collectionId) }
		MADE_FOR_YOU -> homeRepository.madeForYou().map(::playlistItem)
		RECENTLY_ADDED -> homeRepository.recentlyAdded(homeRepository.library(), null).map(::albumItem)
		FORGOTTEN -> homeRepository.forgottenFavourites(homeRepository.library(), null).map(::albumItem)
		NEVER_PLAYED -> homeRepository.neverPlayed(homeRepository.library()).map(::albumItem)
		PLAYLISTS -> playlistDao.getAllPlaylistsByName().map { playlistItem(it.toDomainModel()) }
		ARTISTS -> artistDao.getArtistsAlphabeticalByName().map { artistItem(it.toDomainModel()) }
		ALBUMS -> albumDao.getAllAlbumsList().map { albumItem(it.toDomainModel()) }
		GENRES -> genreDao.getGenres().map {
			folder("$GENRE|${it.genreName}", it.genreName, R.drawable.auto_genre)
		}
		RADIOS -> radioDao.getRadios().map {
			item(
				"$RADIO|${it.radioId}", it.name, icon = R.drawable.auto_radio, playable = true,
				type = MediaMetadata.MEDIA_TYPE_RADIO_STATION
			)
		}
		// Anything holding songs opens on a row shuffling them all, then its contents: an artist's
		// or genre's albums (all their songs would be too long a list), or the songs, where picking
		// the first plays them all in order
		else -> when (id.substringBefore('|')) {
			ARTIST, GENRE -> listOf(shuffleAll(id)) + albums(id).map(::albumItem)
			else -> {
				val songs = songs(id)
				val downloaded = audioStore.downloadedIds()
				if (songs.isEmpty()) emptyList()
				else listOf(shuffleAll(id)) + songs.map { songItem(it, id, it.id in downloaded) }
			}
		}
	}

	/**
	 * One row per shelf, each with its first item's artwork: song mixes play when picked, the
	 * others open a grid. Empty shelves are left out.
	 */
	private suspend fun home(): List<MediaItem> {
		val library = homeRepository.library()
		suspend fun shelf(id: String, title: StringResource, items: List<Pair<String?, String?>>) =
			items.takeIf { it.isNotEmpty() }?.let {
				item(
					id, getString(title), it.take(3).mapNotNull { (name, _) -> name }.joinToString(),
					it.first().second, browsable = true
				)
			}
		val offline = !connectivityManager.isOnline.value
		return listOfNotNull(
			playAll(DOWNLOADED_SONGS, Res.string.info_offline, icon = R.drawable.auto_download)
				.takeIf { offline },
			playAll(QUICK_PICKS, Res.string.title_quick_picks),
			shelf(
				MADE_FOR_YOU, Res.string.title_made_for_you,
				homeRepository.madeForYou().map { it.name to it.coverArtId }
			),
			shelf(
				RECENTLY_ADDED, Res.string.lens_recently_added,
				homeRepository.recentlyAdded(library, null).map { it.name to it.coverArtId }
			),
			playAll(STARRED_SONGS, Res.string.title_starred),
			shelf(
				FORGOTTEN, Res.string.title_forgotten_favourites,
				homeRepository.forgottenFavourites(library, null).map { it.name to it.coverArtId }
			),
			shelf(
				NEVER_PLAYED, Res.string.title_never_played,
				homeRepository.neverPlayed(library).map { it.name to it.coverArtId }
			)
		)
	}

	/** Library › Starred: a row playing every starred song, then the starred albums and artists. */
	private suspend fun starred(): List<MediaItem> {
		val albums = albumDao.getAllAlbumsList().map { it.toDomainModel() }
			.filter { it.starredAt != null }.sortedByDescending { it.starredAt }
		return listOfNotNull(playAll(STARRED_SONGS, Res.string.title_songs)) +
			albums.map { albumItem(it, group = getString(Res.string.title_albums)) } +
			artistDao.getArtistsStarred().map {
				artistItem(it.toDomainModel(), group = getString(Res.string.title_artists))
			}
	}

	/**
	 * A row that plays the songs of [folder] (none when it has none), with the first artwork
	 * among them, or [icon], and the song count.
	 */
	private suspend fun playAll(folder: String, title: StringResource, icon: Int? = null): MediaItem? {
		val songs = songs(folder).ifEmpty { return null }
		return item(
			"$PLAY|$folder", getString(title),
			getPluralString(Res.plurals.count_songs, songs.size, songs.size),
			songs.firstNotNullOfOrNull { it.coverArtId ?: it.albumId }.takeIf { icon == null }, icon,
			playable = true, extras = style(single = icon?.let { CATEGORY })
		)
	}

	/** Whether the current server has any download, finished or still coming. */
	val hasDownloads: Flow<Boolean>
		get() = audioStore.pinned.map { it.isNotEmpty() }.distinctUntilChanged()

	/**
	 * Browse search: the artists, albums, songs and playlists matching [query], as rows under a
	 * title per kind. The kind holding an exact match comes first, so "ugress" leads with the
	 * artist; each kind shows its first few.
	 */
	suspend fun search(query: String): List<MediaItem> {
		val results = searchRepository.search(query)
		val downloaded = audioStore.downloadedIds()
		fun String?.exact() = equals(query, ignoreCase = true)
		val artists = results.filterIsInstance<DomainArtist>()
		val albums = results.filterIsInstance<DomainAlbum>()
		val songs = results.filterIsInstance<DomainSong>()
		val playlists = results.filterIsInstance<DomainPlaylist>()
		val artistsTitle = getString(Res.string.title_artists)
		val albumsTitle = getString(Res.string.title_albums)
		val songsTitle = getString(Res.string.title_songs)
		val playlistsTitle = getString(Res.string.title_playlists)
		return listOf(
			artists.any { it.name.exact() } to
				artists.take(SEARCH_GROUP_SIZE).map { artistItem(it, artistsTitle, tile = false) },
			albums.any { it.name.exact() } to
				albums.take(SEARCH_GROUP_SIZE).map { albumItem(it, albumsTitle, tile = false) },
			songs.any { it.title.exact() } to
				songs.take(SEARCH_GROUP_SIZE).map { songItem(it, SINGLE, it.id in downloaded, songsTitle) },
			playlists.any { it.name.exact() } to
				playlists.take(SEARCH_GROUP_SIZE).map { playlistItem(it, playlistsTitle, tile = false) }
		).sortedByDescending { (exact, _) -> exact }.flatMap { (_, items) -> items }
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

	/** The album a menu's Go to album opens, for the song [id]. */
	suspend fun albumOf(id: String): String? =
		songDao.getSongById(id.split('|')[1])?.toDomainModel()?.albumId?.let { "$ALBUM|$it" }

	/** The row opening a folder of songs (see [children]): Shuffle, for all of them. */
	private suspend fun shuffleAll(folder: String) = item(
		shuffled(folder), getString(Res.string.action_shuffle), icon = R.drawable.auto_shuffle,
		playable = true, extras = style(single = CATEGORY)
	)

	private suspend fun collectionItem(id: String): MediaItem? =
		albumDao.getAlbumById(id)?.let { albumItem(it.toDomainModel()) }
			?: playlistDao.getPlaylistById(id)?.let { playlistItem(it.toDomainModel()) }

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

	// Artwork opens its songs (an artist, its albums), in grids or, as [tile] false, in rows.
	// Only Android Automotive has item menus (see commandsFor), so this works everywhere.
	private fun albumItem(album: DomainAlbum, group: String? = null, tile: Boolean = true) = item(
		"$ALBUM|${album.id}", album.name.orEmpty(), album.artistName, album.coverArtId,
		browsable = true, type = MediaMetadata.MEDIA_TYPE_ALBUM,
		extras = style(single = tile.style, group = group)
	)

	private fun playlistItem(
		playlist: DomainPlaylist,
		group: String? = null,
		tile: Boolean = true
	) = item(
		"$PLAYLIST|${playlist.id}", playlist.name.orEmpty(), null, playlist.coverArtId,
		browsable = true, type = MediaMetadata.MEDIA_TYPE_PLAYLIST,
		extras = style(single = tile.style, group = group)
	)

	private fun artistItem(artist: DomainArtist, group: String? = null, tile: Boolean = true) = item(
		"$ARTIST|${artist.id}", artist.name, null, artist.coverArtId,
		browsable = true, type = MediaMetadata.MEDIA_TYPE_ARTIST,
		extras = style(single = tile.style, group = group)
	)

	private val Boolean.style get() = if (this) GRID else LIST

	/**
	 * A song; an album's own tracks show their length instead of the artist they share. They
	 * keep the artwork: without one, Android Automotive draws a coloured placeholder, so a song
	 * without its own takes its album's (Subsonic serves cover art by album id too).
	 */
	private fun songItem(
		song: DomainSong,
		folder: String,
		downloaded: Boolean,
		group: String? = null
	): MediaItem {
		val inAlbum = folder.substringBefore('|') == ALBUM
		return item(
			"$SONG|${song.id}|$folder", song.title,
			if (inAlbum) song.duration.toHoursMinutesSeconds()
			else song.artists.joinToString { it.name }.ifBlank { song.artistName },
			song.coverArtId ?: song.albumId,
			playable = true, type = MediaMetadata.MEDIA_TYPE_MUSIC,
			extras = style(group = group).apply {
				if (song.explicitStatus == DomainExplicitStatus.Explicit) {
					putLong(MediaConstants.EXTRAS_KEY_IS_EXPLICIT, MediaConstants.EXTRAS_VALUE_ATTRIBUTE_PRESENT)
				}
				if (downloaded) {
					putLong(MediaConstants.EXTRAS_KEY_DOWNLOAD_STATUS, MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED)
				}
			}
		)
	}

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
				// the car's per-item menu
				.setSupportedCommands(commandsFor(id.substringBefore('|')))
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
		const val HOME = "home"
		private const val STARRED = "starred"
		private const val LIBRARY = "library"
		private const val DOWNLOADS = "downloads"
		private const val PLAYLISTS = "playlists"
		private const val ARTISTS = "artists"
		private const val ALBUMS = "albums"
		private const val GENRES = "genres"
		private const val RADIOS = "radios"
		private const val STARRED_SONGS = "starred_songs"
		private const val DOWNLOADED_SONGS = "downloaded_songs"
		private const val QUICK_PICKS = "quick_picks"
		private const val MADE_FOR_YOU = "made_for_you"
		private const val RECENTLY_ADDED = "recently_added"
		private const val FORGOTTEN = "forgotten_favourites"
		private const val NEVER_PLAYED = "never_played"

		/** A song found by search: it plays alone, and Autoplay carries on. */
		private const val SINGLE = "single"

		/** Search results shown per kind: past a handful, a driver searches again. */
		private const val SEARCH_GROUP_SIZE = 5

		private const val ALBUM = "album"
		private const val PLAYLIST = "playlist"
		private const val ARTIST = "artist"
		private const val GENRE = "genre"
		private const val RADIO = "radio"
		private const val SONG = "song"
		private const val PLAY = "play"
		private const val SHUFFLE = "shuffle"

		/**
		 * Session commands of the car's per-item menu; the item's id comes in their args. The
		 * host shows only as many as it has room for, so each list leads with the likeliest.
		 */
		const val PLAY_NEXT = "PLAY_NEXT"
		const val ADD_TO_QUEUE = "ADD_TO_QUEUE"
		const val SHUFFLE_ITEM = "SHUFFLE"
		const val GO_TO_ALBUM = "GO_TO_ALBUM"

		private fun commandsFor(kind: String) = when (kind) {
			SONG -> listOf(PLAY_NEXT, ADD_TO_QUEUE, GO_TO_ALBUM)
			ALBUM, PLAYLIST, ARTIST -> listOf(SHUFFLE_ITEM, PLAY_NEXT, ADD_TO_QUEUE)
			else -> emptyList()
		}

		val MENU_COMMANDS = listOf(SHUFFLE_ITEM, PLAY_NEXT, ADD_TO_QUEUE, GO_TO_ALBUM)

		/** The id that plays the songs of [id] shuffled. */
		fun shuffled(id: String) = "$SHUFFLE|$id"

		/** What a custom browse action's result asks of the host, in its (androidx.media) keys. */
		private const val RESULT = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT"
		const val RESULT_BROWSE_NODE = "${RESULT}_BROWSE_NODE"
		const val RESULT_SHOW_PLAYING_ITEM = "${RESULT}_SHOW_PLAYING_ITEM"
		const val RESULT_MESSAGE = "${RESULT}_MESSAGE"

		private const val GRID = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
		private const val LIST = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
		private const val CATEGORY = MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_CATEGORY_LIST_ITEM

		/** Root extras: grid-or-list defaults and search support, read by Android Auto. */
		val rootExtras = Bundle().apply {
			putBoolean("android.media.browse.SEARCH_SUPPORTED", true)
			putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
			putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
		}
	}
}
