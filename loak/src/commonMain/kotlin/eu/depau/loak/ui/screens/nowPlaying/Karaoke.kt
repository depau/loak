package eu.depau.loak.ui.screens.nowPlaying

import eu.depau.loak.ui.util.escapeToDismiss
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_exit_karaoke
import eu.depau.loak.generated.resources.action_next_song
import eu.depau.loak.generated.resources.action_previous_song
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.SkipNext
import eu.depau.loak.icons.filled.SkipPrevious
import eu.depau.loak.icons.outlined.FullscreenExit
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.BlendBackground
import eu.depau.loak.ui.screens.lyrics.LyricsScreen
import eu.depau.loak.ui.screens.lyrics.components.LocalLyricsFontSize
import eu.depau.loak.ui.screens.lyrics.components.LocalLyricsCentered
import androidx.compose.foundation.layout.fillMaxHeight
import eu.depau.loak.ui.screens.nowPlaying.components.controls.NowPlayingProgressBar
import eu.depau.loak.ui.util.Immersive
import eu.depau.loak.ui.util.KeepScreenOn
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds
import eu.depau.loak.ui.components.common.PlayPauseIcon

/**
 * The lyrics, full screen: large lines over the blurred cover, the system bars hidden and the
 * screen kept on. Tapping shows the controls (exit, seek, transport) for a few seconds. Back
 * returns to the lyrics tab.
 */
@Composable
fun Karaoke(state: PlayerSheetState) {
	AnimatedVisibility(
		visible = state.karaoke,
		enter = fadeIn() + scaleIn(initialScale = .92f),
		exit = fadeOut() + scaleOut(targetScale = .92f)
	) {
		val player = koinInject<MediaPlayerViewModel>()
		val playerState by player.uiState.collectAsStateWithLifecycle()
		val song = playerState.currentSong

		Immersive()
		KeepScreenOn()
		NavigationBackHandler(
			state = rememberNavigationEventState(NavigationEventInfo.None),
			isBackEnabled = state.karaoke,
			onBackCompleted = { state.karaoke = false }
		)

		var controls by remember { mutableStateOf(true) }
		var touches by remember { mutableIntStateOf(0) }
		LaunchedEffect(controls, touches) {
			if (controls) {
				delay(3.seconds)
				controls = false
			}
		}

		BoxWithConstraints(
			Modifier
				.fillMaxSize()
				// going full screen (desktop) can drop the app's keyboard focus: take it here
				.escapeToDismiss { state.karaoke = false }
				.background(Color.Black)
				// any tap shows the controls (a tap on a line also seeks to it); taps while
				// they show keep them up
				.pointerInput(Unit) {
					awaitPointerEventScope {
						while (true) {
							val event = awaitPointerEvent(PointerEventPass.Initial)
							if (event.type == PointerEventType.Release) {
								if (controls) touches++ else controls = true
							}
						}
					}
				}
		) {
			BlendBackground(coverArtId = song?.coverArtId, isPaused = playerState.isPaused)
			Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f)))
			// as large as the window allows: about a seventh of the height, or 11 lines' width
			val wide = maxWidth >= 600.dp
			val size = minOf(maxWidth.value / 11f, maxHeight.value / 7f).coerceIn(20f, 64f).sp
			Box(
				Modifier
					.align(Alignment.Center)
					.fillMaxHeight()
					.windowInsetsPadding(WindowInsets.safeDrawing)
					.padding(top = 24.dp, start = 24.dp, end = 24.dp)
					.widthIn(max = 960.dp)
					.fillMaxWidth()
			) {
				// centred where lines are short next to the window's width
				CompositionLocalProvider(LocalLyricsFontSize provides size, LocalLyricsCentered provides wide) {
					LyricsScreen(song)
				}
			}

			AnimatedVisibility(
				visible = controls,
				enter = fadeIn(),
				exit = fadeOut(),
				modifier = Modifier.fillMaxSize()
			) {
				Box(Modifier.fillMaxSize()) {
					Row(
						Modifier
							.fillMaxWidth()
							.background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .6f), Color.Transparent)))
							.windowInsetsPadding(WindowInsets.safeDrawing)
							.padding(horizontal = 12.dp, vertical = 8.dp),
						verticalAlignment = Alignment.CenterVertically,
						horizontalArrangement = Arrangement.spacedBy(12.dp)
					) {
						FilledIconButton(
							onClick = { state.karaoke = false },
							colors = IconButtonDefaults.filledTonalIconButtonColors()
						) {
							Icon(Icons.Outlined.FullscreenExit, stringResource(Res.string.action_exit_karaoke))
						}
						Column(Modifier.weight(1f)) {
							Text(song?.title ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
							Text(song?.artistName ?: "", color = Color.White.copy(alpha = .75f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
						}
					}
					Column(
						Modifier
							.align(Alignment.BottomCenter)
							.fillMaxWidth()
							.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .65f))))
							.windowInsetsPadding(WindowInsets.safeDrawing)
							.padding(horizontal = 16.dp, vertical = 12.dp),
						horizontalAlignment = Alignment.CenterHorizontally
					) {
						Box(Modifier.widthIn(max = 640.dp)) { NowPlayingProgressBar() }
						Spacer(Modifier.height(4.dp))
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(24.dp)
						) {
							IconButton(onClick = player::previous) {
								Icon(Icons.Filled.SkipPrevious, stringResource(Res.string.action_previous_song), tint = Color.White)
							}
							FilledIconButton(onClick = player::togglePlay) {
								PlayPauseIcon(playerState)
							}
							IconButton(onClick = player::next) {
								Icon(Icons.Filled.SkipNext, stringResource(Res.string.action_next_song), tint = Color.White)
							}
						}
					}
				}
			}
		}
	}
}
