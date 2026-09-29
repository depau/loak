package eu.depau.loak.ui.screens.album

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.collections.immutable.toImmutableList
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.title_albums
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.settings.BottomBarVisibilityMode
import eu.depau.loak.domain.models.settings.ListViewMode
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.AlphabeticalScroller
import eu.depau.loak.ui.components.layouts.ArtGrid
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.PullToRefreshBox
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.RootTopBar
import eu.depau.loak.ui.components.snackbars.ErrorSnackBar
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.PersistentViewModelStoreOwner
import eu.depau.loak.ui.screens.album.components.AlbumListScreenSortButton
import eu.depau.loak.ui.screens.album.components.albumListScreenContent
import eu.depau.loak.ui.screens.album.viewmodels.AlbumListViewModel
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import eu.depau.loak.ui.util.withoutTop
import eu.depau.loak.ui.viewmodel.RootViewModel
import kotlin.time.Duration

@Composable
fun AlbumListScreen(
	nested: Boolean = false,
	listType: DomainAlbumListType
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val selectedViewMode = preferenceManager.albumListViewMode

	val viewModel = koinViewModel<AlbumListViewModel>(
		key = listType.toString(),
		parameters = { parametersOf(listType) },
		viewModelStoreOwner = if (nested) {
			LocalViewModelStoreOwner.current!!
		} else {
			koinInject<PersistentViewModelStoreOwner>()
		}
	)
	val player = koinInject<MediaPlayerViewModel>()
	val selectedSorting by viewModel.listType.collectAsStateWithLifecycle()
	val selectedReversed by viewModel.selectedReversed.collectAsStateWithLifecycle()
	val selectedFilters by viewModel.selectedFilters.collectAsStateWithLifecycle()
	val albumsState by viewModel.albumsState.collectAsStateWithLifecycle()
	val selectedAlbum by viewModel.selectedAlbum.collectAsStateWithLifecycle()
	val starred by viewModel.starred.collectAsStateWithLifecycle()
	val rating by viewModel.rating.collectAsStateWithLifecycle()
	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

	val actions: @Composable RowScope.() -> Unit = {
		AlbumListScreenSortButton(
			nested = nested,
			selectedSorting = selectedSorting,
			onSetSorting = { viewModel.setListType(it) },
			selectedReversed = selectedReversed,
			onSetReversed = { viewModel.setReversed(it) },
			selectedViewMode = selectedViewMode,
			onSetViewMode = { preferenceManager.albumListViewMode = it },
			selectedFilters = selectedFilters,
			onToggleFilter = { viewModel.toggleFilter(it) }
		)
	}

	val rootViewModel = koinViewModel<RootViewModel>()
	LaunchedEffect(Unit) {
		rootViewModel.events.collect { event ->
			if (event is RootViewModel.Event.ScrollToTop) {
				viewModel.gridState.animateScrollToItem(0)
			}
		}
	}

	Scaffold(
		topBar = {
			if (!nested) {
				RootTopBar(
					{ Text(stringResource(Res.string.title_albums)) },
					scrollBehavior,
					actions
				)
			} else {
				NestedTopBar({ Text(stringResource(Res.string.title_albums)) }, actions)
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!nested || preferVisible) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = albumsState !is UiState.Loading,
			onRefresh = { viewModel.refreshAlbums(true) },
			key = albumsState
		) {
			val grouped = remember(albumsState.data) {
				albumsState.data.orEmpty().groupBy { it.name?.firstOrNull()?.uppercaseChar() ?: '#' }
					.toList()
					.sortedBy { it.first }
			}

			val headerIndices = remember(grouped) {
				var currentIndex = 0
				grouped.map { (letter, albums) ->
					val pos = currentIndex
					currentIndex += albums.size + 1
					letter.toString() to pos
				}.toImmutableList()
			}

			Box {
				ArtGrid(
					modifier = if (!nested)
						Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
					else Modifier,
					state = viewModel.gridState,
					contentPadding = innerPadding.withoutTop(),
					verticalArrangement = if (albumsState.data?.isEmpty() == true) {
						Arrangement.Center
					} else if (selectedViewMode == ListViewMode.List) {
						Arrangement.spacedBy(0.dp)
					} else {
						Arrangement.spacedBy(12.dp)
					},
					selectedViewMode = selectedViewMode
				) {
					albumListScreenContent(
						state = albumsState,
						starred = starred,
						selectedSorting = selectedSorting,
						selectedAlbum = selectedAlbum,
						selectedAlbumRating = rating,
						selectedViewMode = selectedViewMode,
						onPlayNext = { if (selectedAlbum != null) player.playNext(selectedAlbum as DomainSongCollection) },
						onAddToQueue = { if (selectedAlbum != null) player.addToQueue(selectedAlbum as DomainSongCollection) },
						onUpdateSelection = { viewModel.selectAlbum(it) },
						onClearSelection = { viewModel.clearSelection() },
						onSetShareId = { newShareId ->
							shareId = newShareId
						},
						onSetStarred = { viewModel.starAlbum(it) },
						onRateSelectedAlbum = { viewModel.setRating(it) }
					)
				}
				if (selectedSorting == DomainAlbumListType.AlphabeticalByName) {
					AlphabeticalScroller(
						state = viewModel.gridState,
						headers = headerIndices,
						modifier = Modifier.align(Alignment.TopEnd)
					)
				}
			}
		}
	}

	ErrorSnackBar(
		error = (albumsState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)
}
