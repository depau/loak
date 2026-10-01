package eu.depau.loak.ui.components.layouts

import androidx.compose.ui.text.style.TextOverflow
import eu.depau.loak.ui.util.pickedUpFromLabel
import androidx.compose.foundation.background
import eu.depau.loak.icons.outlined.VolumeUp
import eu.depau.loak.generated.resources.action_volume
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalQueuePaneOpen
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_next_song
import eu.depau.loak.generated.resources.action_pause
import eu.depau.loak.generated.resources.action_play
import eu.depau.loak.generated.resources.action_previous_song
import eu.depau.loak.generated.resources.action_queue
import eu.depau.loak.generated.resources.action_repeat
import eu.depau.loak.generated.resources.action_shuffle
import eu.depau.loak.generated.resources.action_star
import eu.depau.loak.generated.resources.info_not_playing
import eu.depau.loak.generated.resources.title_now_playing
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Note
import eu.depau.loak.icons.filled.Pause
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.filled.RepeatOn
import eu.depau.loak.icons.filled.RepeatOneOn
import eu.depau.loak.icons.filled.ShuffleOn
import eu.depau.loak.icons.filled.SkipNext
import eu.depau.loak.icons.filled.SkipPrevious
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.KeyboardArrowDown
import eu.depau.loak.icons.outlined.List
import eu.depau.loak.icons.outlined.Repeat
import eu.depau.loak.icons.outlined.Shuffle
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import eu.depau.loak.util.toHoursMinutesSeconds
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

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
	val queuePaneOpen = LocalQueuePaneOpen.current
	val volumeLabel = stringResource(Res.string.action_volume)
	val isRadio = song?.id?.startsWith("radio_") == true
	val interactive = enabled && song != null

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
	val openPlayer = dropUnlessResumed {
		if (!backStack.contains(Screen.NowPlaying)) backStack.add(Screen.NowPlaying)
	}
	val shape = ContinuousRoundedRectangle(28.dp)

	Surface(
		modifier = modifier
			.windowInsetsPadding(WindowInsets.navigationBars)
			.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
			.fillMaxWidth()
			.height(80.dp),
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
						Icon(
							if (playerState.isPaused) Icons.Filled.Play else Icons.Filled.Pause,
							stringResource(
								if (playerState.isPaused) Res.string.action_play else Res.string.action_pause
							)
						)
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
				Row(
					modifier = Modifier.weight(1f),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End)
				) {
					if (song != null && !isRadio) {
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
					}
					IconToggleButton(
						checked = queuePaneOpen.value,
						onCheckedChange = { queuePaneOpen.value = it },
						colors = IconButtonDefaults.iconToggleButtonColors(
							checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
							checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer
						)
					) {
						Icon(Icons.Outlined.List, stringResource(Res.string.action_queue))
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
			if (song != null) {
				Box(
					Modifier
						.align(Alignment.BottomStart)
						.padding(horizontal = 24.dp)
						.fillMaxWidth()
						.height(3.dp)
						.background(MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
				) {
					Box(
						Modifier
							.fillMaxWidth(playerState.progress.coerceIn(0f, 1f))
							.height(3.dp)
							.background(MaterialTheme.colorScheme.primary)
					)
				}
			}
		}
	}
}
