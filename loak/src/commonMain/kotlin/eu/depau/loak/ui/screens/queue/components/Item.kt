package eu.depau.loak.ui.screens.queue.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_play_next
import eu.depau.loak.generated.resources.action_remove_from_queue
import eu.depau.loak.generated.resources.action_reorder
import eu.depau.loak.generated.resources.info_explicit
import eu.depau.loak.generated.resources.info_not_available_offline
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Delete
import eu.depau.loak.icons.outlined.DragHandle
import eu.depau.loak.icons.outlined.Lock
import eu.depau.loak.icons.outlined.Offline
import eu.depau.loak.icons.outlined.QueuePlayNext
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.common.Waveform
import eu.depau.loak.ui.util.DraggableListState
import eu.depau.loak.ui.util.buildSongInfoString
import eu.depau.loak.ui.util.dragHandle
import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.playOrExplain
import eu.depau.loak.ui.components.common.unavailable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.depau.loak.ui.components.sheets.SongActionsSheet

@Composable
fun QueueScreenItem(
	index: Int,
	count: Int,
	song: DomainSong,
	isPlaying: Boolean,
	isSelected: Boolean,
	isDragging: Boolean,
	draggableState: DraggableListState,
	/** The row's key in the list, which the drag handle finds it by. */
	dragKey: Any,
	onClick: () -> Unit,
	canPlayNext: Boolean,
	canAddToQueue: Boolean,
	onRemoveFromQueue: (() -> Unit)?,
	onPlayNext: () -> Unit,
	onRemove: () -> Unit
) {
	val haptic = LocalHapticFeedback.current
	var sheetOpen by rememberSaveable { mutableStateOf(false) }
	val onLongClick = {
		haptic.performHapticFeedback(HapticFeedbackType.LongPress)
		sheetOpen = true
	}
	SongActionsSheet(
		song = song,
		open = sheetOpen,
		onDismissRequest = { sheetOpen = false },
		canPlayNext = canPlayNext,
		canAddToQueue = canAddToQueue,
		onRemoveFromQueue = onRemoveFromQueue,
		inPlayer = true
	)
	val preferenceManager = koinInject<PreferenceManager>()
	val isExplicit = song.explicitStatus == DomainExplicitStatus.Explicit
		&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	val maybeUnavailable = !LocalAvailability.current.song(song.id)

	val elevation by animateDpAsState(
		targetValue = if (isDragging) 8.dp else 0.dp,
		animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()
	)

	val dismissState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()

	val itemShape = SegmentedListItemDefaults.segmentedShapes(
		index = index,
		count = count,
		dismissDirection = dismissState.dismissDirection
	)

	SwipeToDismissBox(
		state = dismissState,
		onDismiss = { direction ->
			scope.launch {
				// rows are keyed by song id: the box first returns to its slot so its
				// own dismissed-offset transition doesn't double-fire during the
				// removal fade (the removed row keeps its per-song key and disappears)
				dismissState.snapTo(SwipeToDismissBoxValue.Settled)
				if (direction == SwipeToDismissBoxValue.EndToStart) onPlayNext() else onRemove()
			}
		},
		backgroundContent = {
			// the rows are translucent: only draw the action behind one being swiped
			if (dismissState.dismissDirection == SwipeToDismissBoxValue.Settled) return@SwipeToDismissBox
			val playNext = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(itemShape.shape)
					.background(
						if (playNext) MaterialTheme.colorScheme.primaryContainer
						else MaterialTheme.colorScheme.errorContainer
					)
					.padding(horizontal = 20.dp)
			) {
				if (playNext) {
					Icon(
						imageVector = Icons.Outlined.QueuePlayNext,
						contentDescription = stringResource(Res.string.action_play_next),
						tint = MaterialTheme.colorScheme.onPrimaryContainer,
						modifier = Modifier.align(Alignment.CenterEnd)
					)
				} else {
					Icon(
						imageVector = Icons.Outlined.Delete,
						contentDescription = stringResource(Res.string.action_remove_from_queue),
						tint = MaterialTheme.colorScheme.onErrorContainer,
						modifier = Modifier.align(Alignment.CenterStart)
					)
				}
			}
		},
		content = {
			Surface(
				shadowElevation = elevation,
				shape = itemShape.shape,
				// translucent over the pane or sheet, which may be tinted by the cover art;
				// opaque while lifted for dragging
				color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh
				else Color.Transparent
			) {
				SegmentedListItem(
					modifier = Modifier.unavailable(maybeUnavailable),
					onClick = playOrExplain(song.id, onClick),
					onLongClick = onLongClick,
					enabled = !isExplicit,
					selected = isSelected,
					colors = SegmentedListItemDefaults.segmentedColors(
						containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .05f),
						disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .03f),
						selectedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .12f),
						selectedContentColor = MaterialTheme.colorScheme.primary,
						selectedSupportingContentColor = MaterialTheme.colorScheme.primary
							.copy(alpha = .7f)
					),
					shapes = itemShape,
					verticalAlignment = Alignment.CenterVertically,
					content = { MarqueeText(song.title) },
					// plain text: links in it would take taps meant for the row (which plays)
					supportingContent = {
						MarqueeText(buildSongInfoString(song = song, onClickArtist = {}).text)
					},
					leadingContent = {
						CoverArt(
							modifier = Modifier.size(48.dp),
							coverArtId = song.coverArtId,
							shape = ContinuousRoundedRectangle(10.dp)
						)
					},
					trailingContent = {
						Row(
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalAlignment = Alignment.CenterVertically
						) {
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
							if (isSelected) {
								Waveform(isPlaying = isPlaying)
							}
							IconButton(
								// by key: the handle's gesture outlives index changes
								modifier = Modifier.dragHandle(
									state = draggableState,
									key = dragKey
								),
								onClick = {}
							) {
								Icon(
									Icons.Outlined.DragHandle,
									contentDescription = stringResource(Res.string.action_reorder)
								)
							}
						}
					},
					contentPadding = PaddingValues(10.dp)
				)
			}
		}
	)
}
