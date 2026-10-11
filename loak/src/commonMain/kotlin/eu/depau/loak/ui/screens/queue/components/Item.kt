package eu.depau.loak.ui.screens.queue.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
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

	val dismissState = remember { AnchoredDraggableState(SwipeToDismissBoxValue.Settled) }
	val snapAnimationSpec = remember {
		spring<Float>(
			dampingRatio = Spring.DampingRatioNoBouncy,
			stiffness = Spring.StiffnessMediumLow
		)
	}
	val snapFling = AnchoredDraggableDefaults.flingBehavior(
		state = dismissState,
		positionalThreshold = { it * .5f },
		animationSpec = snapAnimationSpec
	)
	val flingBehavior = remember(snapFling) {
		object : FlingBehavior {
			// Release position decides: a quick flick must not bypass the halfway threshold.
			override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
				with(snapFling) { performFling(0f) }
				return initialVelocity
			}
		}
	}
	val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
	val offset = dismissState.offset.takeUnless { it.isNaN() } ?: 0f
	val direction = when {
		offset > 0f -> SwipeToDismissBoxValue.StartToEnd
		offset < 0f -> SwipeToDismissBoxValue.EndToStart
		else -> SwipeToDismissBoxValue.Settled
	}
	val armed = dismissState.targetValue != SwipeToDismissBoxValue.Settled
	val actionScale by animateFloatAsState(if (armed) 1.25f else 1f)
	LaunchedEffect(armed) {
		if (offset != 0f) haptic.performHapticFeedback(
			if (armed) HapticFeedbackType.GestureThresholdActivate
			else HapticFeedbackType.GestureEnd
		)
	}
	LaunchedEffect(dismissState.settledValue) {
		val settled = dismissState.settledValue
		if (settled != SwipeToDismissBoxValue.Settled) {
			// Reset before mutating the keyed queue so removal animations cannot dismiss twice.
			dismissState.snapTo(SwipeToDismissBoxValue.Settled)
			if (settled == SwipeToDismissBoxValue.EndToStart) onPlayNext() else onRemove()
		}
	}

	val itemShape = SegmentedListItemDefaults.segmentedShapes(
		index = index,
		count = count,
		dismissDirection = direction
	)

	Box(
		modifier = Modifier
			.onSizeChanged { size ->
				dismissState.updateAnchors(DraggableAnchors {
					SwipeToDismissBoxValue.Settled at 0f
					SwipeToDismissBoxValue.StartToEnd at size.width.toFloat()
					SwipeToDismissBoxValue.EndToStart at -size.width.toFloat()
				})
			}
			.anchoredDraggable(
				state = dismissState,
				orientation = Orientation.Horizontal,
				reverseDirection = isRtl,
				enabled = !isDragging && dismissState.settledValue == SwipeToDismissBoxValue.Settled,
				flingBehavior = flingBehavior
			),
		propagateMinConstraints = true
	) {
		if (direction != SwipeToDismissBoxValue.Settled) {
			val playNext = direction == SwipeToDismissBoxValue.EndToStart
			Box(
				modifier = Modifier
					.matchParentSize()
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
						modifier = Modifier.align(Alignment.CenterEnd).scale(actionScale)
					)
				} else {
					Icon(
						imageVector = Icons.Outlined.Delete,
						contentDescription = stringResource(Res.string.action_remove_from_queue),
						tint = MaterialTheme.colorScheme.onErrorContainer,
						modifier = Modifier.align(Alignment.CenterStart).scale(actionScale)
					)
				}
			}
		}
		Row(
			modifier = Modifier.offset {
				IntOffset((offset * if (isRtl) -1 else 1).roundToInt(), 0)
			}
		) {
			Surface(
				shadowElevation = elevation,
				shape = itemShape.shape,
				// Keep the artwork tint at rest, but cover the action beneath a moving row.
				color = if (isDragging || direction != SwipeToDismissBoxValue.Settled)
					MaterialTheme.colorScheme.surfaceContainerHigh
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
	}
}
