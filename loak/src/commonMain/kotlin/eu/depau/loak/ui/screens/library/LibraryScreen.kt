package eu.depau.loak.ui.screens.library

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.GenreDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Album
import eu.depau.loak.icons.outlined.Artist
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.Genre
import eu.depau.loak.icons.outlined.History
import eu.depau.loak.icons.outlined.Note
import eu.depau.loak.icons.outlined.PlaylistPlay
import eu.depau.loak.icons.outlined.Radio
import eu.depau.loak.icons.outlined.Share
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.RootTopBar
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.util.IoDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private data class LibraryCounts(
	val songs: Int = 0, val albums: Int = 0, val artists: Int = 0, val genres: Int = 0,
	val playlists: Int = 0, val starred: Int = 0
)

private class LibraryRow(
	val icon: ImageVector,
	val title: StringResource,
	val count: Int?,
	val destination: Screen?,
	val enabled: Boolean = true
)

/**
 * The catch-all: every kind of thing in the library, plainly, always on the bar. "Downloaded"
 * narrows it to what's on this device (rows then open Downloads).
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
	var downloadedOnly by rememberSaveable { mutableStateOf(false) }

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
	val offline by produceState(Triple(0, 0, 0), downloaded) {
		value = withContext(IoDispatcher) {
			val songs = songDao.getSongsByIds(downloaded.keys.toList())
			Triple(songs.size, songs.mapNotNull { it.belongsToAlbumId }.toSet().size, songs.map { it.artistId }.toSet().size)
		}
	}

	val rows = if (!downloadedOnly) listOf(
		listOf(
			LibraryRow(Icons.Outlined.Note, Res.string.title_songs, counts.songs, Screen.SongList(nested = true)),
			LibraryRow(Icons.Outlined.Album, Res.string.title_albums, counts.albums, Screen.AlbumList(nested = true)),
			LibraryRow(Icons.Outlined.Artist, Res.string.title_artists, counts.artists, Screen.ArtistList(nested = true)),
			LibraryRow(Icons.Outlined.Genre, Res.string.title_genres, counts.genres, Screen.GenreList(nested = true))
		),
		listOf(
			LibraryRow(Icons.Outlined.PlaylistPlay, Res.string.title_playlists, counts.playlists, Screen.PlaylistList(nested = true)),
			LibraryRow(Icons.Outlined.Star, Res.string.title_starred, counts.starred, Screen.Starred(nested = true)),
			LibraryRow(Icons.Outlined.History, Res.string.title_recently_played, null,
				Screen.AlbumList(nested = true, listType = DomainAlbumListType.Recent)),
			LibraryRow(Icons.Outlined.Radio, Res.string.title_internet_radio, null, Screen.RadioList(nested = true)),
			LibraryRow(Icons.Outlined.Share, Res.string.title_shares, null, Screen.ShareList)
		)
	) else listOf(
		listOf(
			LibraryRow(Icons.Outlined.Note, Res.string.title_songs, offline.first, Screen.Downloads(DownloadsTab.Songs)),
			LibraryRow(Icons.Outlined.Album, Res.string.title_albums, offline.second, Screen.Downloads(DownloadsTab.Albums)),
			LibraryRow(Icons.Outlined.Artist, Res.string.title_artists, offline.third, Screen.Downloads(DownloadsTab.Artists)),
			LibraryRow(Icons.Outlined.Genre, Res.string.title_genres, null, null, enabled = false)
		),
		listOf(
			LibraryRow(Icons.Outlined.PlaylistPlay, Res.string.title_playlists, null, null, enabled = false),
			LibraryRow(Icons.Outlined.Star, Res.string.title_starred, null, null, enabled = false),
			LibraryRow(Icons.Outlined.History, Res.string.title_recently_played, null, null, enabled = false),
			LibraryRow(Icons.Outlined.Radio, Res.string.title_internet_radio, null, null, enabled = false),
			LibraryRow(Icons.Outlined.Share, Res.string.title_shares, null, null, enabled = false)
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
			item {
				Row(
					modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					FilterChip(
						selected = downloadedOnly,
						onClick = { downloadedOnly = !downloadedOnly },
						label = { Text(stringResource(Res.string.label_downloaded)) },
						leadingIcon = { Icon(Icons.Outlined.Download, null, Modifier.size(18.dp)) }
					)
					Spacer(Modifier.weight(1f))
					TextButton(onClick = dropUnlessResumed { backStack.add(Screen.Downloads(DownloadsTab.Songs)) }) {
						Icon(Icons.Outlined.Download, null, Modifier.size(18.dp))
						Spacer(Modifier.size(6.dp))
						Text(stringResource(Res.string.title_downloads))
					}
				}
			}
			rows.forEachIndexed { g, group ->
				if (g > 0) item { Spacer(Modifier.height(12.dp)) }
				group.forEachIndexed { i, row ->
					item {
						SegmentedListItem(
							modifier = Modifier.alpha(if (row.enabled) 1f else .38f),
							onClick = dropUnlessResumed { row.destination?.let { backStack.add(it) } },
							enabled = row.enabled,
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
			if (downloadedOnly) item {
				Text(
					stringResource(Res.string.info_downloaded_only),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(12.dp)
				)
			}
		}
	}
}

enum class DownloadsTab { Songs, Albums, Artists }

/** Everything on this device, by song, album or artist. */
@Composable
fun DownloadsScreen(initial: DownloadsTab) {
	val backStack = LocalNavStack.current
	val songDao = koinInject<SongDao>()
	val player = koinInject<MediaPlayerViewModel>()
	val downloadManager = koinInject<DownloadManager>()
	val downloaded by downloadManager.downloadedSongs.collectAsState()
	var tab by rememberSaveable { mutableStateOf(initial) }
	val songs by produceState(emptyList<DomainSong>(), downloaded) {
		value = withContext(IoDispatcher) {
			songDao.getSongsByIds(downloaded.keys.toList()).map { it.toDomainModel() }
				.sortedWith(compareBy({ it.artistName }, { it.albumTitle }, { it.trackNumber }))
		}
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
						}))
					}
				}
			}
			if (songs.isEmpty()) {
				Text(stringResource(Res.string.info_no_downloads), Modifier.padding(24.dp))
				return@Column
			}
			LazyColumn(Modifier.fillMaxSize()) {
				when (tab) {
					DownloadsTab.Songs -> items(songs, key = { it.id }) { song ->
						ListItem(
							modifier = Modifier.padding(horizontal = 4.dp).clickable { player.playNow(songs, songs.indexOf(song)) },
							leadingContent = { CoverArt(coverArtId = song.coverArtId, modifier = Modifier.size(48.dp)) },
							headlineContent = { Text(song.title, maxLines = 1) },
							supportingContent = { Text(listOfNotNull(song.artistName, song.albumTitle).joinToString(" · "), maxLines = 1) },
						)
					}
					DownloadsTab.Albums -> items(songs.groupBy { it.albumId }.entries.toList(), key = { it.key ?: "" }) { (albumId, albumSongs) ->
						val first = albumSongs.first()
						ListItem(
							modifier = Modifier.padding(horizontal = 4.dp).clickable(enabled = albumId != null) {
								albumId?.let { backStack.add(Screen.CollectionDetail(it, "")) }
							},
							leadingContent = { CoverArt(coverArtId = first.coverArtId, modifier = Modifier.size(48.dp)) },
							headlineContent = { Text(first.albumTitle ?: "", maxLines = 1) },
							supportingContent = { Text("${first.artistName ?: ""} · ${albumSongs.size}", maxLines = 1) }
						)
					}
					DownloadsTab.Artists -> items(songs.groupBy { it.artistId }.entries.toList(), key = { it.key ?: "" }) { (artistId, artistSongs) ->
						ListItem(
							modifier = Modifier.padding(horizontal = 4.dp).clickable(enabled = artistId != null) {
								artistId?.let { backStack.add(Screen.ArtistDetail(it)) }
							},
							headlineContent = { Text(artistSongs.first().artistName ?: "", maxLines = 1) },
							supportingContent = { Text("${artistSongs.size}", maxLines = 1) }
						)
					}
				}
			}
		}
	}
}
