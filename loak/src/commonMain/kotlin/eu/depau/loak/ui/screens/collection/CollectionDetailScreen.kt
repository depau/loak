package eu.depau.loak.ui.screens.collection

import eu.depau.loak.ui.screens.collection.components.RadioCard
import eu.depau.loak.domain.manager.SmartPlaylistDetails
import eu.depau.loak.ui.screens.playlist.smart.SmartPlaylistEditor
import eu.depau.loak.ui.screens.playlist.smart.SmartRulesCard
import eu.depau.loak.ui.screens.collection.components.PlaylistRebuiltNotice
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import eu.depau.loak.icons.outlined.Check
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.DragHandle
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.TopBarButton
import eu.depau.loak.ui.util.dragHandle
import eu.depau.loak.ui.util.draggableItemsIndexed
import eu.depau.loak.ui.util.rememberDraggableListState
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_save
import eu.depau.loak.generated.resources.action_reorder
import eu.depau.loak.generated.resources.title_reorder_playlist
import eu.depau.loak.generated.resources.info_reorder_hint
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import eu.depau.loak.generated.resources.info_no_matching_tracks
import eu.depau.loak.ui.screens.collection.components.PlaylistTrackFilterRow
import androidx.compose.foundation.lazy.LazyColumn
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.ui.components.dialogs.DeletionDialog
import eu.depau.loak.ui.components.dialogs.DeletionEndpoint
import eu.depau.loak.di.isExpanded
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.materialkolor.dynamiccolor.ColorSpec
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_no_songs
import eu.depau.loak.generated.resources.title_disc_number
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Album
import eu.depau.loak.icons.outlined.Note
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.components.layouts.PullToRefreshBox
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.snackbars.ErrorSnackBar
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.screens.collection.components.CollectionDetailScreenFooterRow
import eu.depau.loak.ui.screens.collection.components.CollectionDetailScreenHeadingRow
import eu.depau.loak.ui.screens.collection.components.CollectionDetailScreenHeadingRowButtons
import eu.depau.loak.ui.screens.collection.components.CollectionDetailScreenSongRow
import eu.depau.loak.ui.screens.collection.components.CollectionDetailScreenTopBar
import eu.depau.loak.ui.screens.collection.components.collectionDetailScreenMoreByArtistRow
import eu.depau.loak.ui.screens.collection.viewmodels.CollectionDetailViewModel
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import eu.depau.loak.ui.theme.LoakTheme
import eu.depau.loak.ui.util.rememberColorSchemeFromCoverArt
import eu.depau.loak.ui.util.withoutTop
import kotlin.time.Duration

@Composable
fun CollectionDetailScreen(
	collectionId: String,
	tab: String
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val expanded = LocalPlatformContext.current.isExpanded()

	val viewModel = koinViewModel<CollectionDetailViewModel>(
		key = collectionId,
		parameters = { parametersOf(collectionId) }
	)

	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val collectionState by viewModel.collectionState.collectAsState()
	val collection = collectionState.data
	val selectedAlbum by viewModel.selectedAlbum.collectAsState()
	val starred by viewModel.starred.collectAsState()
	val isReversed by viewModel.isReversed.collectAsState()
	val searchQuery = rememberTextFieldState()
	val visibleSongs by remember(collection?.songs, searchQuery.text, isReversed) {
		derivedStateOf {
			val songs = (collection as? DomainPlaylist)?.songs ?: return@derivedStateOf emptyList()
			val query = searchQuery.text.toString().trim()
			val filtered = if (query.isEmpty()) songs else songs.filter { song ->
				song.title.contains(query, ignoreCase = true) ||
					song.artistName?.contains(query, ignoreCase = true) == true ||
					song.albumTitle?.contains(query, ignoreCase = true) == true
			}
			if (isReversed) filtered.reversed() else filtered
		}
	}
	var reordering by rememberSaveable { mutableStateOf(false) }
	var reorderSongs by remember(reordering, collection?.songs) {
		mutableStateOf(
			(collection as? DomainPlaylist)?.songs.orEmpty().mapIndexed { index, song ->
				"${song.id}_$index" to song
			}
		)
	}
	val draggable = rememberDraggableListState(
		lazyListState = viewModel.listState,
		onMove = { from, to ->
			reorderSongs = reorderSongs.toMutableList().apply {
				add(to, removeAt(from))
			}
		}
	)
	val backStack = LocalNavStack.current
	var playlistDeletionId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareId by remember { mutableStateOf<String?>(null) }
	var editingRules by remember { mutableStateOf<SmartPlaylistDetails?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	val albumInfoState by viewModel.albumInfoState.collectAsState()
	val selectedAlbumIsStarred by viewModel.selectedAlbumIsStarred.collectAsStateWithLifecycle()
	val selectedAlbumRating by viewModel.selectedAlbumRating.collectAsStateWithLifecycle()
	val otherAlbums by viewModel.otherAlbums.collectAsState()
	val allDownloads by viewModel.allDownloads.collectAsState()
	val downloadStatus by viewModel.collectionDownloadStatus()
		.collectAsState(DownloadStatus.NOT_DOWNLOADED)

	val rating by viewModel.rating.collectAsStateWithLifecycle()

	val titleAlpha by remember {
		derivedStateOf {
			if (viewModel.listState.firstVisibleItemIndex >= 1) return@derivedStateOf 1f
			val height =
				viewModel.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size?.toFloat()
					?: 0f
			if (height > 0f) {
				val threshold = height * 0.4f
				((viewModel.listState.firstVisibleItemScrollOffset.toFloat() - threshold) / (height - threshold)).coerceIn(
					0f,
					1f
				)
			} else {
				0f
			}
		}
	}
	val colorScheme = if (preferenceManager.dynamicTheming) {
		rememberColorSchemeFromCoverArt(
			coverArtId = collection?.coverArtId,
			specVersion = ColorSpec.SpecVersion.SPEC_2025
		)
	} else {
		null
	}

	LoakTheme(colorScheme) {
		Scaffold(
			topBar = {
				if (reordering && collection is DomainPlaylist && !expanded) {
					NestedTopBar(
						navigationAction = {
							TopBarButton(
								modifier = Modifier.padding(start = 20.dp, end = 13.dp),
								onClick = { reordering = false }
							) {
								Icon(
									imageVector = Icons.Outlined.Close,
									contentDescription = stringResource(Res.string.action_cancel)
								)
							}
						},
						title = {
							Text(
								text = stringResource(Res.string.title_reorder_playlist),
								style = MaterialTheme.typography.titleMedium
							)
						},
						actions = {
							Button(
								onClick = {
									viewModel.reorderPlaylist(reorderSongs.map { it.second })
									reordering = false
								},
								modifier = Modifier.padding(end = 8.dp)
							) {
								Text(stringResource(Res.string.action_save))
							}
						}
					)
				} else {
					CollectionDetailScreenTopBar(
						albumInfoState = albumInfoState,
						collection = collection,
						titleAlpha = titleAlpha,
						onSetShareId = { shareId = it },
						onDownloadAll = { viewModel.downloadAll() },
						onCancelDownloadAll = { viewModel.cancelDownloadAll() },
						onPlayNext = { if (collection != null) player.playNext(collection) },
						onAddToQueue = { if (collection != null) player.addToQueue(collection) },
						downloadStatus = downloadStatus,
						rating = if (collection !is DomainPlaylist) rating else null,
						onSetRating = if (collection !is DomainPlaylist) {
							{ viewModel.rateAlbum(it) }
						} else null,
						starred = if (collection !is DomainPlaylist) starred else null,
						onSetStarred = if (collection !is DomainPlaylist) {
							{ viewModel.starAlbum(it) }
						} else null,
						refreshCollection = { viewModel.refreshCollection(false) },
						onReorderPlaylist = if (collection is DomainPlaylist) {
							{ reordering = true }
						} else null,
						onDeletePlaylist = if (collection is DomainPlaylist) {
							{ playlistDeletionId = collection.id }
						} else null
					)
				}
			},
			bottomBar = {
				val scrollManager = LocalBottomBarScrollManager.current
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		) { contentPadding ->
			PullToRefreshBox(
				modifier = Modifier
					.padding(top = contentPadding.calculateTopPadding())
					.background(MaterialTheme.colorScheme.surface),
				finished = reordering || collectionState !is UiState.Loading,
				onRefresh = { if (!reordering) viewModel.refreshCollection(true) },
				key = collectionState
			) {
				Row {
					// expanded windows: cover and actions in a column beside the tracks
					if (expanded && collection != null) Column(
						modifier = Modifier
							.width(400.dp)
							.fillMaxHeight()
							.verticalScroll(rememberScrollState())
							.padding(contentPadding.withoutTop()),
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						if (reordering && collection is DomainPlaylist) {
							CollectionDetailScreenHeadingRow(
								collection = collection,
								tab = tab,
								titleAlpha = 1f
							)
							Surface(
								shape = MaterialTheme.shapes.small,
								color = MaterialTheme.colorScheme.secondaryContainer,
								modifier = Modifier.padding(top = 8.dp)
							) {
								Text(
									text = stringResource(Res.string.title_reorder_playlist),
									style = MaterialTheme.typography.labelMedium,
									color = MaterialTheme.colorScheme.onSecondaryContainer,
									modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
								)
							}
							Row(
								modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
								horizontalArrangement = Arrangement.spacedBy(10.dp)
							) {
								OutlinedButton(
									onClick = { reordering = false },
									modifier = Modifier.weight(1f).height(44.dp)
								) {
									Text(stringResource(Res.string.action_cancel))
								}
								Button(
									onClick = {
										viewModel.reorderPlaylist(reorderSongs.map { it.second })
										reordering = false
									},
									modifier = Modifier.weight(1.3f).height(44.dp)
								) {
									Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
									Spacer(Modifier.size(6.dp))
									Text(stringResource(Res.string.action_save))
								}
							}
							Surface(
								shape = MaterialTheme.shapes.medium,
								color = MaterialTheme.colorScheme.surfaceContainer,
								modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
							) {
								Text(
									text = stringResource(Res.string.info_reorder_hint),
									modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						} else {
							CollectionDetailScreenHeadingRow(
								collection = collection,
								tab = tab,
								titleAlpha = 1f
							)
							CollectionDetailScreenHeadingRowButtons(collection = collection)
							if (collection is DomainPlaylist) {
								PlaylistRebuiltNotice(collection)
								SmartRulesCard(collection, onEdit = { editingRules = it })
								RadioCard(collection)
							}
						}
					}
					Column(
						modifier = Modifier
							.background(MaterialTheme.colorScheme.surface)
							.weight(1f)
							.fillMaxHeight()
					) {
						if (!expanded && reordering && collection is DomainPlaylist) {
							Surface(
								shape = MaterialTheme.shapes.medium,
								color = MaterialTheme.colorScheme.surfaceContainer,
								modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
							) {
								Text(
									text = stringResource(Res.string.info_reorder_hint),
									modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
									style = MaterialTheme.typography.bodyMedium,
									color = MaterialTheme.colorScheme.onSurfaceVariant
								)
							}
						}
						LazyColumn(
							modifier = Modifier
								.fillMaxWidth()
								.weight(1f),
							horizontalAlignment = Alignment.CenterHorizontally,
							contentPadding = contentPadding.withoutTop(),
							state = viewModel.listState
						) {
						if (collection == null) {
							if (collectionState is UiState.Loading) {
								item {
									Box(
										modifier = Modifier.fillMaxWidth().padding(48.dp),
										contentAlignment = Alignment.Center
									) {
										CircularProgressIndicator()
									}
								}
							} else if (collectionState is UiState.Error) {
								item {
									ContentUnavailable(
										icon = Icons.Outlined.Note,
										label = (collectionState as UiState.Error).error.message
											?: stringResource(Res.string.info_no_songs)
									)
								}
							}
							return@LazyColumn
						}

						if (!expanded && !reordering) {
							item {
								CollectionDetailScreenHeadingRow(
									collection = collection,
									tab = tab,
									titleAlpha = 1f - titleAlpha
								)
							}

							item {
								CollectionDetailScreenHeadingRowButtons(
									collection = collection
								)
							}

							if (collection is DomainPlaylist) item {
								PlaylistRebuiltNotice(collection)
								SmartRulesCard(collection, onEdit = { editingRules = it })
							RadioCard(collection)
							}
						}

						if (collection is DomainAlbum) {
							collection.copy(
								songs = collection.songs.sortedWith(
									compareBy(
										{ it.discNumber },
										{ it.trackNumber }
									))
							).let { album ->
								album.songs.groupBy { it.discNumber }.forEach { group ->
									val multipleDiscs = album.songs.groupBy { it.discNumber }.size > 1
									if (group.key != null && multipleDiscs) {
										item {
											Row(
												modifier = Modifier
													.fillMaxWidth()
													.padding(horizontal = 16.dp)
													.padding(
														top = if (group.key == 1) 0.dp else 12.dp,
														bottom = 4.dp
													)
													.heightIn(min = 32.dp),
												verticalAlignment = Alignment.CenterVertically
											) {
												Icon(
													imageVector = Icons.Outlined.Album,
													contentDescription = null,
													tint = MaterialTheme.colorScheme.onSurfaceVariant,
													modifier = Modifier.size(20.dp)
												)

												Spacer(modifier = Modifier.width(8.dp))

												Text(
													text = stringResource(
														Res.string.title_disc_number,
														group.key as Int
													),
													style = MaterialTheme.typography.titleMediumEmphasized,
													fontWeight = FontWeight(600),
													color = MaterialTheme.colorScheme.onSurfaceVariant
												)
											}
										}
									}
									itemsIndexed(group.value) { index, song ->
										val download = allDownloads.find { it.songId == song.id }
										Box {
											CollectionDetailScreenSongRow(
												song = song,
												index = index,
												count = group.value.count(),
												isPlaylist = false,
												onClick = {
													if (playerState.currentSong?.id != song.id) {
														player.playNow(
															album,
															album.songs.indexOfFirst { it.id == song.id })
													} else {
														player.togglePlay()
													}
												},
												onPlayNext = {
													player.playNextSingle(song)
												},
												onAddToQueue = {
													player.addToQueueSingle(song)
												},
												collection = collection,
												onRemoveFromPlaylist = null,
												download = download
											)
										}
									}
								}
							}
						} else if (reordering && collection is DomainPlaylist) {
							draggableItemsIndexed(
								state = draggable,
								items = reorderSongs,
								key = { _, (key, _) -> key }
							) { index, (key, song), isDragging ->
								val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp)
								Surface(
									shape = MaterialTheme.shapes.medium,
									shadowElevation = elevation,
									color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surface,
									modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
								) {
									ListItem(
										colors = ListItemDefaults.colors(containerColor = Color.Transparent),
										leadingContent = {
											CoverArt(
												modifier = Modifier.size(44.dp),
												coverArtId = song.coverArtId,
												shape = MaterialTheme.shapes.small
											)
										},
										headlineContent = {
											Text(
												text = song.title,
												maxLines = 1,
												overflow = TextOverflow.Ellipsis
											)
										},
										supportingContent = song.artistName?.let {
											{ Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
										},
										trailingContent = {
											Box(
												modifier = Modifier
													.size(40.dp)
													.dragHandle(
														state = draggable,
														key = key
													),
												contentAlignment = Alignment.Center
											) {
												Icon(
													imageVector = Icons.Outlined.DragHandle,
													contentDescription = stringResource(Res.string.action_reorder),
													tint = MaterialTheme.colorScheme.onSurfaceVariant
												)
											}
										}
									)
								}
							}
						} else {
							val playlistSongs = collection.songs
							if (playlistSongs.isNotEmpty()) {
								item {
									PlaylistTrackFilterRow(
										query = searchQuery,
										filteredCount = visibleSongs.size,
										totalCount = playlistSongs.size,
										isReversed = isReversed,
										onToggleReversed = { viewModel.toggleReversed() }
									)
								}
							}
							if (visibleSongs.isEmpty() && playlistSongs.isNotEmpty()) {
								item {
									ContentUnavailable(
										icon = Icons.Outlined.Note,
										label = stringResource(Res.string.info_no_matching_tracks)
									)
								}
							} else {
								itemsIndexed(visibleSongs, key = { index, song -> "${song.id}_$index" }) { index, song ->
									val download = allDownloads.find { it.songId == song.id }
									Box {
										CollectionDetailScreenSongRow(
											song = song,
											index = index,
											count = visibleSongs.count(),
											isPlaylist = true,
											onClick = {
												if (playerState.currentSong?.id != song.id) {
													player.playNow((collection as DomainPlaylist).copy(songs = visibleSongs), index)
												} else {
													player.togglePlay()
												}
											},
											onPlayNext = {
												player.playNextSingle(song)
											},
											onAddToQueue = {
												player.addToQueueSingle(song)
											},
											collection = collection,
											onRemoveFromPlaylist = { viewModel.removeFromPlaylist(song) },
											download = download
										)
									}
								}
							}
						}

						if (collection.songs.isEmpty()) {
							item {
								ContentUnavailable(
									icon = Icons.Outlined.Note,
									label = stringResource(Res.string.info_no_songs)
								)
							}
						}
						if (!reordering) item { CollectionDetailScreenFooterRow(collection) }

						(collection as? DomainAlbum)?.artistName?.let { artistName ->
							collectionDetailScreenMoreByArtistRow(
								artistName = artistName,
								artistAlbums = otherAlbums,
								selectedAlbum = selectedAlbum,
								onSetShareId = { shareId = it },
								onPlayNext = if (selectedAlbum != null) {
									{ player.playNext(selectedAlbum as DomainSongCollection) }
								} else null,
								onAddToQueue = if (selectedAlbum != null) {
									{ player.addToQueue(selectedAlbum as DomainSongCollection) }
								} else null,
								selectedAlbumRating = selectedAlbumRating,
								selectedAlbumStarred = selectedAlbumIsStarred,
								onSetAlbumRating = { viewModel.rateSelectedAlbum(it) },
								onSetAlbumStarred = { viewModel.starSelectedAlbum(it) },
								onSelect = { viewModel.selectAlbum(it) },
								onDeselect = { viewModel.clearSelection() },
								tab = tab
							)
						}
					}
					}
				}
			}
		}

		ErrorSnackBar(
			error = (collectionState as? UiState.Error)?.error,
			onClearError = { viewModel.clearError() }
		)

		ShareDialog(
			id = shareId,
			onIdClear = { shareId = null; viewModel.clearSelection() },
			expiry = shareExpiry,
			onExpiryChange = { shareExpiry = it }
		)

		val rules = editingRules
		if (rules != null && collection is DomainPlaylist) SmartPlaylistEditor(
			playlist = collection,
			details = rules,
			onDismissRequest = { editingRules = null },
			onSaved = {
				editingRules = null
				viewModel.refreshCollection(true)
			}
		)

		DeletionDialog(
			endpoint = DeletionEndpoint.PLAYLIST,
			id = playlistDeletionId,
			onIdClear = { playlistDeletionId = null },
			onRefresh = { backStack.removeLastOrNull() }
		)
	}
}
