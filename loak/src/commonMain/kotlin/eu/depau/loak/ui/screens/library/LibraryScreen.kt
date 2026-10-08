package eu.depau.loak.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.GenreDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.entities.DownloadCollectionEntity
import eu.depau.loak.data.database.entities.DownloadCollectionType
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.models.CronSchedule
import eu.depau.loak.domain.models.describe
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Album
import eu.depau.loak.icons.outlined.Artist
import eu.depau.loak.icons.outlined.BarChart
import eu.depau.loak.icons.outlined.Calendar
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.icons.outlined.Delete
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.Explore
import eu.depau.loak.icons.outlined.Genre
import eu.depau.loak.icons.outlined.History
import eu.depau.loak.icons.outlined.Note
import eu.depau.loak.icons.outlined.PlaylistPlay
import eu.depau.loak.icons.outlined.Radio
import eu.depau.loak.icons.outlined.RecentlyAdded
import eu.depau.loak.icons.outlined.Refresh
import eu.depau.loak.icons.outlined.Share
import eu.depau.loak.icons.outlined.Shuffle
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.icons.outlined.Trophy
import eu.depau.loak.shared.MediaPlayerViewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.RootTopBar
import eu.depau.loak.ui.components.sheets.ScheduleSheet
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.util.IoDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.ui.components.common.SongRow
import androidx.compose.foundation.lazy.itemsIndexed

private data class LibraryCounts(
	val songs: Int = 0, val albums: Int = 0, val artists: Int = 0, val genres: Int = 0,
	val playlists: Int = 0, val starred: Int = 0
)

private class LibraryRow(
	val icon: ImageVector,
	val title: StringResource,
	val count: Int?,
	val destination: Screen?
)

/**
 * The catch-all: every kind of thing in the library, plainly, always on the bar. A row opens
 * that list; "Downloaded" opens the on-device Downloads screen (pinned + available, any status).
 */
@Composable
fun LibraryScreen() {
	val backStack = LocalNavStack.current
	val songDao = koinInject<SongDao>()
	val albumDao = koinInject<AlbumDao>()
	val artistDao = koinInject<ArtistDao>()
	val genreDao = koinInject<GenreDao>()
	val playlistDao = koinInject<PlaylistDao>()
	val downloadManager = koinInject<DownloadManager>()
	val downloaded by downloadManager.downloadedSongs.collectAsState()

	val counts by produceState(LibraryCounts()) {
		value = withContext(IoDispatcher) {
			val songs = songDao.getAllSongs()
			LibraryCounts(
				songs = songs.size,
				albums = albumDao.getAlbumCount(),
				artists = artistDao.getAllArtistIds().size,
				genres = genreDao.getAllGenreNames().size,
				playlists = playlistDao.getPlaylistCount(),
				starred = songs.count { it.starredAt != null }
			)
		}
	}

	val rows = listOf(
		listOf(
			LibraryRow(Icons.Outlined.Note, Res.string.title_songs, counts.songs, Screen.SongList(nested = true)),
			LibraryRow(Icons.Outlined.Album, Res.string.title_albums, counts.albums, Screen.AlbumList(nested = true)),
			LibraryRow(Icons.Outlined.Artist, Res.string.title_artists, counts.artists, Screen.ArtistList(nested = true)),
			LibraryRow(Icons.Outlined.Genre, Res.string.title_genres, counts.genres, Screen.GenreList(nested = true)),
			LibraryRow(Icons.Outlined.Download, Res.string.label_downloaded, downloaded.size, Screen.Downloads())
		),
		listOf(
			LibraryRow(Icons.Outlined.RecentlyAdded, Res.string.lens_recently_added, null, Screen.AlbumList(true, DomainAlbumListType.Newest)),
			LibraryRow(Icons.Outlined.History, Res.string.title_recently_played, null, Screen.AlbumList(true, DomainAlbumListType.Recent)),
			LibraryRow(Icons.Outlined.BarChart, Res.string.lens_most_played, null, Screen.AlbumList(true, DomainAlbumListType.Frequent)),
			LibraryRow(Icons.Outlined.Trophy, Res.string.lens_highest_rated, null, Screen.AlbumList(true, DomainAlbumListType.Highest)),
			LibraryRow(Icons.Outlined.Shuffle, Res.string.lens_random, null, Screen.AlbumList(true, DomainAlbumListType.Random)),
			LibraryRow(Icons.Outlined.Calendar, Res.string.lens_by_year, null, Screen.AlbumList(true, DomainAlbumListType.Year)),
			LibraryRow(Icons.Outlined.Star, Res.string.title_starred, counts.starred, Screen.Starred(nested = true))
		),
		listOf(
			LibraryRow(Icons.Outlined.PlaylistPlay, Res.string.title_playlists, counts.playlists, Screen.PlaylistList(nested = true)),
			LibraryRow(Icons.Outlined.Explore, Res.string.title_explore, null, Screen.Explore),
			LibraryRow(Icons.Outlined.Radio, Res.string.title_internet_radio, null, Screen.RadioList(nested = true)),
			LibraryRow(Icons.Outlined.Share, Res.string.title_shares, null, Screen.ShareList)
		)
	)

	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
	Scaffold(
		modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		topBar = { RootTopBar(title = { Text(stringResource(Res.string.title_library)) }, scrollBehavior = scrollBehavior) },
		bottomBar = { RootBottomBar(scrolled = LocalBottomBarScrollManager.current.isTriggered) }
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = 16.dp, end = 16.dp,
				top = innerPadding.calculateTopPadding(),
				bottom = innerPadding.calculateBottomPadding() + 24.dp
			),
			verticalArrangement = Arrangement.spacedBy(SegmentedListItemDefaults.SegmentedGap)
		) {
			rows.forEachIndexed { g, group ->
				if (g > 0) item { Spacer(Modifier.height(12.dp)) }
				group.forEachIndexed { i, row ->
					item {
						SegmentedListItem(
							onClick = dropUnlessResumed { row.destination?.let { backStack.add(it) } },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = i, count = group.size),
							leadingContent = { Icon(row.icon, null) },
							content = { Text(stringResource(row.title)) },
							trailingContent = {
								Row(verticalAlignment = Alignment.CenterVertically) {
									row.count?.let { Text("$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
									Icon(Icons.Outlined.ChevronForward, null)
								}
							}
						)
					}
				}
			}
		}
	}
}

enum class DownloadsTab { Playlists, Albums, Artists, Songs }

/** Everything downloaded or on this device, by playlist, album, artist or song. */
@Composable
fun DownloadsScreen(initial: DownloadsTab) {
	val backStack = LocalNavStack.current
	val songDao = koinInject<SongDao>()
	val albumDao = koinInject<AlbumDao>()
	val artistDao = koinInject<ArtistDao>()
	val playlistDao = koinInject<PlaylistDao>()
	val player = koinInject<MediaPlayerViewModel>()
	val downloadManager = koinInject<DownloadManager>()
	val store = koinInject<AudioStore>()
	val downloads by downloadManager.allDownloads.collectAsState(initial = emptyList())
	val collections by downloadManager.collections.collectAsState()
	val storedSongs by store.storedSongs.collectAsState()
	var tab by rememberSaveable { mutableStateOf(initial) }
	// <playlistId, rec> for the rows with a schedule; null otherwise
	var scheduling by remember { mutableStateOf<DownloadCollectionEntity?>(null) }

	// downloaded (pinned + complete) song ids, one set for every tab's "done" count
	val completeBySong = remember(downloads) { downloads.associateBy { it.songId } }
	val downloadedIds = remember(completeBySong) {
		completeBySong.filterValues { it.status == DownloadStatus.DOWNLOADED }.keys
	}

	Scaffold(topBar = { NestedTopBar(title = { Text(stringResource(Res.string.title_downloads)) }) }) { innerPadding ->
		Column(Modifier.padding(innerPadding).fillMaxSize()) {
			SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
				DownloadsTab.entries.forEachIndexed { i, t ->
					SegmentedButton(
						selected = tab == t,
						onClick = { tab = t },
						shape = SegmentedButtonDefaults.itemShape(i, DownloadsTab.entries.size)
					) {
						Text(stringResource(when (t) {
							DownloadsTab.Songs -> Res.string.title_songs
							DownloadsTab.Albums -> Res.string.title_albums
							DownloadsTab.Artists -> Res.string.title_artists
							DownloadsTab.Playlists -> Res.string.title_playlists
						}))
					}
				}
			}
			when (tab) {
				DownloadsTab.Songs -> DownloadsSongsTab(songDao, player, completeBySong, storedSongs, downloads)
				DownloadsTab.Albums -> DownloadsAlbumsTab(albumDao, backStack, collections, downloadedIds)
				DownloadsTab.Artists -> DownloadsArtistsTab(artistDao, songDao, backStack, collections, downloadedIds)
				DownloadsTab.Playlists -> DownloadsPlaylistsTab(
					playlistDao, backStack, collections, downloadedIds,
					downloadManager, scheduling, { scheduling = it }
				)
			}
		}
	}

	scheduling?.let { rec ->
		ScheduleSheet(
			initialCron = rec.scheduleCron,
			initialEnabled = rec.scheduleEnabled,
			onDismissRequest = { scheduling = null },
			onSave = { cron, enabled ->
				downloadManager.setCollectionSchedule(rec.collectionId, cron, enabled)
				scheduling = null
			},
			onKick = { downloadManager.kickDownload(rec.collectionId); scheduling = null }
		)
	}
}

/** Songs on disk plus pinned-any-status ones, with each one's download status. */
@Composable
private fun DownloadsSongsTab(
	songDao: SongDao,
	player: MediaPlayerViewModel,
	completeBySong: Map<String, eu.depau.loak.data.database.entities.DownloadEntity>,
	storedSongs: Set<String>,
	downloads: List<eu.depau.loak.data.database.entities.DownloadEntity>
) {
	// union: available complete files + pinned songs of any status (pending ones included)
	val pinnedIds = remember(downloads) { downloads.map { it.songId }.toSet() }
	val songs by produceState(emptyList<DomainSong>(), storedSongs, pinnedIds) {
		value = withContext(IoDispatcher) {
			val ids = (storedSongs + pinnedIds).toList()
			if (ids.isEmpty()) emptyList() else songDao.getSongsByIds(ids).map { it.toDomainModel() }
				.sortedWith(compareBy({ it.artistName }, { it.albumTitle }, { it.trackNumber }))
		}
	}

	if (songs.isEmpty()) {
		EmptyDownloads(Res.string.info_no_downloads)
		return
	}
	LazyColumn(Modifier.fillMaxSize()) {
		itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
			SongRow(
				song = song,
				onClick = { player.playNow(songs, index) },
				modifier = Modifier.fillMaxWidth(),
				download = completeBySong[song.id]
			)
		}
	}
}

@Composable
private fun DownloadsAlbumsTab(
	albumDao: AlbumDao,
	backStack: NavBackStack<NavKey>,
	collections: List<DownloadCollectionEntity>,
	downloadedIds: Set<String>
) {
	val subscribed = remember(collections) {
		collections.filter { it.type == DownloadCollectionType.ALBUM }.map { it.collectionId }.toSet()
	}
	val ids by produceState(emptyList<String>(), subscribed) {
		val all = albumDao.getAllAlbumIds().toSet()
		value = (subscribed intersect all).toList()
	}
	val albums by produceState(emptyList<eu.depau.loak.data.database.relations.AlbumWithSongs>(), ids) {
		if (ids.isEmpty()) value = emptyList()
		else value = albumDao.getAlbumsByIds(ids).sortedBy { it.album.name?.lowercase() }
	}

	if (albums.isEmpty()) {
		EmptyDownloads(Res.string.info_no_downloaded_albums)
		return
	}
	LazyColumn(Modifier.fillMaxSize()) {
		items(albums, key = { it.album.albumId }) { albumWithSongs ->
			val album = albumWithSongs.album
			val total = album.songCount.coerceAtLeast(albumWithSongs.songs.size.coerceAtLeast(1))
			val done = albumWithSongs.songs.count { it.songId in downloadedIds }
			CollectionRow(
				title = album.name.orEmpty(),
				subtitle = listOfNotNull(album.artistName, "${done}/$total").joinToString(" · "),
				coverArtId = album.coverArtId,
				done = done,
				total = total,
				onClick = { backStack.add(Screen.CollectionDetail(album.albumId, "")) }
			)
		}
	}
}

@Composable
private fun DownloadsArtistsTab(
	artistDao: ArtistDao,
	songDao: SongDao,
	backStack: NavBackStack<NavKey>,
	collections: List<DownloadCollectionEntity>,
	downloadedIds: Set<String>
) {
	val subscribed = remember(collections) {
		collections.filter { it.type == DownloadCollectionType.ARTIST }.map { it.collectionId }.toSet()
	}
	val ids by produceState(emptyList<String>(), subscribed) {
		val all = artistDao.getAllArtistIds().toSet()
		value = (subscribed intersect all).toList()
	}
	data class Row(val name: String, val coverArtId: String?, val done: Int, val total: Int)
	val rows by produceState(emptyList<Row>(), ids) {
		if (ids.isEmpty()) {
			value = emptyList()
		} else {
			value = withContext(IoDispatcher) {
				val artists = artistDao.getArtistsByIds(ids)
				artists.map { artist ->
					val songs = songDao.getSongsByArtistId(artist.artistId)
					Row(artist.name, artist.coverArtId, songs.count { it.songId in downloadedIds }, songs.size)
				}.sortedBy { it.name.lowercase() }
			}
		}
	}

	if (rows.isEmpty()) {
		EmptyDownloads(Res.string.info_no_downloaded_artists)
		return
	}
	LazyColumn(Modifier.fillMaxSize()) {
		items(rows, key = { it.name }) { row ->
			CollectionRow(
				title = row.name,
				subtitle = if (row.total > 0) "${row.done}/${row.total}"
				else "${row.done}",
				coverArtId = row.coverArtId,
				done = row.done,
				total = row.total,
				onClick = { backStack.add(Screen.ArtistDetail(row.name)) }
			)
		}
	}
}

@Composable
private fun DownloadsPlaylistsTab(
	playlistDao: PlaylistDao,
	backStack: NavBackStack<NavKey>,
	collections: List<DownloadCollectionEntity>,
	downloadedIds: Set<String>,
	downloadManager: DownloadManager,
	scheduling: DownloadCollectionEntity?,
	onSchedule: (DownloadCollectionEntity) -> Unit
) {
	val byId = remember(collections) { collections.associateBy { it.collectionId } }
	val ids by produceState(emptyList<String>(), byId.keys) {
		val all = playlistDao.getAllPlaylistIds().toSet()
		value = (byId.keys intersect all).toList()
	}
	data class Row(val playlist: eu.depau.loak.data.database.relations.PlaylistWithSongs, val subscribed: DownloadCollectionEntity?)
	val playlists by produceState(emptyList<Row>(), ids) {
		if (ids.isEmpty()) value = emptyList()
		else value = playlistDao.getPlaylistsByIds(ids)
			.map { Row(it, byId[it.playlist.playlistId]) }
			.sortedBy { it.playlist.playlist.name?.lowercase() }
	}

	if (playlists.isEmpty()) {
		EmptyDownloads(Res.string.info_no_downloaded_playlists)
		return
	}
	LazyColumn(Modifier.fillMaxSize()) {
		items(playlists, key = { it.playlist.playlist.playlistId }) { row ->
			val entity = row.playlist.playlist
			val total = entity.songCount.coerceAtLeast(row.playlist.songs.size.coerceAtLeast(1))
			val done = row.playlist.songs.count { it.song.songId in downloadedIds }
			val rec = row.subscribed
			val subtitle = buildList {
				add("${done}/$total")
				rec?.takeIf { it.scheduleEnabled && it.scheduleCron != null }?.let {
					add(CronSchedule.parse(it.scheduleCron!!)?.describe() ?: it.scheduleCron)
				}
			}
			val scope = rememberCoroutineScope()
			CollectionRow(
				title = entity.name.orEmpty(),
				subtitle = subtitle.joinToString(" · "),
				coverArtId = entity.coverArtId,
				done = done,
				total = total,
				onClick = { backStack.add(Screen.CollectionDetail(entity.playlistId, "")) },
				modifier = Modifier.combinedClickable(
					onClick = {},
					onLongClick = { rec?.let(onSchedule) }
				),
				trailing = {
					if (rec != null) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
						Icon(
							Icons.Outlined.Refresh,
							stringResource(Res.string.action_download_now),
							modifier = Modifier.clickable { downloadManager.kickDownload(entity.playlistId) }
						)
						Icon(
							Icons.Outlined.Delete,
							stringResource(Res.string.action_delete_download),
							modifier = Modifier.clickable {
								scope.launch { downloadManager.removeCollectionRow(entity.playlistId) }
							}
						)
					}
				}
			)
		}
	}
}

/** One row of the Downloads screen, with a progress fill when [done] < [total]. */
@Composable
private fun CollectionRow(
	title: String,
	subtitle: String,
	coverArtId: String?,
	done: Int,
	total: Int,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	trailing: (@Composable () -> Unit)? = null
) {
	val fraction = if (total > 0) done.toFloat() / total else 1f
	val showProgress = total > 0 && done < total
	Column(modifier) {
		ListItem(
			leadingContent = { CoverArt(coverArtId = coverArtId, modifier = Modifier.size(48.dp)) },
			headlineContent = { Text(title, maxLines = 1) },
			supportingContent = { Text(subtitle, maxLines = 1) },
			trailingContent = trailing?.let { { it() } },
			modifier = Modifier.clickable(onClick = onClick)
		)
		if (showProgress) {
			Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
				LinearProgressIndicator(
					progress = { fraction },
					modifier = Modifier.weight(1f)
				)
				Spacer(Modifier.size(8.dp))
				Text(
					"${(fraction * 100).toInt()}%",
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.End
				)
			}
		}
	}
}

@Composable
private fun EmptyDownloads(message: StringResource) {
	Text(
		stringResource(message),
		modifier = Modifier.fillMaxWidth().padding(24.dp),
		textAlign = TextAlign.Center,
		color = MaterialTheme.colorScheme.onSurfaceVariant
	)
}
