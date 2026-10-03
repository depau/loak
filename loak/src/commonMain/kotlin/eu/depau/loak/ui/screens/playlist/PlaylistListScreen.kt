package eu.depau.loak.ui.screens.playlist

import eu.depau.loak.ui.screens.playlist.components.PlaylistKindFilter
import eu.depau.loak.ui.screens.playlist.components.PlaylistKindFilterRow
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.parsePlaylistName
import eu.depau.loak.domain.models.PlaylistKind
import androidx.compose.foundation.lazy.grid.GridItemSpan
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.NavidromeManager
import androidx.compose.runtime.produceState
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Soundwave
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.generated.resources.action_smart_playlist
import eu.depau.loak.generated.resources.action_playlist
import eu.depau.loak.ui.screens.playlist.smart.SmartPlaylistEditor
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.title_create_playlist
import eu.depau.loak.generated.resources.title_playlists
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.settings.BottomBarCollapseMode
import eu.depau.loak.domain.models.settings.BottomBarVisibilityMode
import eu.depau.loak.domain.models.settings.ListViewMode
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.dialogs.DeletionDialog
import eu.depau.loak.ui.components.dialogs.DeletionEndpoint
import eu.depau.loak.ui.components.layouts.ArtGrid
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.PullToRefreshBox
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.RootTopBar
import eu.depau.loak.ui.components.layouts.rootTopBarScrollBehavior
import eu.depau.loak.ui.components.snackbars.ErrorSnackBar
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.PersistentViewModelStoreOwner
import eu.depau.loak.ui.screens.playlist.components.PlaylistListScreenSortButton
import eu.depau.loak.ui.screens.playlist.components.playlistListScreenContent
import eu.depau.loak.ui.screens.playlist.dialogs.PlaylistCreateDialog
import eu.depau.loak.ui.screens.playlist.viewmodels.PlaylistListViewModel
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import eu.depau.loak.ui.util.withoutTop
import eu.depau.loak.ui.viewmodel.RootViewModel
import kotlin.time.Duration

@Composable
fun PlaylistListScreen(
	nested: Boolean = false,
	initialKind: PlaylistKindFilter = PlaylistKindFilter.All
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val selectedViewMode = preferenceManager.playlistListViewMode

	val viewModel = koinViewModel<PlaylistListViewModel>(
		viewModelStoreOwner = if (nested) {
			LocalViewModelStoreOwner.current!!
		} else {
			koinInject<PersistentViewModelStoreOwner>()
		}
	)
	// show the cache at once, then pick up server changes
	LaunchedEffect(Unit) { viewModel.revalidate() }
	val player = koinInject<MediaPlayerViewModel>()
	val playlistsState by viewModel.playlistsState.collectAsState()
	val selectedPlaylist by viewModel.selectedPlaylist.collectAsState()
	val selectedSorting by viewModel.selectedSorting.collectAsStateWithLifecycle()
	val selectedReversed by viewModel.selectedReversed.collectAsStateWithLifecycle()
	val selectedFilters by viewModel.selectedFilters.collectAsStateWithLifecycle()

	val scrollManager = LocalBottomBarScrollManager.current

	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var deletionId by remember { mutableStateOf<String?>(null) }
	val scrollBehavior = rootTopBarScrollBehavior()

	val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
	val scaleInSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

	var createDialogShown by rememberSaveable { mutableStateOf(false) }
	var smartEditorShown by rememberSaveable { mutableStateOf(false) }
	val backStack = LocalNavStack.current
	var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
	val navidrome = koinInject<NavidromeManager>()
	val canMakeSmart by produceState(false) { value = navidrome.serverInfo().canEditSmartPlaylists }

	val gridState = rememberLazyGridState()

	// the kind chips: who made each playlist, and how
	var kindFilter by rememberSaveable { mutableStateOf(initialKind) }
	val radios by koinInject<AudioMuseManager>().radios.collectAsState()
	val radioNames = remember(radios) { radios.mapTo(HashSet()) { it.name } }
	val username = koinInject<SessionManager>().username
	val kinds = remember(playlistsState, radioNames, preferenceManager.audioMuseIntegration) {
		playlistsState.data.orEmpty().associate {
			it.id to parsePlaylistName(it.name, it.validUntil != null, preferenceManager.audioMuseIntegration, radioNames).kind
		}
	}
	val availableKinds = remember(kinds, username) {
		PlaylistKindFilter.entries.filter { f ->
			f == PlaylistKindFilter.All || playlistsState.data.orEmpty().any { f.matches(it, kinds[it.id] ?: PlaylistKind.Regular, username) }
		}
	}
	val shownState = remember(playlistsState, kindFilter, kinds) {
		val state = playlistsState
		if (kindFilter == PlaylistKindFilter.All || state !is UiState.Success) state
		else UiState.Success(state.data.filter { kindFilter.matches(it, kinds[it.id] ?: PlaylistKind.Regular, username) })
	}

	val actions: @Composable RowScope.() -> Unit = {
		PlaylistListScreenSortButton(
			nested = nested,
			selectedSorting = selectedSorting,
			onSetSorting = { viewModel.setSorting(it) },
			selectedReversed = selectedReversed,
			onSetReversed = { viewModel.setReversed(it) },
			selectedViewMode = selectedViewMode,
			onSetViewMode = { preferenceManager.playlistListViewMode = it },
			selectedFilters = selectedFilters,
			onToggleFilter = { viewModel.toggleFilter(it) }
		)
	}

	val rootViewModel = koinInject<RootViewModel>()
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
					title = { Text(stringResource(Res.string.title_playlists)) },
					scrollBehavior = scrollBehavior,
					actions = actions
				)
			} else {
				NestedTopBar(
					title = { Text(stringResource(Res.string.title_playlists)) },
					actions = actions
				)
			}
		},
		floatingActionButton = {
			AnimatedContent(
				!scrollManager.isTriggered
					|| preferenceManager.bottomBarCollapseMode == BottomBarCollapseMode.Never,
				transitionSpec = {
					val transformOrigin = TransformOrigin(0f, 1f)
					(slideInHorizontally(slideSpec) { it / 2 }
						+ scaleIn(scaleInSpec, transformOrigin = transformOrigin)
						+ slideInVertically(slideSpec) { it / 2 })
						.togetherWith(slideOutHorizontally(slideSpec) { it / 2 }
							+ scaleOut(transformOrigin = transformOrigin)
							+ slideOutVertically(slideSpec) { it / 2 })
						.using(SizeTransform(clip = false))
				}
			) { notScrolled ->
				if (notScrolled && canMakeSmart && preferenceManager.smartPlaylistsEnabled) {
					FloatingActionButtonMenu(
						expanded = fabMenuExpanded,
						button = {
							ToggleFloatingActionButton(
								checked = fabMenuExpanded,
								onCheckedChange = { fabMenuExpanded = it }
							) {
								Icon(
									if (checkedProgress > .5f) Icons.Outlined.Close else Icons.Outlined.Add,
									contentDescription = stringResource(Res.string.title_create_playlist),
									modifier = Modifier.animateIcon({ checkedProgress })
								)
							}
						}
					) {
						FloatingActionButtonMenuItem(
							onClick = {
								fabMenuExpanded = false
								smartEditorShown = true
							},
							icon = { Icon(Icons.Outlined.Soundwave, null) },
							text = { Text(stringResource(Res.string.action_smart_playlist)) }
						)
						FloatingActionButtonMenuItem(
							onClick = {
								fabMenuExpanded = false
								createDialogShown = true
							},
							icon = { Icon(Icons.Outlined.PlaylistAdd, null) },
							text = { Text(stringResource(Res.string.action_playlist)) }
						)
					}
				} else if (notScrolled) {
					MediumFloatingActionButton(
						shape = MaterialTheme.shapes.large,
						containerColor = MaterialTheme.colorScheme.primary,
						onClick = {
							createDialogShown = true
						}
					) {
						Icon(
							imageVector = Icons.Outlined.Add,
							contentDescription = stringResource(Res.string.title_create_playlist),
							modifier = Modifier.size(26.dp)
						)
					}
				}
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
			finished = playlistsState !is UiState.Loading,
			onRefresh = { viewModel.refreshPlaylists(true) },
			key = playlistsState
		) {
			ArtGrid(
				modifier = if (!nested)
					Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
				else Modifier,
				state = gridState,
				contentPadding = innerPadding.withoutTop(),
				verticalArrangement = if (playlistsState.data?.isEmpty() == true) {
					Arrangement.Center
				} else if (selectedViewMode == ListViewMode.List) {
					Arrangement.spacedBy(0.dp)
				} else {
					Arrangement.spacedBy(12.dp)
				},
				selectedViewMode = selectedViewMode
			) {
				if (availableKinds.size > 1) item(span = { GridItemSpan(maxLineSpan) }) {
					PlaylistKindFilterRow(availableKinds, kindFilter, { kindFilter = it })
				}
				playlistListScreenContent(
					state = shownState,
					selectedPlaylist = selectedPlaylist,
					selectedViewMode = selectedViewMode,
					onUpdateSelection = { viewModel.selectPlaylist(it) },
					onClearSelection = { viewModel.clearSelection() },
					onSetShareId = { newShareId ->
						shareId = newShareId
					},
					onSetDeletionId = { newDeletionId ->
						deletionId = newDeletionId
					},
					onPlayNext = { if (selectedPlaylist != null) player.playNext(selectedPlaylist as DomainSongCollection) },
					onAddToQueue = {
						if (selectedPlaylist != null) player.addToQueue(
							selectedPlaylist as DomainSongCollection
						)
					}
				)
			}
		}
	}

	ErrorSnackBar(
		error = (playlistsState as? UiState.Error)?.error,
		onClearError = { viewModel.clearError() }
	)

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	DeletionDialog(
		endpoint = DeletionEndpoint.PLAYLIST,
		id = deletionId,
		onIdClear = { deletionId = null },
		onRefresh = { viewModel.refreshPlaylists(false) }
	)

	if (smartEditorShown) {
		SmartPlaylistEditor(
			playlist = null,
			details = null,
			onDismissRequest = { smartEditorShown = false },
			onSaved = { id ->
				smartEditorShown = false
				backStack.add(Screen.CollectionDetail(id, ""))
			}
		)
	}

	if (createDialogShown) {
		PlaylistCreateDialog(
			onDismissRequest = { createDialogShown = false },
			onRefresh = { viewModel.refreshPlaylists(true) }
		)
	}
}
