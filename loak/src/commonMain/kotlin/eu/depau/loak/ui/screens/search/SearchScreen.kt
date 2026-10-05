package eu.depau.loak.ui.screens.search

import eu.depau.loak.generated.resources.filter_sound
import eu.depau.loak.generated.resources.action_see_all
import eu.depau.loak.generated.resources.info_ask_ai_row
import eu.depau.loak.generated.resources.action_ask_ai_playlist
import eu.depau.loak.generated.resources.info_by_sound_private
import eu.depau.loak.generated.resources.info_by_sound
import eu.depau.loak.generated.resources.title_sounds_like
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.clip
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.icons.filled.Sparkle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.insert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_to_queue
import eu.depau.loak.generated.resources.action_remove_from_history
import eu.depau.loak.generated.resources.action_search_history
import eu.depau.loak.generated.resources.info_explicit
import eu.depau.loak.generated.resources.info_no_search_results
import eu.depau.loak.generated.resources.info_not_available_offline
import eu.depau.loak.generated.resources.title_albums
import eu.depau.loak.generated.resources.title_all
import eu.depau.loak.generated.resources.title_artists
import eu.depau.loak.generated.resources.title_playlists
import eu.depau.loak.generated.resources.title_songs
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainArtistListType
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.settings.BottomBarVisibilityMode
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.domain.models.settings.ListViewMode
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.History
import eu.depau.loak.icons.outlined.Lock
import eu.depau.loak.icons.outlined.NoSearchResults
import eu.depau.loak.icons.outlined.Offline
import eu.depau.loak.icons.outlined.Queue
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.playOrExplain
import eu.depau.loak.ui.components.common.unavailable
import eu.depau.loak.ui.components.common.ErrorBox
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.layouts.ArtGrid
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.artGridPlaceholder
import eu.depau.loak.ui.components.layouts.horizontalSection
import eu.depau.loak.ui.components.sheets.SongSheet
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.PersistentViewModelStoreOwner
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.album.components.AlbumListScreenGridItem
import eu.depau.loak.ui.screens.album.viewmodels.AlbumListViewModel
import eu.depau.loak.ui.screens.artist.ArtistListScreenGridItem
import eu.depau.loak.ui.screens.artist.viewmodels.ArtistListViewModel
import eu.depau.loak.ui.screens.playlist.components.PlaylistListScreenGridItem
import eu.depau.loak.ui.screens.playlist.viewmodels.PlaylistListViewModel
import androidx.compose.runtime.saveable.rememberSaveable
import eu.depau.loak.ui.components.dialogs.DeletionDialog
import eu.depau.loak.ui.components.dialogs.DeletionEndpoint
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import kotlin.time.Duration
import kotlinx.serialization.Serializable
import eu.depau.loak.ui.screens.search.components.SearchScreenChips
import eu.depau.loak.ui.screens.search.components.SearchScreenTopBar
import eu.depau.loak.ui.screens.search.viewmodels.SearchViewModel
import eu.depau.loak.ui.util.buildSongInfoString
import eu.depau.loak.ui.viewmodel.RootViewModel
import eu.depau.loak.ui.util.loakAnimateItem
import eu.depau.loak.ui.util.onSecondaryClick
import androidx.compose.foundation.layout.add
import eu.depau.loak.ui.util.windowControlsInsets
import eu.depau.loak.ui.util.windowDragArea

@Serializable
enum class SearchCategory(val res: StringResource) {
	ALL(Res.string.title_all),
	SONGS(Res.string.title_songs),
	PLAYLISTS(Res.string.title_playlists),
	ARTISTS(Res.string.title_artists),
	ALBUMS(Res.string.title_albums),
	SOUND(Res.string.filter_sound)
}

// TODO: clean this up, holy shit
@Composable
fun SearchScreen(
	nested: Boolean,
	initialCategory: SearchCategory
) {
	val preferenceManager = koinInject<PreferenceManager>()

	val viewModel = koinViewModel<SearchViewModel>(
		viewModelStoreOwner = if (nested) {
			LocalViewModelStoreOwner.current!!
		} else {
			koinInject<PersistentViewModelStoreOwner>()
		}
	)
	val selectedSong by viewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSongIsStarred by viewModel.selectedSongIsStarred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()

	val artistListViewModel = koinViewModel<ArtistListViewModel> {
		parametersOf(DomainArtistListType.AlphabeticalByName)
	}
	val artistListSelection by artistListViewModel.selectedArtist.collectAsState()
	val artistListSelectionAlbums by artistListViewModel.selectedArtistAlbums.collectAsState()
	val artistListStarred by artistListViewModel.starred.collectAsState()

	val albumListViewModel = koinViewModel<AlbumListViewModel> {
		parametersOf(DomainAlbumListType.AlphabeticalByName)
	}
	val albumListSelection by albumListViewModel.selectedAlbum.collectAsState()
	val playlistListViewModel = koinViewModel<PlaylistListViewModel>()
	val playlistListSelection by playlistListViewModel.selectedPlaylist.collectAsState()
	val albumListStarred by albumListViewModel.starred.collectAsState()
	val selectedAlbumRating by albumListViewModel.rating.collectAsStateWithLifecycle()

	val query = viewModel.searchQuery
	val state by viewModel.searchState.collectAsState()
	val searchHistory by viewModel.searchHistory.collectAsState(initial = emptyList())
	val downloadedSongs by viewModel.downloadedSongs.collectAsState()

	val player = koinInject<MediaPlayerViewModel>()
	val backStack = LocalNavStack.current
	val soundState by viewModel.soundState.collectAsState()
	val audioMuseInfo by viewModel.audioMuseInfo.collectAsState()
	val audioMusePrefs = koinInject<PreferenceManager>()

	val selectedCategory by viewModel.selectedCategory.collectAsState()
	LaunchedEffect(initialCategory) {
		viewModel.selectedCategory.value = initialCategory
	}
	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	var playlistDeletionId by rememberSaveable { mutableStateOf<String?>(null) }

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
			Column(
				modifier = Modifier
					.windowDragArea()
					.background(MaterialTheme.colorScheme.surface)
					.padding(
						TopAppBarDefaults.windowInsets.add(windowControlsInsets()).asPaddingValues()
					)
			) {
				SearchScreenTopBar(
					query = query,
					nested = nested,
					onSearch = { submittedQuery ->
						viewModel.addToSearchHistory(submittedQuery)
						viewModel.searchBySound(submittedQuery)
					}
				)
				SearchScreenChips(
					selectedCategory = selectedCategory,
					onCategorySelect = {
						viewModel.selectedCategory.value = it
						if (it == SearchCategory.SOUND) viewModel.searchBySound()
					},
					soundAvailable = audioMuseInfo?.soundSearch == true && audioMusePrefs.audioMuseDescribe
				)
			}
		},
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!nested || preferVisible) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { contentPadding ->
		AnimatedContent(
			state,
			modifier = Modifier.fillMaxSize()
		) { uiState ->
			when (uiState) {
				is UiState.Loading -> ArtGrid(
					contentPadding = contentPadding,
					selectedViewMode = ListViewMode.List
				) {
					artGridPlaceholder(viewMode = ListViewMode.List)
				}
				is UiState.Error -> ErrorBox(uiState, padding = contentPadding)
				is UiState.Success -> {
					val results = uiState.data
					val showAll = selectedCategory == SearchCategory.ALL
					val albums =
						if (showAll || selectedCategory == SearchCategory.ALBUMS) results.filterIsInstance<DomainAlbum>() else emptyList()
					val artists =
						if (showAll || selectedCategory == SearchCategory.ARTISTS) results.filterIsInstance<DomainArtist>() else emptyList()
					val songs =
						if (showAll || selectedCategory == SearchCategory.SONGS) results.filterIsInstance<DomainSong>() else emptyList()
					val playlists =
						if (showAll || selectedCategory == SearchCategory.PLAYLISTS) results.filterIsInstance<DomainPlaylist>() else emptyList()

					val soundSongs = if (showAll || selectedCategory == SearchCategory.SOUND) soundState?.data.orEmpty() else emptyList()
					if (query.text.isNotBlank() && albums.isEmpty() && artists.isEmpty() && songs.isEmpty() && playlists.isEmpty()
						&& soundSongs.isEmpty() && selectedCategory != SearchCategory.SOUND) {
						ContentUnavailable(
							icon = Icons.Outlined.NoSearchResults,
							label = stringResource(Res.string.info_no_search_results)
						)
					}

					LazyVerticalGrid(
						modifier = Modifier.fillMaxSize(),
						columns = GridCells.Fixed(2),
						contentPadding = contentPadding,
						state = viewModel.gridState,
						verticalArrangement = Arrangement.spacedBy(8.dp)
					) {
						if (query.text.isNotBlank()) {
							if (songs.isNotEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									Text(
										stringResource(Res.string.title_songs),
										style = MaterialTheme.typography.headlineSmall,
										modifier = Modifier.padding(
											horizontal = 16.dp,
											vertical = 8.dp
										)
									)
								}
								items(
									songs.take(10).size,
									span = { GridItemSpan(maxLineSpan) }) { index ->
									val song = songs[index]
									val isExplicit = song.explicitStatus == DomainExplicitStatus.Explicit
										&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
									val maybeUnavailable = !LocalAvailability.current.song(song.id)

									val dismissState = rememberSwipeToDismissBoxState()

									LaunchedEffect(dismissState.currentValue) {
										if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
											player.addToQueueSingle(song)
											dismissState.snapTo(SwipeToDismissBoxValue.Settled)
										}
									}

									SwipeToDismissBox(
										state = dismissState,
										enableDismissFromStartToEnd = false,
										enableDismissFromEndToStart = true,
										backgroundContent = {
											val backgroundColor by animateColorAsState(
												targetValue = when (dismissState.targetValue) {
													SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
													else -> Color.Transparent
												}
											)
											val iconColor by animateColorAsState(
												targetValue = when (dismissState.targetValue) {
													SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onPrimaryContainer
													else -> MaterialTheme.colorScheme.onSurfaceVariant
												}
											)

											Box(
												modifier = Modifier
													.fillMaxSize()
													.background(color = backgroundColor)
													.padding(horizontal = 20.dp),
												contentAlignment = Alignment.CenterEnd
											) {
												Icon(
													imageVector = Icons.Outlined.Queue,
													contentDescription = stringResource(Res.string.action_add_to_queue),
													tint = iconColor
												)
											}
										}
									) {
										ListItem(
											modifier = Modifier
												.background(MaterialTheme.colorScheme.surface)
												.unavailable(maybeUnavailable)
												.onSecondaryClick { viewModel.selectSong(song) },
											onClick = playOrExplain(song.id) { player.playNow(song) },
											onLongClick = { viewModel.selectSong(song) },
											content = { Text(song.title) },
											supportingContent = {
												MarqueeText(
													buildSongInfoString(
														song = song,
														onClickArtist = { backStack.add(Screen.ArtistDetail(it)) }
													)
												)
											},
											leadingContent = {
												CoverArt(
													coverArtId = song.coverArtId,
													modifier = Modifier.size(50.dp),
													shape = preferenceManager.coverArtShape.decreasedShape
												)
											},
											trailingContent = {
												if (isExplicit) {
													Icon(
														Icons.Outlined.Lock,
														stringResource(Res.string.info_explicit),
														modifier = Modifier.size(20.dp)
													)
												}
												if (maybeUnavailable) {
													Icon(
														Icons.Outlined.Offline,
														stringResource(Res.string.info_not_available_offline),
														modifier = Modifier.size(20.dp)
													)
												}
											}
										)
										if (selectedSong == song) {
											SongSheet(
												onDismissRequest = { viewModel.clearSelectedSong() },
												song = song,
												onPlayNext = {
													player.playNextSingle(song)
												},
												onAddToQueue = {
													player.addToQueueSingle(song)
												},
												downloadStatus = if (downloadedSongs.containsKey(
														song.id
													)
												) DownloadStatus.DOWNLOADED else null,
												onTrackInfo = dropUnlessResumed {
													backStack.add(Screen.SongDetailScreen(song.id, song.coverArtId))
												},
												onViewAlbum = song.albumId?.let { albumId ->
													dropUnlessResumed {
														backStack.add(
															Screen.CollectionDetail(
																collectionId = albumId,
																tab = "search"
															)
														)
													}
												},
												starred = selectedSongIsStarred,
												onSetStarred = { viewModel.starSelectedSong(it) },
												rating = selectedSongRating,
												onSetRating = { viewModel.rateSelectedSong(it) }
											)
										}
									}
								}
							}

							horizontalSection(
								title = Res.string.title_playlists,
								destination = Screen.PlaylistList(true),
								state = UiState.Success(playlists),
								key = { it.id },
								seeAll = false
							) { playlist ->
								PlaylistListScreenGridItem(
									modifier = loakAnimateItem(fadeInSpec = null)
										.width(150.dp),
									tab = "search",
									playlist = playlist,
									selected = playlist == playlistListSelection,
									onSelect = { playlistListViewModel.selectPlaylist(playlist) },
									onDeselect = { playlistListViewModel.clearSelection() },
									onSetShareId = { shareId = it },
									onSetDeletionId = { playlistDeletionId = it },
									onPlayNext = { player.playNext(playlist as DomainSongCollection) },
									onAddToQueue = { player.addToQueue(playlist as DomainSongCollection) }
								)
							}

							horizontalSection(
								title = Res.string.title_artists,
								destination = Screen.ArtistList(true),
								state = UiState.Success(artists),
								key = { it.id },
								seeAll = false
							) { artist ->
								ArtistListScreenGridItem(
									modifier = loakAnimateItem(fadeInSpec = null)
										.width(150.dp),
									tab = "search",
									artist = artist,
									selected = artist == artistListSelection,
									selectedArtistAlbums = artistListSelectionAlbums,
									starred = artistListStarred,
									onSelect = { artistListViewModel.selectArtist(artist) },
									onDeselect = { artistListViewModel.clearSelection() },
									onSetStarred = { artistListViewModel.starArtist(it) },
									onPlayNext = { artistListViewModel.playArtistAlbumsNext(player) },
									onAddToQueue = {
										artistListViewModel.addArtistAlbumsToQueue(
											player
										)
									}
								)
							}
							horizontalSection(
								title = Res.string.title_albums,
								destination = Screen.AlbumList(true),
								state = UiState.Success(albums),
								key = { it.id },
								seeAll = false
							) { album ->
								AlbumListScreenGridItem(
									modifier = loakAnimateItem(fadeInSpec = null)
										.width(150.dp),
									tab = "search",
									album = album,
									selected = album == albumListSelection,
									starred = albumListStarred,
									onSelect = { albumListViewModel.selectAlbum(album) },
									onDeselect = { albumListViewModel.clearSelection() },
									onSetStarred = { albumListViewModel.starAlbum(it) },
									onSetShareId = { shareId = it },
									onPlayNext = { player.playNext(album as DomainSongCollection) },
									onAddToQueue = { player.addToQueue(album as DomainSongCollection) },
									rating = selectedAlbumRating,
									onSetRating = { albumListViewModel.setRating(it) }
								)
							}
							// AudioMuse-AI: songs that sound like the query, filled on submit
							if (soundSongs.isNotEmpty() || (selectedCategory == SearchCategory.SOUND && soundState is UiState.Loading)) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
										Row(verticalAlignment = Alignment.CenterVertically) {
											Text(
												stringResource(Res.string.title_sounds_like, query.text.toString().trim()),
												style = MaterialTheme.typography.headlineSmall,
												modifier = Modifier.weight(1f)
											)
											if (showAll && soundSongs.size > 5) TextButton(onClick = {
												viewModel.selectedCategory.value = SearchCategory.SOUND
											}) { Text(stringResource(Res.string.action_see_all)) }
										}
										Text(
											stringResource(
												if (showAll) Res.string.info_by_sound else Res.string.info_by_sound_private,
												query.text.toString().trim()
											),
											style = MaterialTheme.typography.bodySmall,
											color = MaterialTheme.colorScheme.onSurfaceVariant
										)
										if (soundState is UiState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
									}
								}
								val shown = if (showAll) soundSongs.take(5) else soundSongs
								items(shown.size, span = { GridItemSpan(maxLineSpan) }) { index ->
									val song = shown[index]
									ListItem(
										modifier = Modifier.clickable { player.playNow(soundSongs, soundSongs.indexOf(song)) },
										leadingContent = { CoverArt(coverArtId = song.coverArtId, modifier = Modifier.size(50.dp)) },
										headlineContent = { Text(song.title, maxLines = 1) },
										supportingContent = { Text(song.artistName.orEmpty(), maxLines = 1) }
									)
								}
							}

							// AudioMuse-AI's AI service: never automatic, a row that opens its screen
							if (showAll && audioMuseInfo?.canAsk == true && audioMusePrefs.audioMuseAskAi) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									ListItem(
										modifier = Modifier
											.padding(horizontal = 16.dp, vertical = 8.dp)
											.clip(MaterialTheme.shapes.large)
											.clickable { backStack.add(Screen.AskAI(query.text.toString().trim())) },
										colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
										leadingContent = { Icon(Icons.Filled.Sparkle, null) },
										headlineContent = { Text(stringResource(Res.string.action_ask_ai_playlist)) },
										supportingContent = { Text(stringResource(Res.string.info_ask_ai_row, query.text.toString().trim())) },
										trailingContent = { Icon(Icons.Outlined.ChevronForward, null) }
									)
								}
							}
						} else {
							if (searchHistory.isNotEmpty()) {
								item(span = { GridItemSpan(maxLineSpan) }) {
									Text(
										text = stringResource(Res.string.action_search_history),
										style = MaterialTheme.typography.titleMedium,
										color = MaterialTheme.colorScheme.primary,
										modifier = Modifier.padding(
											horizontal = 20.dp,
											vertical = 12.dp
										)
									)
								}
								items(
									searchHistory.size,
									span = { GridItemSpan(maxLineSpan) }) { index ->
									val historyItem = searchHistory[index]
									ListItem(
										modifier = Modifier.clickable {
											query.clearText()
											query.edit { insert(0, historyItem) }
										},
										headlineContent = { Text(historyItem) },
										leadingContent = {
											Icon(
												imageVector = Icons.Outlined.History,
												contentDescription = null,
												tint = MaterialTheme.colorScheme.onSurfaceVariant
											)
										},
										trailingContent = {
											IconButton(onClick = {
												viewModel.removeFromSearchHistory(historyItem)
											}) {
												Icon(
													imageVector = Icons.Outlined.Close,
													contentDescription = stringResource(Res.string.action_remove_from_history),
													tint = MaterialTheme.colorScheme.onSurfaceVariant
												)
											}
										}
									)
								}
							}
						}
					}
				}
			}
		}
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
		onRefresh = {
			playlistListViewModel.refreshPlaylists(false)
			viewModel.refresh()
		}
	)
}
