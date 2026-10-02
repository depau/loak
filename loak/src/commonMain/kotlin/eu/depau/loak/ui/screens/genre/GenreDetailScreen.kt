package eu.depau.loak.ui.screens.genre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.DomainSongListType
import eu.depau.loak.domain.models.settings.BottomBarVisibilityMode
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.title_albums
import eu.depau.loak.generated.resources.title_songs
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Album
import eu.depau.loak.icons.outlined.Note
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.PullToRefreshBox
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.horizontalSection
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.album.components.AlbumListScreenGridItem
import eu.depau.loak.ui.screens.album.viewmodels.AlbumListViewModel
import eu.depau.loak.ui.screens.home.HomeFeed
import eu.depau.loak.ui.screens.home.viewmodels.HomeViewModel
import eu.depau.loak.ui.screens.library.components.libraryScreenOverviewButton
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration

/** A genre's page: Home's feed for that genre, then its albums and links to all of it. */
@Composable
fun GenreDetailScreen(
	genreName: String
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()

	val viewModel = koinViewModel<HomeViewModel>(
		key = "genre_feed_$genreName",
		parameters = { parametersOf(genreName) }
	)
	val state by viewModel.state.collectAsStateWithLifecycle()

	val albumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "genre_detail_albums_$genreName",
		parameters = { parametersOf(DomainAlbumListType.ByGenre(genreName)) }
	)
	val albumsState by albumsViewModel.albumsState.collectAsStateWithLifecycle()
	val selectedAlbum by albumsViewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by albumsViewModel.starred.collectAsStateWithLifecycle()
	val selectedAlbumRating by albumsViewModel.rating.collectAsStateWithLifecycle()

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	Scaffold(
		topBar = { NestedTopBar({ Text(genreName) }) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (preferVisible) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = !state.loading,
			onRefresh = {
				viewModel.refresh()
				albumsViewModel.refreshAlbums(true)
			},
			key = state.loading
		) {
			HomeFeed(
				viewModel = viewModel,
				gridState = rememberLazyGridState(),
				scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
				innerPadding = innerPadding
			) {
				horizontalSection(
					title = Res.string.title_albums,
					destination = Screen.AlbumList(true, DomainAlbumListType.ByGenre(genreName)),
					state = albumsState,
					key = { it.id },
					seeAll = true
				) { album ->
					AlbumListScreenGridItem(
						modifier = Modifier.width(150.dp),
						tab = "genre",
						album = album,
						selected = album == selectedAlbum,
						starred = selectedAlbumIsStarred,
						onSelect = { albumsViewModel.selectAlbum(album) },
						onDeselect = { albumsViewModel.clearSelection() },
						onSetStarred = { albumsViewModel.starAlbum(it) },
						onSetShareId = { shareId = it },
						onPlayNext = { player.playNext(album as DomainSongCollection) },
						onAddToQueue = { player.addToQueue(album as DomainSongCollection) },
						rating = selectedAlbumRating,
						onSetRating = { albumsViewModel.setRating(it) }
					)
				}
				libraryScreenOverviewButton(
					icon = Icons.Outlined.Note,
					label = Res.string.title_songs,
					destination = Screen.SongList(true, DomainSongListType.ByGenre(genreName)),
					start = true
				)
				libraryScreenOverviewButton(
					icon = Icons.Outlined.Album,
					label = Res.string.title_albums,
					destination = Screen.AlbumList(true, DomainAlbumListType.ByGenre(genreName)),
					start = false
				)
			}
		}
	}

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)
}
