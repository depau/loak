package eu.depau.loak.ui.components.layouts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isExpanded
import eu.depau.loak.domain.discover.ActivePreview
import eu.depau.loak.domain.discover.PreviewManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.MiniPlayerStyle
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_to_lidarr
import eu.depau.loak.generated.resources.action_close_preview
import eu.depau.loak.generated.resources.action_pause
import eu.depau.loak.generated.resources.action_play
import eu.depau.loak.generated.resources.label_preview_tag
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Pause
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.ui.screens.discover.components.rememberLidarrAddFlow
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Stands in for the mini player (or the player bar on expanded windows) while a Discover
 * preview plays, at the same size and place. Tapping it does nothing: previews have no
 * full-screen player.
 */
@Composable
fun PreviewBar(preview: ActivePreview, modifier: Modifier = Modifier, enabled: Boolean = true) {
	val previewManager = koinInject<PreviewManager>()
	val expanded = LocalPlatformContext.current.isExpanded()
	val detached = expanded ||
		koinInject<PreferenceManager>().miniPlayerStyle == MiniPlayerStyle.Detached
	// the player bar and the detached mini player float; the attached one spans the bottom
	val navBarPadding = if (expanded) 0.dp else miniPlayerNavBarPadding()
	val horizontalInsets = NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Horizontal)
	val shape = ContinuousRoundedRectangle(
		when {
			expanded -> 28.dp
			detached -> 16.dp
			else -> 0.dp
		}
	)
	val progress by animateFloatAsState(preview.progress)
	val song = preview.song
	val addFlow = rememberLidarrAddFlow()

	Box(
		modifier = modifier
			.fillMaxWidth()
			.then(
				when {
					expanded -> Modifier.windowInsetsPadding(WindowInsets.navigationBars)
					detached -> Modifier.windowInsetsPadding(horizontalInsets)
					else -> Modifier
				}
			),
		contentAlignment = Alignment.Center
	) {
		Surface(
			modifier = Modifier
				.widthIn(max = if (detached && !expanded) 600.dp else Dp.Unspecified)
				.padding(
					start = if (expanded) 16.dp else if (detached) 12.dp else 0.dp,
					end = if (expanded) 16.dp else if (detached) 12.dp else 0.dp,
					bottom = if (expanded) 16.dp else if (detached) 12.dp + navBarPadding else 0.dp
				)
				.fillMaxWidth()
				.height((if (expanded) 80.dp else 72.dp) + if (detached) 0.dp else navBarPadding),
			shape = shape,
			color = MaterialTheme.colorScheme.primaryContainer,
			shadowElevation = 6.dp
		) {
			Box {
				Row(
					modifier = Modifier
						.fillMaxHeight()
						.then(if (detached) Modifier else Modifier.windowInsetsPadding(horizontalInsets))
						.padding(start = 12.dp, end = 8.dp, bottom = if (detached) 0.dp else navBarPadding),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp)
				) {
					AsyncImage(
						model = song.coverUrl,
						imageLoader = koinInject<ImageLoader>(),
						contentDescription = null,
						contentScale = ContentScale.Crop,
						modifier = Modifier
							.size(48.dp)
							.clip(ContinuousRoundedRectangle(10.dp))
							.background(MaterialTheme.colorScheme.surfaceVariant)
					)
					Column(Modifier.weight(1f)) {
						Text(
							song.title,
							style = MaterialTheme.typography.titleMedium,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis
						)
						Row(
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(6.dp)
						) {
							Text(
								stringResource(Res.string.label_preview_tag),
								style = MaterialTheme.typography.labelSmall,
								color = MaterialTheme.colorScheme.onPrimary,
								maxLines = 1,
								modifier = Modifier
									.background(
										MaterialTheme.colorScheme.primary,
										ContinuousRoundedRectangle(5.dp)
									)
									.padding(horizontal = 6.dp, vertical = 1.dp)
							)
							Text(
								song.artist,
								style = MaterialTheme.typography.bodySmall,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					}
					FilledIconButton(
						onClick = { addFlow.addSong(song) },
						enabled = enabled,
						colors = IconButtonDefaults.filledIconButtonColors(
							containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
						)
					) {
						Icon(Icons.Outlined.Add, stringResource(Res.string.action_add_to_lidarr))
					}
					val playing = preview.isPlaying
					IconButton(
						onClick = { if (playing) previewManager.pause() else previewManager.resume() },
						enabled = enabled
					) {
						Icon(
							if (playing) Icons.Filled.Pause else Icons.Filled.Play,
							stringResource(if (playing) Res.string.action_pause else Res.string.action_play)
						)
					}
					IconButton(onClick = { previewManager.stop() }, enabled = enabled) {
						Icon(Icons.Outlined.Close, stringResource(Res.string.action_close_preview))
					}
				}
				// same edge as the mini player's progress: bottom when floating, top when attached
				Box(
					Modifier
						.align(if (detached) Alignment.BottomStart else Alignment.TopStart)
						.fillMaxWidth(progress)
						.height(3.dp)
						.background(MaterialTheme.colorScheme.primary)
				)
			}
		}
	}
}
