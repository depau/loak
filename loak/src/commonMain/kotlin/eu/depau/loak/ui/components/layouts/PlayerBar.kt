package eu.depau.loak.ui.components.layouts

import eu.depau.loak.di.CoverArtId
import eu.depau.loak.ui.util.escapeToDismiss
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import eu.depau.loak.ui.screens.nowPlaying.LocalPlayerSheet
import eu.depau.loak.ui.screens.nowPlaying.playerPill
import eu.depau.loak.ui.screens.nowPlaying.playerPillArt

import androidx.compose.ui.text.style.TextOverflow
import eu.depau.loak.ui.util.pickedUpFromLabel
import androidx.compose.foundation.background
import eu.depau.loak.icons.outlined.VolumeUp
import eu.depau.loak.generated.resources.action_volume
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_next_song
import eu.depau.loak.generated.resources.action_previous_song
import eu.depau.loak.generated.resources.action_repeat
import eu.depau.loak.generated.resources.action_shuffle
import eu.depau.loak.generated.resources.action_star
import eu.depau.loak.generated.resources.info_not_playing
import eu.depau.loak.generated.resources.info_progress
import eu.depau.loak.generated.resources.title_now_playing
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Note
import eu.depau.loak.icons.filled.RepeatOn
import eu.depau.loak.icons.filled.RepeatOneOn
import eu.depau.loak.icons.filled.ShuffleOn
import eu.depau.loak.icons.filled.SkipNext
import eu.depau.loak.icons.filled.SkipPrevious
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.KeyboardArrowDown
import eu.depau.loak.icons.outlined.Repeat
import eu.depau.loak.icons.outlined.Shuffle
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel
import eu.depau.loak.di.LocalMouseInUse
import eu.depau.loak.ui.theme.ContinuousCapsule
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import eu.depau.loak.util.toHoursMinutesSeconds
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.material3.SliderState
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import eu.depau.loak.ui.components.common.PlayPauseIcon

/** Room for "00:00 / 00:00" plus its padding and the gaps between the bar's buttons. */
private val TimeWidth = 112.dp

/** The inline volume slider (icon + 112dp slider) over the 48dp volume button. */
private val InlineVolumeExtraWidth = 96.dp

/**
 * The player on expanded windows: the mini player's card with the full controls. It floats over
 * the content; tapping the song opens the full player.
 */
@Composable
fun PlayerBar(modifier: Modifier = Modifier, enabled: Boolean = true) {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val song = playerState.currentSong
	val nowPlayingViewModel = koinViewModel<NowPlayingViewModel> { parametersOf(player) }
	val songIsStarred by nowPlayingViewModel.songIsStarred.collectAsState()
	val backStack = LocalNavStack.current
	val volumeLabel = stringResource(Res.string.action_volume)
	val isRadio = song?.id?.startsWith("radio_") == true
	val interactive = enabled && song != null

	val coilPlatformContext = LocalCoilPlatformContext.current
	val imageLoader = koinInject<ImageLoader>()
	val model = remember(song?.coverArtId) {
		ImageRequest.Builder(coilPlatformContext)
			.data(song?.coverArtId?.let { CoverArtId(it) })
			.diskCachePolicy(CachePolicy.ENABLED)
			.memoryCachePolicy(CachePolicy.ENABLED)
			.build()
	}
	val playerSheet = LocalPlayerSheet.current
	val openPlayer = { playerSheet.open() }
	val drag = rememberDraggableState { playerSheet.dragBy(it) }
	val shape = ContinuousRoundedRectangle(28.dp)

	Box(
		modifier = modifier
			.windowInsetsPadding(WindowInsets.navigationBars)
			.fillMaxWidth()
	) {
		Surface(
			modifier = Modifier
				.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
				.fillMaxWidth()
				.height(80.dp)
				// the open player grows out of the bar, following the finger
				.playerPill(playerSheet, 28.dp, NavigationBarDefaults.containerColor)
				.draggable(
					state = drag,
					orientation = Orientation.Vertical,
					enabled = interactive,
					onDragStopped = { velocity -> playerSheet.settle(velocity) }
				),
			shape = shape,
			color = NavigationBarDefaults.containerColor,
			shadowElevation = 6.dp
		) {
		Box {
			Row(
				modifier = Modifier.fillMaxHeight().padding(start = 12.dp, end = 12.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				// song
				Row(
					modifier = Modifier
						.weight(1f)
						.clip(ContinuousRoundedRectangle(12.dp))
						.clickable(enabled = interactive, onClick = openPlayer),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp)
				) {
					Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
						AsyncImage(
							model = model,
							imageLoader = imageLoader,
							contentDescription = null,
							contentScale = ContentScale.Crop,
							modifier = Modifier
								.playerPillArt(playerSheet)
								.size(56.dp)
								.clip(ContinuousRoundedRectangle(8.dp))
								.background(MaterialTheme.colorScheme.surfaceVariant)
						)
						if (song?.coverArtId.isNullOrEmpty()) {
							Icon(
								Icons.Filled.Note,
								contentDescription = null,
								tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
							)
						}
					}
					Column(Modifier.weight(1f, fill = false)) {
						MarqueeText(song?.title ?: stringResource(Res.string.info_not_playing))
						(song?.let { pickedUpFromLabel() } ?: song?.artistName)?.let {
							Text(
								it,
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					}
					IconButton(
						onClick = { nowPlayingViewModel.starSong(!songIsStarred) },
						enabled = interactive && !isRadio
					) {
						Icon(
							if (songIsStarred) Icons.Filled.Star else Icons.Outlined.Star,
							stringResource(Res.string.action_star)
						)
					}
				}

				// transport
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp)
				) {
					IconToggleButton(
						checked = playerState.isShuffleEnabled,
						onCheckedChange = { player.toggleShuffle() },
						enabled = interactive
					) {
						Icon(
							if (playerState.isShuffleEnabled) Icons.Filled.ShuffleOn else Icons.Outlined.Shuffle,
							stringResource(Res.string.action_shuffle)
						)
					}
					IconButton(onClick = { player.previous() }, enabled = interactive) {
						Icon(Icons.Filled.SkipPrevious, stringResource(Res.string.action_previous_song))
					}
					FilledIconButton(
						onClick = { if (playerState.isPaused) player.resume() else player.pause() },
						enabled = interactive,
						modifier = Modifier.size(56.dp)
					) {
						PlayPauseIcon(playerState)
					}
					IconButton(onClick = { player.next() }, enabled = interactive) {
						Icon(Icons.Filled.SkipNext, stringResource(Res.string.action_next_song))
					}
					IconToggleButton(
						checked = playerState.repeatMode != 0,
						onCheckedChange = { player.toggleRepeat() },
						enabled = interactive
					) {
						Icon(
							when (playerState.repeatMode) {
								1 -> Icons.Filled.RepeatOneOn
								2 -> Icons.Filled.RepeatOn
								else -> Icons.Outlined.Repeat
							},
							stringResource(Res.string.action_repeat)
						)
					}
				}

				// time and panels
				BoxWithConstraints(Modifier.weight(1f)) {
					// what fits, given the room: the open-player button always stays; the volume
					// slider collapses into a button first, then the time goes
					val buttons = 48.dp * (1 + (if (player.volume != null) 1 else 0))
					val showTime = maxWidth >= buttons + TimeWidth
					val roomy = maxWidth >= buttons + TimeWidth + InlineVolumeExtraWidth
					Row(
						modifier = Modifier.fillMaxWidth(),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
					) {
						if (showTime && song != null && !isRadio) {
							Text(
								"${(song.duration * playerState.progress.toDouble()).toHoursMinutesSeconds()} / " +
									song.duration.toHoursMinutesSeconds(),
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								modifier = Modifier.padding(end = 8.dp)
							)
						}
						// only where the app owns the volume (web); elsewhere the device's keys do
						player.volume?.let { volumeFlow ->
							val volume by volumeFlow.collectAsState()
							if (roomy) {
								Icon(
									Icons.Outlined.VolumeUp,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onSurfaceVariant
								)
								Slider(
									value = volume,
									onValueChange = player::setVolume,
									modifier = Modifier
										.width(112.dp)
										.semantics { contentDescription = volumeLabel }
								)
							} else {
								VolumePopupButton(volume, player::setVolume, volumeLabel)
							}
						}
						IconButton(onClick = openPlayer, enabled = interactive) {
							Icon(
								Icons.Outlined.KeyboardArrowDown,
								stringResource(Res.string.title_now_playing),
								modifier = Modifier.rotate(180f)
							)
						}
					}
				}
			}
			}
		}

		PlayerBarBottomStrip(
			progress = song?.let { playerState.progress },
			onSeek = player::seek,
			enabled = interactive,
			label = stringResource(Res.string.info_progress),
			modifier = Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.height(32.dp)
		)
	}
}

@Composable
private fun PlayerBarBottomStrip(
	progress: Float?,
	onSeek: (Float) -> Unit,
	enabled: Boolean,
	label: String,
	modifier: Modifier = Modifier
) {
	Box(modifier) {
		// Own the visual gap so clicks cannot reach the page behind this floating bar.
		Box(
			Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.height(16.dp)
				.pointerInput(Unit) {
					awaitPointerEventScope {
						while (true) {
							awaitPointerEvent().changes.forEach { it.consume() }
						}
					}
				}
		)
		if (progress != null) {
			PlayerBarProgress(
				progress = progress,
				onSeek = onSeek,
				enabled = enabled,
				label = label,
				modifier = Modifier
					.align(Alignment.BottomStart)
					.padding(horizontal = 40.dp)
					.fillMaxWidth()
			)
		}
	}
}

@Composable
private fun PlayerBarProgress(
	progress: Float,
	onSeek: (Float) -> Unit,
	enabled: Boolean,
	label: String,
	modifier: Modifier = Modifier
) {
	val mouseInUse = LocalMouseInUse.current
	val haptics = LocalHapticFeedback.current
	val hover = remember { MutableInteractionSource() }
	val hovered by hover.collectIsHoveredAsState()
	var dragging by remember { mutableStateOf(false) }
	val trackHeight by animateDpAsState(if (hovered || dragging) 8.dp else 3.dp)
	val progress = progress.coerceIn(0f, 1f)

	Box(
		modifier = modifier
			.height(32.dp)
			.hoverable(hover, enabled)
			.semantics {
				contentDescription = label
				progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
				if (enabled) setProgress {
					onSeek(it.coerceIn(0f, 1f))
					true
				}
			}
			.pointerInput(enabled, mouseInUse) {
				if (enabled && mouseInUse) detectTapGestures {
					onSeek((it.x / size.width.toFloat()).coerceIn(0f, 1f))
				}
			}
			.pointerInput(enabled) {
				if (enabled) detectDragGestures(
					onDragStart = {
						dragging = true
						haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
					},
					onDragEnd = {
						dragging = false
						haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
					},
					onDragCancel = { dragging = false }
				) { change, _ ->
					onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
					change.consume()
				}
			},
		contentAlignment = Alignment.Center
	) {
		Box(
			Modifier
				.fillMaxWidth()
				.height(trackHeight)
				.clip(ContinuousCapsule)
				.background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
		) {
			Box(
				Modifier
					.fillMaxWidth(progress)
					.fillMaxHeight()
					.background(MaterialTheme.colorScheme.primary)
			)
		}
	}
}

/**
 * Volume as a button with a vertical slider in a popup: opens on click, or while a mouse hovers
 * the button or the popup (closing shortly after it leaves both).
 */
@Composable
private fun VolumePopupButton(volume: Float, onVolumeChange: (Float) -> Unit, label: String) {
	var open by remember { mutableStateOf(false) }
	var openedByHover by remember { mutableStateOf(false) }
	val buttonHover = remember { MutableInteractionSource() }
	val popupHover = remember { MutableInteractionSource() }
	val hovered = buttonHover.collectIsHoveredAsState().value ||
		popupHover.collectIsHoveredAsState().value
	LaunchedEffect(hovered) {
		if (hovered && !open) {
			open = true
			openedByHover = true
		} else if (!hovered && openedByHover) {
			delay(300)
			open = false
		}
	}
	val sliderState = remember { SliderState(volume) }
	sliderState.onValueChange = { sliderState.value = it; onVolumeChange(it) }
	LaunchedEffect(volume) { sliderState.value = volume }

	Box {
		IconButton(
			onClick = { open = !open; openedByHover = false },
			modifier = Modifier.hoverable(buttonHover)
		) {
			Icon(Icons.Outlined.VolumeUp, label)
		}
		DropdownMenu(
			expanded = open,
			onDismissRequest = { open = false },
			// opened by hover: don't take keyboard focus away from the app (space = play/pause)
			properties = PopupProperties(focusable = !openedByHover),
			modifier = if (openedByHover) Modifier else Modifier.escapeToDismiss { open = false }
		) {
			VerticalSlider(
				state = sliderState,
				reverseDirection = true, // loud at the top
				modifier = Modifier
					.hoverable(popupHover)
					.padding(horizontal = 12.dp)
					.height(140.dp)
					.semantics { contentDescription = label }
			)
		}
	}
}
