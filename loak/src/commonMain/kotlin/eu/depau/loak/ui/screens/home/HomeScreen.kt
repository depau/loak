package eu.depau.loak.ui.screens.home

import eu.depau.loak.ui.util.verticalWheelToParent
import eu.depau.loak.ui.util.pageBy
import eu.depau.loak.ui.util.HorizontalScrollArrows
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isExpanded
import eu.depau.loak.di.isLandscape
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainArtistListType
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_play_all
import eu.depau.loak.generated.resources.option_sort_frequent
import eu.depau.loak.generated.resources.option_sort_newest
import eu.depau.loak.generated.resources.option_sort_random
import eu.depau.loak.generated.resources.option_sort_starred
import eu.depau.loak.generated.resources.title_forgotten_favourites
import eu.depau.loak.generated.resources.title_genres
import eu.depau.loak.generated.resources.title_home
import eu.depau.loak.generated.resources.title_made_for_you
import eu.depau.loak.generated.resources.title_mixed_for_you
import eu.depau.loak.generated.resources.title_playing_on_server
import eu.depau.loak.generated.resources.title_quick_picks
import eu.depau.loak.generated.resources.title_similar_to
import eu.depau.loak.generated.resources.title_speed_dial
import eu.depau.loak.generated.resources.title_your_library
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Genre
import eu.depau.loak.icons.outlined.History
import eu.depau.loak.icons.outlined.Shuffle
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.dialogs.DeletionDialog
import eu.depau.loak.ui.components.dialogs.DeletionEndpoint
import eu.depau.loak.ui.components.layouts.PullToRefreshBox
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.RootTopBar
import eu.depau.loak.ui.components.layouts.horizontalSection
import eu.depau.loak.ui.components.sheets.SongSheet
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.PersistentViewModelStoreOwner
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.album.components.AlbumListScreenGridItem
import eu.depau.loak.ui.screens.album.viewmodels.AlbumListViewModel
import eu.depau.loak.ui.screens.artist.ArtistListScreenGridItem
import eu.depau.loak.ui.screens.artist.viewmodels.ArtistListViewModel
import eu.depau.loak.ui.screens.home.components.GenreChips
import eu.depau.loak.ui.screens.home.components.ListenerRow
import eu.depau.loak.ui.screens.home.components.MixCard
import eu.depau.loak.ui.screens.home.components.ShelfHeader
import eu.depau.loak.ui.screens.home.components.SmallOutlinedButton
import eu.depau.loak.ui.screens.home.components.SonicJourneyCard
import eu.depau.loak.ui.screens.home.components.SongColumns
import eu.depau.loak.ui.screens.home.components.SpeedDial
import eu.depau.loak.ui.screens.home.components.SpeedDialLayout
import eu.depau.loak.ui.screens.home.components.TAB
import eu.depau.loak.ui.screens.home.viewmodels.HomeViewModel
import eu.depau.loak.ui.screens.library.components.libraryScreenOverviewButton
import eu.depau.loak.ui.screens.playlist.components.PlaylistListScreenGridItem
import eu.depau.loak.ui.screens.playlist.viewmodels.PlaylistListViewModel
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import eu.depau.loak.ui.util.withoutTop
import eu.depau.loak.ui.viewmodel.RootViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Duration

@Composable
fun HomeScreen() {
	val persistentViewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>()
	val viewModel = koinViewModel<HomeViewModel>(viewModelStoreOwner = persistentViewModelStoreOwner)
	val state by viewModel.state.collectAsStateWithLifecycle()
	val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
	val gridState = rememberLazyGridState()

	val rootViewModel = koinInject<RootViewModel>()
	LaunchedEffect(Unit) {
		rootViewModel.events.collect { event ->
			if (event is RootViewModel.Event.ScrollToTop) gridState.animateScrollToItem(0)
		}
	}

	Scaffold(
		topBar = { RootTopBar({ Text(stringResource(Res.string.title_home)) }, scrollBehavior) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			RootBottomBar(scrolled = scrollManager.isTriggered)
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = !state.loading,
			onRefresh = { viewModel.refresh() },
			key = state.loading
		) {
			HomeFeed(
				viewModel = viewModel,
				gridState = gridState,
				scrollBehavior = scrollBehavior,
				innerPadding = innerPadding
			)
		}
	}
}

/**
 * Home's shelves. Also a genre's page, with that genre's [HomeViewModel] and its albums in
 * [footer].
 */
@Composable
fun HomeFeed(
	viewModel: HomeViewModel,
	gridState: LazyGridState,
	scrollBehavior: TopAppBarScrollBehavior,
	innerPadding: PaddingValues,
	footer: (LazyGridScope.() -> Unit)? = null
) {
	val state by viewModel.state.collectAsStateWithLifecycle()
	val selectedGenre by viewModel.selectedGenre.collectAsStateWithLifecycle()
	val player = koinInject<MediaPlayerViewModel>()
	val persistentViewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>()

	// the cards' options menus, as on the library's screens
	val albumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "homeAlbums",
		parameters = { parametersOf(DomainAlbumListType.Recent) },
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val selectedAlbum by albumsViewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by albumsViewModel.starred.collectAsStateWithLifecycle()
	val selectedAlbumRating by albumsViewModel.rating.collectAsStateWithLifecycle()
	val playlistsViewModel = koinViewModel<PlaylistListViewModel>(
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val selectedPlaylist by playlistsViewModel.selectedPlaylist.collectAsStateWithLifecycle()
	val artistsViewModel = koinViewModel<ArtistListViewModel>(
		key = "homeArtists",
		parameters = { parametersOf(DomainArtistListType.AlphabeticalByName) },
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val selectedArtist by artistsViewModel.selectedArtist.collectAsStateWithLifecycle()
	val selectedArtistAlbums by artistsViewModel.selectedArtistAlbums.collectAsStateWithLifecycle()
	val selectedArtistIsStarred by artistsViewModel.starred.collectAsStateWithLifecycle()

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var playlistDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
	var listenerSong by remember { mutableStateOf<DomainSong?>(null) }

	LifecycleResumeEffect(Unit) {
		viewModel.onShown()
		onPauseOrDispose { }
	}

	val platformContext = LocalPlatformContext.current
	val expanded = platformContext.isExpanded()
	val medium = platformContext.isLandscape() && !expanded
	val cardWidth = if (expanded) 176.dp else 150.dp
	val columns = if (expanded) 4 else 2
	val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }

	@Composable
	fun AlbumCard(album: DomainAlbum) = AlbumListScreenGridItem(
		modifier = Modifier.width(cardWidth),
		tab = TAB,
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

	LazyVerticalGrid(
		modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		columns = GridCells.Fixed(columns),
		contentPadding = innerPadding.withoutTop() + PaddingValues(top = 8.dp, bottom = 16.dp),
		verticalArrangement = Arrangement.spacedBy(5.dp),
		horizontalArrangement = Arrangement.spacedBy(5.dp),
		state = gridState
	) {
		if (!state.ready) return@LazyVerticalGrid
		if (state.genres.isNotEmpty()) item(key = "genres", span = full) {
			GenreChips(state.genres, selectedGenre, viewModel::selectGenre)
		}

		if (state.speedDial.isNotEmpty()) {
			item(key = "speed dial header", span = full) {
				ShelfHeader(stringResource(Res.string.title_speed_dial))
			}
			item(key = "speed dial", span = full) {
				SpeedDial(
					items = state.speedDial,
					layout = when {
						expanded -> SpeedDialLayout.Row
						medium -> SpeedDialLayout.GridPeek
						else -> SpeedDialLayout.Grid
					},
					onPlayRadio = { viewModel.playRadio(it.song) },
					onFeelingLucky = viewModel::feelingLucky
				)
			}
		}

		if (state.quickPicks.isNotEmpty()) {
			item(key = "quick picks header", span = full) {
				ShelfHeader(stringResource(Res.string.title_quick_picks)) {
					SmallOutlinedButton(stringResource(Res.string.action_play_all)) { viewModel.playQuickPicks() }
				}
			}
			item(key = "quick picks", span = full) {
				SongColumns(
					songs = state.quickPicks,
					viewModel = viewModel,
					columnWidth = if (expanded) 520 else if (medium) 300 else null,
					onSetShareId = { shareId = it },
					// a song plays its radio; Play all plays the list
					onPlay = { viewModel.playRadio(state.quickPicks[it]) }
				)
			}
		}

		horizontalSection(
			title = Res.string.title_mixed_for_you,
			destination = Screen.ArtistList(true),
			state = UiState.Success(state.mixArtists),
			key = { it.id },
			seeAll = false
		) { artist -> MixCard(artist, cardWidth) { viewModel.playMix(artist) } }

		horizontalSection(
			title = Res.string.title_made_for_you,
			destination = Screen.PlaylistList(true),
			state = UiState.Success(state.madeForYou),
			key = { it.id },
			seeAll = false
		) { playlist ->
			PlaylistListScreenGridItem(
				modifier = Modifier.width(cardWidth),
				tab = TAB,
				playlist = playlist,
				selected = playlist.id == selectedPlaylist?.id,
				onSelect = { playlistsViewModel.selectPlaylist(playlist) },
				onDeselect = { playlistsViewModel.clearSelection() },
				onSetDeletionId = { playlistDeletionId = it },
				onSetShareId = { shareId = it },
				onPlayNext = { player.playNext(playlist as DomainSongCollection) },
				onAddToQueue = { player.addToQueue(playlist as DomainSongCollection) }
			)
		}

		state.sonicJourney?.let { (from, to) ->
			item(key = "sonic journey", span = full) {
				SonicJourneyCard(from, to, viewModel::playSonicJourney)
			}
		}

		state.similarTo?.let { (seed, similar) ->
			item(key = "similar header", span = full) {
				ShelfHeader(
					stringResource(Res.string.title_similar_to, seed.name),
					leading = { CoverArt(coverArtId = seed.coverArtId, modifier = Modifier.size(40.dp)) }
				)
			}
			item(key = "similar", span = full) {
				val rowState = rememberLazyListState()
				HorizontalScrollArrows(
					canScrollBackward = rowState.canScrollBackward,
					canScrollForward = rowState.canScrollForward,
					onBackward = { rowState.pageBy(-1) },
					onForward = { rowState.pageBy(1) }
				) {
					LazyRow(
						modifier = Modifier.verticalWheelToParent(),
						state = rowState,
						horizontalArrangement = Arrangement.spacedBy(12.dp),
						contentPadding = PaddingValues(horizontal = 16.dp)
					) {
						items(similar, key = { it.id }) { artist ->
							ArtistListScreenGridItem(
								modifier = Modifier.width(cardWidth),
								tab = TAB,
								artist = artist,
								selected = artist == selectedArtist,
								selectedArtistAlbums = selectedArtistAlbums,
								starred = selectedArtistIsStarred,
								onSelect = { artistsViewModel.selectArtist(artist) },
								onDeselect = { artistsViewModel.clearSelection() },
								onSetStarred = { artistsViewModel.starArtist(it) },
								onPlayNext = { artistsViewModel.playArtistAlbumsNext(player) },
								onAddToQueue = { artistsViewModel.addArtistAlbumsToQueue(player) }
							)
						}
					}
				}
			}
		}

		horizontalSection(
			title = Res.string.option_sort_newest,
			destination = Screen.AlbumList(true, DomainAlbumListType.Newest),
			state = UiState.Success(state.recentlyAdded),
			key = { it.id },
			seeAll = true
		) { album -> AlbumCard(album) }

		horizontalSection(
			title = Res.string.title_forgotten_favourites,
			destination = Screen.AlbumList(true, DomainAlbumListType.Frequent),
			state = UiState.Success(state.forgotten),
			key = { it.id },
			seeAll = false
		) { album -> AlbumCard(album) }

		if (state.nowPlaying.isNotEmpty()) {
			item(key = "now playing header", span = full) {
				ShelfHeader(stringResource(Res.string.title_playing_on_server))
			}
			state.nowPlaying.forEach { listener ->
				item(key = "listener ${listener.username} ${listener.song.id}", span = full) {
					ListenerRow(
						listener = listener,
						onClick = { viewModel.playRadio(listener.song) },
						onLongClick = { listenerSong = listener.song }
					)
				}
			}
		}

		// on a genre's page the library shortcuts give way to its albums
		if (footer != null) footer() else yourLibrary(columns)
	}

	listenerSong?.let { song ->
		SongSheet(
			onDismissRequest = { listenerSong = null },
			song = song,
			onPlayNext = { player.playNextSingle(song) },
			onAddToQueue = { player.addToQueueSingle(song) }
		)
	}

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)

	DeletionDialog(
		endpoint = DeletionEndpoint.PLAYLIST,
		id = playlistDeletionId,
		onIdClear = { playlistDeletionId = null },
		onRefresh = { playlistsViewModel.refreshPlaylists(false) }
	)
}

/** The library's shortcuts, at the end of Home. */
private fun LazyGridScope.yourLibrary(columns: Int) {
	item(key = "library header", span = { GridItemSpan(maxLineSpan) }) {
		ShelfHeader(stringResource(Res.string.title_your_library))
	}
	val buttons = listOf(
		Triple(Icons.Outlined.Star, Res.string.option_sort_starred, Screen.Starred()),
		Triple(Icons.Outlined.Shuffle, Res.string.option_sort_random, Screen.AlbumList(true, DomainAlbumListType.Random)),
		Triple(Icons.Outlined.History, Res.string.option_sort_frequent, Screen.AlbumList(true, DomainAlbumListType.Frequent)),
		Triple(Icons.Outlined.Genre, Res.string.title_genres, Screen.GenreList(true))
	)
	buttons.forEachIndexed { index, (icon, label, destination) ->
		libraryScreenOverviewButton(
			icon = icon,
			label = label,
			destination = destination,
			start = index % columns == 0,
			end = index % columns == columns - 1
		)
	}
}
