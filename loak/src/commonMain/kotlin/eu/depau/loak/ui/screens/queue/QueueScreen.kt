package eu.depau.loak.ui.screens.queue

import eu.depau.loak.ui.util.escapeToDismiss
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_clear_queue
import eu.depau.loak.generated.resources.count_remaining_songs
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.info_duration_left
import eu.depau.loak.generated.resources.info_instant_mix
import eu.depau.loak.generated.resources.info_no_queue
import eu.depau.loak.generated.resources.option_auto_fill_queue
import eu.depau.loak.generated.resources.subtitle_auto_fill_queue
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.ui.semantics.Role
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.QueueInfoType
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.PlaylistRemove
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.screens.queue.components.QueueScreenItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.depau.loak.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import kotlin.time.Duration
import androidx.compose.foundation.layout.Box
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

/**
 * Stable LazyColumn key for a queue row. Songs are keyed by id, with an occurrence
 * counter so that duplicate ids (radio, nonplayable/pending songs, enqueue-undo copies)
 * all get distinct keys. The key is a pure function of (index, item), so it stays
 * identical across reorders, removals and play-next moves — that identity is what
 * lets `loakAnimateItem` animate placement/removal instead of hopping.
 *
 * A String key is required: Android's LazyList persists item keys in a Bundle, so
 * custom data-class keys crash with "not supported ... can only use types stored
 * inside the Bundle".
 */
private fun songQueueKey(id: String, occurrence: Int): String = "$id\u0000$occurrence"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/** The queue: in the player's Up next sheet or pane, and the side pane on expanded windows. */
fun QueueScreen() {
	val backStack = LocalNavStack.current
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()
	val queue = playerState.queue
	var savingQueue by remember { mutableStateOf(false) }

	val allAutoplay by player.autoplay.collectAsStateWithLifecycle()
	// the queue can update a frame before Autoplay drops what joined it: rows share keys
	val autoplay = remember(allAutoplay, queue) {
		val queued = queue.mapTo(HashSet()) { it.id }
		allAutoplay.filter { it.id !in queued }
	}
	val queueKey = { index: Int ->
		// number of times the same id appears before this position,
		// so duplicate-titled entries keep distinct but stable keys
		val id = queue[index].id
		songQueueKey(id, (0 until index).count { queue[it].id == id })
	}
	val haptic = LocalHapticFeedback.current
	// one list: the queue, the Autoplay row at the queue's size, then the Autoplay songs.
	// Autoplay songs can move into the queue (onto the row: its end); queue songs stay in it.
	// Read at each move: the state is remembered with the first lambdas
	val currentQueue by rememberUpdatedState(queue)
	val currentAutoplay by rememberUpdatedState(autoplay)
	val draggableState = rememberDraggableListState(
		canMove = { from, to -> from > currentQueue.size || to < currentQueue.size }
	) { from, to ->
		val size = currentQueue.size
		val song = currentAutoplay.getOrNull(from - size - 1)
		when {
			from < size -> player.moveQueueItem(from, to)
			song == null -> return@rememberDraggableListState
			to <= size -> player.queueAutoplay(song, to)
			else -> currentAutoplay.getOrNull(to - size - 1)?.let { player.moveAutoplay(song, it) }
		}
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
				// one block for all rows: an Autoplay song dragged into the queue stays the same
				// composition (same key, same call site), so its drag goes on
				val autoplayShown = queue.isNotEmpty() && preferenceManager.autoFillQueue
				val rows = queue.size + when {
					queue.isEmpty() -> 0
					autoplayShown -> 1 + autoplay.size
					else -> 1
				}
				draggableItemsIndexed(
					state = draggableState,
					items = List(rows) { it },
					key = { row, _ ->
						when {
							row < queue.size -> queueKey(row)
							row == queue.size -> AUTOPLAY_KEY
							// as a queue row's: Autoplay songs aren't in the queue
							else -> songQueueKey(autoplay[row - queue.size - 1].id, 0)
						}
					}
				) { row, _, isDragging ->
					if (row == queue.size) {
						AutoplayRow(
							checked = preferenceManager.autoFillQueue,
							onCheckedChange = { preferenceManager.autoFillQueue = it }
						)
						return@draggableItemsIndexed
					}
					val inQueue = row < queue.size
					val index = if (inQueue) row else row - queue.size - 1
					val song = if (inQueue) queue[index] else autoplay[index]
					QueueScreenItem(
						index = index,
						count = if (inQueue) queue.size else autoplay.size,
						song = song,
						isPlaying = inQueue && playerState.currentIndex == index
							&& !playerState.isPaused,
						isSelected = inQueue && playerState.currentIndex == index,
						isDragging = isDragging,
						draggableState = draggableState,
						dragKey = if (inQueue) queueKey(index) else songQueueKey(song.id, 0),
						onClick = dropUnlessResumed {
							when {
								!inQueue -> player.playAutoplay(song)
								playerState.currentIndex != index -> player.playAt(index)
								else -> {
									player.seek(0f)
									player.resume()
								}
							}
						},
						// the playing song is already "next"; queued songs are in the queue
						canPlayNext = !inQueue || index != playerState.currentIndex,
						canAddToQueue = !inQueue,
						onRemoveFromQueue = if (inQueue) ({ player.removeFromQueueWithUndo(index) }) else null,
						onPlayNext = { player.playNextSingle(song) },
						onRemove = {
							haptic.performHapticFeedback(HapticFeedbackType.LongPress)
							if (inQueue) player.removeFromQueueWithUndo(index)
							else player.removeFromAutoplay(song)
						}
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
	}

	if (savingQueue) {
		PlaylistUpdateDialog(
			songs = queue.toImmutableList(),
			onDismissRequest = { savingQueue = false }
		)
	}
}

private const val AUTOPLAY_KEY = "autoplay"

/** The Autoplay switch, after the queue. */
@Composable
private fun AutoplayRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
			.padding(start = 12.dp, end = 4.dp, top = 20.dp, bottom = 8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				text = stringResource(Res.string.option_auto_fill_queue),
				style = MaterialTheme.typography.titleMedium
			)
			Text(
				text = stringResource(Res.string.subtitle_auto_fill_queue),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		Switch(checked = checked, onCheckedChange = null)
	}
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
	DropdownMenu(
		expanded = expanded,
		onDismissRequest = onDismissRequest,
		modifier = Modifier.escapeToDismiss(onDismissRequest)
	) {
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
