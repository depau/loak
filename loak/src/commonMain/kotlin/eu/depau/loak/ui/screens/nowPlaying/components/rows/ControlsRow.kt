package eu.depau.loak.ui.screens.nowPlaying.components.rows

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.screens.nowPlaying.components.controls.NowPlayingProgressBar

@Composable
fun NowPlayingControlsRow(
	modifier: Modifier = Modifier,
	isLandscape: Boolean,
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit,
	/** False where height is short: the format line goes, and the gaps tighten. */
	showTechInfo: Boolean = true,
	/** Tiny windows: a thumbnail beside the title, and no durations line. */
	thumbnail: (@Composable () -> Unit)? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
	val effectSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		// Keyed on the current index so the title/format/duration rows fade+slide
		// between songs instead of snapping. The rows below read the snapshotted
		// song (not the live playerState) so the outgoing frame keeps showing the
		// previous song during the crossfade.
		AnimatedContent(
			targetState = playerState.currentIndex,
			transitionSpec = {
				(fadeIn(effectSpec) + slideInVertically(slideSpec) { it / 2 })
					.togetherWith(fadeOut(effectSpec) + slideOutVertically(slideSpec) { it / 2 })
			}
		) { index ->
			val song = playerState.queue.getOrNull(index) ?: playerState.currentSong
			Column {
				Row(verticalAlignment = Alignment.CenterVertically) {
					thumbnail?.invoke()
					NowPlayingInfoRow(
						song = song,
						songIsStarred = songIsStarred,
						onSetSongIsStarred = onSetSongIsStarred
					)
				}
				NowPlayingProgressBar()
				if (thumbnail == null) NowPlayingDurationsRow(song = song)
				if (preferenceManager.nowPlayingSongInfo && showTechInfo) {
					NowPlayingTechnicalInfoRow(song = song)
				}
			}
		}
		Spacer(modifier = Modifier.height(if (thumbnail != null) 0.dp else if (!showTechInfo) 12.dp else if (isLandscape) 24.dp else 30.dp))
		// the buttons scale with the row's width: narrower, so they fit a tiny window's height
		Box(if (thumbnail != null) Modifier.padding(horizontal = 32.dp) else Modifier) { NowPlayingButtonsRow() }
	}
}
