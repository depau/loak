package eu.depau.loak.ui.components.layouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import eu.depau.loak.ui.screens.nowPlaying.LocalPlayerSheet
import eu.depau.loak.ui.screens.nowPlaying.playerPill
import eu.depau.loak.ui.screens.nowPlaying.playerPillArt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_next_song
import eu.depau.loak.generated.resources.action_pause
import eu.depau.loak.generated.resources.action_play
import eu.depau.loak.generated.resources.action_previous_song
import eu.depau.loak.generated.resources.action_star
import eu.depau.loak.generated.resources.info_not_playing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.ui.util.pickedUpFromLabel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.settings.MiniPlayerProgressStyle
import eu.depau.loak.domain.models.settings.MiniPlayerStyle
import eu.depau.loak.domain.models.settings.NavbarConfig
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Note
import eu.depau.loak.icons.filled.Pause
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.Radio
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel
import eu.depau.loak.ui.screens.settings.viewmodels.NavtabsViewModel
import eu.depau.loak.ui.util.playPauseIconPainter
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

@Composable
fun MiniPlayer(
	modifier: Modifier = Modifier,
	windowInsets: WindowInsets = NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal),
	enabled: Boolean = true
) {
	val player = koinInject<MediaPlayerViewModel>()
	val preferenceManager = koinInject<PreferenceManager>()
	val navtabsViewModel = koinViewModel<NavtabsViewModel>()
	val navtabsState by navtabsViewModel.state.collectAsState()
	val tabs = ((navtabsState as? UiState.Success)?.data ?: NavbarConfig.default)
		.tabs.filter { tab -> tab.visible }
	val backStack = LocalNavStack.current
	val haptics = LocalHapticFeedback.current
	val navBarPadding = if (tabs.size < 2)
		with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }
	else 0.dp

	val playerState by player.uiState.collectAsState()
	val song = playerState.currentSong
	val nowPlayingViewModel = koinViewModel<NowPlayingViewModel> { parametersOf(player) }
	val songIsStarred by nowPlayingViewModel.songIsStarred.collectAsState()

	val coilPlatformContext = LocalCoilPlatformContext.current
	val imageLoader = koinInject<ImageLoader>()
	val sessionManager = koinInject<SessionManager>()
	val model = remember(song?.coverArtId) {
		ImageRequest.Builder(coilPlatformContext)
			.data(song?.coverArtId?.let { sessionManager.getCoverArtUrl(it) })
			.memoryCacheKey(song?.coverArtId)
			.diskCacheKey(song?.coverArtId)
			.diskCachePolicy(CachePolicy.ENABLED)
			.memoryCachePolicy(CachePolicy.ENABLED)
			.build()
	}

	val detached = preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached

	val outerPadding = if (detached) 12.dp else 0.dp
	val coverRounding by animateDpAsState(
		if (playerState.isLoading)
			46.dp
		else 8.dp
	)
	val iconSize = if (detached) 24.dp else 32.dp

	val shape = ContinuousRoundedRectangle(
		if (detached) 16.dp else 0.dp
	)

	val playerSheet = LocalPlayerSheet.current
	val onClick = { playerSheet.open() }
	val drag = rememberDraggableState { playerSheet.dragBy(it) }

	val hasSong = song != null
	val isRadio = song?.id?.startsWith("radio_") == true
	val isInteractive = enabled && hasSong

	AnimatedVisibility(
		visible = hasSong || !preferenceManager.hideIfIdle,
		modifier = modifier
	) {
		Swiper(
			onSwipeLeft = {
				if (isInteractive) player.next()
			},
			onSwipeRight = {
				if (isInteractive) player.previous()
			},
			swipeLeftAccessibilityLabel = stringResource(Res.string.action_previous_song),
			swipeRightAccessibilityLabel = stringResource(Res.string.action_next_song),
			modifier = modifier.then(
				if (detached) {
					Modifier.windowInsetsPadding(windowInsets)
				} else Modifier
			),
			enabled = isInteractive
		) {
			Box(
				modifier = Modifier
					.widthIn(max = if (detached) 600.dp else Dp.Unspecified)
					.padding(
						bottom = if (detached) outerPadding + navBarPadding else 0.dp,
						start = outerPadding,
						end = outerPadding
					)
					.align(Alignment.Center)
			) {
				ListItem(
					modifier = Modifier
						.dropShadow(
							shape,
							Shadow(
								radius = if (detached) 10.dp else 8.dp,
								alpha = 0.25f
							)
						)
						// the open player grows out of here, following the finger
						.playerPill(playerSheet, if (detached) 16.dp else 0.dp, NavigationBarDefaults.containerColor)
						.draggable(
							state = drag,
							orientation = Orientation.Vertical,
							enabled = isInteractive,
							onDragStopped = { velocity -> playerSheet.settle(velocity) }
						),
					contentPadding = PaddingValues(
						start = if (detached) 10.dp else 16.dp,
						end = if (detached) 10.dp else 16.dp,
						top = if (detached) 10.dp else 16.dp,
						bottom = (if (detached) 10.dp else 12.dp) + if (detached) 0.dp else navBarPadding
					) + if (!detached)
						windowInsets.asPaddingValues()
					else PaddingValues(),
					verticalAlignment = Alignment.CenterVertically,
					colors = ListItemDefaults.colors(
						containerColor = NavigationBarDefaults.containerColor
					),
					shapes = ListItemDefaults.shapes(
						shape = shape,
						selectedShape = shape,
						pressedShape = shape,
						focusedShape = shape,
						hoveredShape = shape,
						draggedShape = shape
					),
					onClick = {
						onClick()
					},
					onLongClick = {
						haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
						onClick()
					},
					leadingContent = {
						Box(contentAlignment = Alignment.Center) {
							AsyncImage(
								model = model,
								imageLoader = imageLoader,
								contentDescription = null,
								contentScale = ContentScale.Fit,
								modifier = Modifier
									.playerPillArt(playerSheet)
									.size(if (detached) 48.dp else 50.dp)
									.padding(if (playerState.isLoading) 8.dp else 0.dp)
									.clip(
										ContinuousRoundedRectangle(coverRounding)
									)
									.background(MaterialTheme.colorScheme.surfaceVariant)
							)
							if (song?.coverArtId.isNullOrEmpty()) {
								Icon(
									imageVector = if (isRadio) Icons.Outlined.Radio else Icons.Filled.Note,
									contentDescription = null,
									tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
								)
							}
							AnimatedVisibility(
								playerState.isLoading,
								modifier = Modifier.matchParentSize(),
								enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec())
									+ fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
								exit = scaleOut(MaterialTheme.motionScheme.defaultSpatialSpec())
									+ fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
							) {
								CircularProgressIndicator(
									Modifier.matchParentSize(),
									trackColor = MaterialTheme.colorScheme.primaryContainer
								)
							}
						}
					},
					trailingContent = {
						Row(
							horizontalArrangement = Arrangement.spacedBy(
								if (detached) 8.dp else 12.dp
							)
						) {
							val colors = IconButtonDefaults.iconButtonVibrantColors()
							IconButton(
								onClick = {
									if (playerState.isPaused) {
										player.resume()
									} else {
										player.pause()
									}
								},
								enabled = isInteractive,
								colors = colors
							) {
								val painter = playPauseIconPainter(playerState.isPaused)
								val description = stringResource(
									if (playerState.isPaused)
										Res.string.action_play
									else Res.string.action_pause
								)
								if (painter != null) {
									Icon(
										painter = painter,
										contentDescription = description,
										modifier = Modifier.size(iconSize)
									)
								} else {
									Icon(
										imageVector = if (playerState.isPaused)
											Icons.Filled.Play
										else Icons.Filled.Pause,
										contentDescription = description,
										modifier = Modifier.size(iconSize)
									)
								}
							}
							// skipping is a swipe on the player, so the second button stars the song
							IconButton(
								onClick = { nowPlayingViewModel.starSong(!songIsStarred) },
								enabled = isInteractive && !isRadio,
								colors = colors
							) {
								Icon(
									imageVector = if (songIsStarred) Icons.Filled.Star else Icons.Outlined.Star,
									contentDescription = stringResource(Res.string.action_star),
									modifier = Modifier.size(iconSize)
								)
							}
						}
					},
					content = {
						song?.title?.let { title ->
							MarqueeText(title)
						}
					},
					supportingContent = {
						if (song != null) {
							// a queue picked up from another device says where it came from
							MarqueeText(
								pickedUpFromLabel() ?: song.artistName ?: "[unknown artist]"
							)
						} else {
							MarqueeText(stringResource(Res.string.info_not_playing))
						}
					},
					enabled = enabled
				)
				if (preferenceManager.miniPlayerProgressStyle == MiniPlayerProgressStyle.Visible
					|| preferenceManager.miniPlayerProgressStyle == MiniPlayerProgressStyle.Seekable
				) {
					var dragging by remember { mutableStateOf(false) }
					val alpha by animateFloatAsState(
						if (dragging) 1f else .7f
					)
					val progress by animateFloatAsState(
						playerState.progress.coerceIn(0f, 1f)
					)
					val alignment = if (detached) Alignment.BottomStart else Alignment.TopStart
					Box(
						modifier = Modifier
							.matchParentSize()
							.clip(shape)
							.align(alignment),
						contentAlignment = alignment
					) {
						if (!detached) {
							Box(
								Modifier
									.background(MaterialTheme.colorScheme.surfaceBright)
									.fillMaxWidth()
									.height(3.dp)
							)
						}
						Box(
							Modifier
								.background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
								.fillMaxWidth(if (song != null) progress else 0f)
								.height(3.dp)
						)
						Box(
							Modifier
								.fillMaxWidth()
								.height(14.dp)
								.then(
									if (song != null
										&& preferenceManager.miniPlayerProgressStyle == MiniPlayerProgressStyle.Seekable
										&& isInteractive
									)
										Modifier.pointerInput(Unit) {
											detectDragGestures(
												onDragStart = {
													dragging = true
													haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
												},
												onDragEnd = {
													dragging = false
													haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
												}
											) { change, _ ->
												player.seek(
													(change.position.x / size.width.toFloat()).coerceIn(
														0f,
														1f
													)
												)
												change.consume()
											}
										}
									else Modifier
								)
						)
					}
				}
			}
		}
	}
}
