package eu.depau.loak.ui.screens.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_clear_queue
import eu.depau.loak.generated.resources.count_remaining_songs
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.info_duration_left
import eu.depau.loak.generated.resources.info_instant_mix
import eu.depau.loak.generated.resources.info_no_queue
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalSheetState
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.QueueInfoType
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.PlaylistRemove
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.queue.components.QueueScreenItem
import eu.depau.loak.ui.screens.queue.viewmodels.QueueViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.persistentListOf
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.ui.components.sheets.SongSheet
import eu.depau.loak.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import kotlin.time.Duration
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import eu.depau.loak.di.LocalSnackBarState
import eu.depau.loak.ui.components.snackbars.LoakSnackBar
import eu.depau.loak.ui.theme.defaultFont
import eu.depau.loak.ui.util.draggableItemsIndexed
import eu.depau.loak.ui.util.rememberDraggableListState
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.produceState
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.ServerQueue
import eu.depau.loak.generated.resources.action_load_server_queue
import eu.depau.loak.generated.resources.action_more
import eu.depau.loak.generated.resources.action_save_to_playlist
import eu.depau.loak.generated.resources.action_send_queue_to_server
import eu.depau.loak.generated.resources.info_server_queue_checking
import eu.depau.loak.generated.resources.info_server_queue_none
import eu.depau.loak.generated.resources.info_server_queue_saved_by
import eu.depau.loak.generated.resources.info_server_queue_saved_by_ago
import eu.depau.loak.generated.resources.info_server_unreachable
import eu.depau.loak.icons.outlined.Delete
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.MoreVert
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.icons.outlined.Upload
import eu.depau.loak.ui.util.pickedUpFromLabel
import eu.depau.loak.ui.util.timeAgo
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/** The queue, as a bottom sheet or, with [pane], as the side pane on expanded windows. */
fun QueueScreen(pane: Boolean = false) {
	val viewModel = koinViewModel<QueueViewModel>()
	val backStack = LocalNavStack.current
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()
	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
	val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
	val queue = playerState.queue
	val selectedIndex by viewModel.selectedIndex.collectAsStateWithLifecycle()
	val selectedSongIsStarred by viewModel.selectedSongIsStarred.collectAsStateWithLifecycle()
	val selectedSongRating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
	val allDownloads by viewModel.allDownloads.collectAsStateWithLifecycle(persistentListOf())
	var playlistSong by remember { mutableStateOf<DomainSong?>(null) }
	var savingQueue by remember { mutableStateOf(false) }
	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	val haptic = LocalHapticFeedback.current
	val draggableState = rememberDraggableListState(viewModel.listState) { from, to ->
		player.moveQueueItem(from, to)
		haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
	}

	LaunchedEffect(playerState.currentIndex) {
		runCatching {
			if (queue.isNotEmpty()) {
				draggableState.listState.scrollToItem(
					playerState.currentIndex.coerceAtLeast(0)
				)
			}
		}
	}

	val preferenceManager = koinInject<PreferenceManager>()

	val songCountText = when (preferenceManager.queueInfoType) {
		QueueInfoType.Full -> pluralStringResource(
			Res.plurals.count_songs,
			queue.size,
			queue.size
		)

		QueueInfoType.Remaining -> pluralStringResource(
			Res.plurals.count_remaining_songs,
			queue.size - playerState.currentIndex,
			queue.size - playerState.currentIndex
		)
	}
	val durationText = remember(queue, playerState.progress, playerState.currentIndex) {
		var totalMillis = queue.sumOf { it.duration.inWholeMilliseconds } // ms because precision

		if (preferenceManager.queueInfoType == QueueInfoType.Remaining) {
			// duration of all songs that are before the current index
			val pastMillis = queue
				.take(playerState.currentIndex)
				.sumOf { it.duration.inWholeMilliseconds }

			// elapsed duration of the current index
			val currentTrack = queue.getOrNull(playerState.currentIndex)
			val elapsedCurrentSeconds =
				(currentTrack?.duration?.inWholeMilliseconds ?: 0L) * playerState.progress

			// deduct previous durations plus current elapsed duration
			totalMillis -= pastMillis + elapsedCurrentSeconds.toLong()

			// wow, that's confusing as shit for some reason
		}

		// then we format manually because Duration.toString() doesn't let u disable decimals
		val totalSeconds = totalMillis.milliseconds.inWholeSeconds
		val hours = totalSeconds / 3600
		val minutes = (totalSeconds % 3600) / 60
		val seconds = totalSeconds % 60

		buildString {
			if (hours > 0) append("${hours}h ")
			if (minutes > 0 || hours > 0) append("${minutes}m ")
			append("${seconds}s")
		}
	}
	val formattedDurationText = when (preferenceManager.queueInfoType) {
		QueueInfoType.Full -> durationText
		QueueInfoType.Remaining -> stringResource(Res.string.info_duration_left, durationText)
	}

	val sheetState = if (pane) null else LocalSheetState.current
	val closeScope = rememberCoroutineScope()
	val animateToDismiss = {
		// the pane stays open next to the content
		if (sheetState != null) closeScope.launch {
			sheetState.hide()
		}.invokeOnCompletion {
			if (!sheetState.isVisible) {
				backStack.remove(Screen.Queue)
			}
		}
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Column(modifier = Modifier.fillMaxSize()) {
			if (queue.isNotEmpty()) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.padding(horizontal = 24.dp, vertical = 8.dp),
					horizontalArrangement = Arrangement.SpaceBetween
				) {
					Row(
						modifier = Modifier.height(36.dp).clickable {
							val newValue = when (preferenceManager.queueInfoType) {
								QueueInfoType.Full -> QueueInfoType.Remaining
								QueueInfoType.Remaining -> QueueInfoType.Full
							}
							preferenceManager.queueInfoType = newValue
						},
						verticalAlignment = Alignment.CenterVertically
					) {
						Text(
							text = "$songCountText • $formattedDurationText",
							style = MaterialTheme.typography.titleMedium,
							fontWeight = FontWeight.SemiBold,
							fontFamily = defaultFont(round = 100f),
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							textAlign = TextAlign.Center
						)
					}
					Box {
						var menuOpen by remember { mutableStateOf(false) }
						IconButton(
							onClick = { menuOpen = true },
							modifier = Modifier.size(36.dp)
						) {
							Icon(Icons.Outlined.MoreVert, stringResource(Res.string.action_more))
						}
						QueueMenu(
							expanded = menuOpen,
							onDismissRequest = { menuOpen = false },
							onSendToServer = player::sendQueueToServer,
							onLoadFromServer = player::loadQueueFromServer,
							onSaveToPlaylist = { savingQueue = true },
							onClear = {
								haptic.performHapticFeedback(HapticFeedbackType.LongPress)
								player.clearQueueWithUndo()
							}
						)
					}
				}
				val instantMix by player.instantMix.collectAsStateWithLifecycle()
				(pickedUpFromLabel(ago = true)
					?: instantMix?.let { stringResource(Res.string.info_instant_mix, it.seedName) })?.let {
					Text(
						text = it,
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp)
					)
				}
			}

			LazyColumn(
				modifier = Modifier
					.padding(horizontal = 12.dp)
					.fillMaxSize(),
				state = draggableState.listState,
				contentPadding = WindowInsets.systemBars
					.only(WindowInsetsSides.Bottom)
					.asPaddingValues(),
				verticalArrangement = if (queue.isNotEmpty())
					Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
				else Arrangement.Center
			) {
				draggableItemsIndexed(
					state = draggableState,
					items = queue,
					key = { index, _ -> index }
				) { index, song, isDragging ->
					QueueScreenItem(
						index = index,
						count = queue.count(),
						song = song,
						isPlaying = playerState.currentIndex == index
							&& !playerState.isPaused,
						isSelected = playerState.currentIndex == index,
						isDragging = isDragging,
						draggableState = draggableState,
						onClick = dropUnlessResumed {
							if (playerState.currentIndex != index) {
								player.playAt(index)
								animateToDismiss()
							} else {
								player.seek(0f)
								player.resume()
							}
						},
						onLongClick = {
							haptic.performHapticFeedback(HapticFeedbackType.LongPress)
							viewModel.select(index, song)
						},
						onPlayNext = { player.playNextSingle(song) },
						onRemove = {
							haptic.performHapticFeedback(HapticFeedbackType.LongPress)
							player.removeFromQueueWithUndo(index)
						},
						isOffline = !isOnline,
						isDownloaded = downloadedSongs.containsKey(song.id)
					)
				}
				if (queue.isEmpty()) {
					item {
						ContentUnavailable(
							icon = Icons.Outlined.PlaylistRemove,
							label = stringResource(Res.string.info_no_queue)
						)
					}
				}
			}
		}
		// The queue sheet covers the app's snackbar host, so undo snackbars show here too,
		// under the header: the sheet's bottom edge is off-screen while it's half open.
		if (!pane) SnackbarHost(
			hostState = LocalSnackBarState.current,
			modifier = Modifier.align(Alignment.TopCenter).padding(top = 52.dp)
		) { LoakSnackBar(snackBarData = it) }
	}

	val selectedSong = selectedIndex?.let { queue.getOrNull(it) }
	if (selectedIndex != null && selectedSong != null) {
		val index = selectedIndex!!
		val leaveQueue = {
			backStack.remove(Screen.Queue)
			backStack.remove(Screen.NowPlaying)
		}
		SongSheet(
			onDismissRequest = { viewModel.clearSelection() },
			song = selectedSong,
			// the playing song is already "next"
			onPlayNext = if (index == playerState.currentIndex) null
			else ({ player.playNextSingle(selectedSong) }),
			onRemoveFromQueue = { player.removeFromQueueWithUndo(index) },
			onAddToPlaylist = { playlistSong = selectedSong },
			downloadStatus = allDownloads.find { it.songId == selectedSong.id }?.status,
			onDownload = { viewModel.downloadManager.downloadSong(selectedSong) },
			onCancelDownload = { viewModel.downloadManager.cancelDownload(selectedSong.id) },
			onDeleteDownload = { viewModel.downloadManager.deleteDownload(selectedSong.id) },
			starred = selectedSongIsStarred,
			onSetStarred = { viewModel.star(selectedSong, it) },
			rating = selectedSongRating,
			onSetRating = { viewModel.rate(selectedSong, it) },
			onShare = { shareId = selectedSong.id },
			onViewAlbum = selectedSong.albumId?.let { albumId ->
				dropUnlessResumed {
					leaveQueue()
					backStack.add(Screen.CollectionDetail(albumId, ""))
				}
			},
			onViewArtist = dropUnlessResumed {
				leaveQueue()
				backStack.add(Screen.ArtistDetail(selectedSong.artistId))
			},
			onTrackInfo = dropUnlessResumed {
				viewModel.clearSelection()
				backStack.add(Screen.SongDetailSheet(selectedSong.id, selectedSong.coverArtId))
			}
		)
	}

	playlistSong?.let { song ->
		PlaylistUpdateDialog(
			songs = persistentListOf(song),
			onDismissRequest = { playlistSong = null }
		)
	}

	if (savingQueue) {
		PlaylistUpdateDialog(
			songs = queue.toImmutableList(),
			onDismissRequest = { savingQueue = false }
		)
	}

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)
}

/**
 * The queue's ⋯ menu: the server queue's status with send and load, then saving and
 * clearing the queue.
 */
@Composable
private fun QueueMenu(
	expanded: Boolean,
	onDismissRequest: () -> Unit,
	onSendToServer: () -> Unit,
	onLoadFromServer: () -> Unit,
	onSaveToPlaylist: () -> Unit,
	onClear: () -> Unit
) {
	DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
		val queueSyncManager = koinInject<QueueSyncManager>()
		// fetched each time the menu opens
		val remote by produceState<Result<ServerQueue?>?>(null) {
			value = runCatching { queueSyncManager.fetch() }
		}
		val status = when {
			remote == null -> stringResource(Res.string.info_server_queue_checking)
			remote!!.isFailure -> stringResource(Res.string.info_server_unreachable)
			else -> when (val queue = remote!!.getOrNull()) {
				null -> stringResource(Res.string.info_server_queue_none)
				else -> {
					val name = QueueSyncManager.sourceName(queue.changedBy)
					queue.changed?.let {
						stringResource(Res.string.info_server_queue_saved_by_ago, name, it.timeAgo())
					} ?: stringResource(Res.string.info_server_queue_saved_by, name)
				}
			}
		}
		Text(
			text = status,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
		)
		DropdownMenuItem(
			text = { Text(stringResource(Res.string.action_send_queue_to_server)) },
			leadingIcon = { Icon(Icons.Outlined.Upload, null) },
			onClick = {
				onDismissRequest()
				onSendToServer()
			}
		)
		DropdownMenuItem(
			text = { Text(stringResource(Res.string.action_load_server_queue)) },
			leadingIcon = { Icon(Icons.Outlined.Download, null) },
			enabled = remote?.getOrNull() != null,
			onClick = {
				onDismissRequest()
				onLoadFromServer()
			}
		)
		HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
		DropdownMenuItem(
			text = { Text(stringResource(Res.string.action_save_to_playlist)) },
			leadingIcon = { Icon(Icons.Outlined.PlaylistAdd, null) },
			onClick = {
				onDismissRequest()
				onSaveToPlaylist()
			}
		)
		DropdownMenuItem(
			text = { Text(stringResource(Res.string.action_clear_queue)) },
			leadingIcon = { Icon(Icons.Outlined.Delete, null) },
			onClick = {
				onDismissRequest()
				onClear()
			}
		)
	}
}
