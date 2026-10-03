package eu.depau.loak.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
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
	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Column {
			Row(verticalAlignment = Alignment.CenterVertically) {
				thumbnail?.invoke()
				NowPlayingInfoRow(
					songIsStarred = songIsStarred,
					onSetSongIsStarred = onSetSongIsStarred
				)
			}
			NowPlayingProgressBar()
			if (thumbnail == null) NowPlayingDurationsRow()
			if (preferenceManager.nowPlayingSongInfo && showTechInfo) {
				NowPlayingTechnicalInfoRow()
			}
		}
		Spacer(modifier = Modifier.height(if (thumbnail != null) 0.dp else if (!showTechInfo) 12.dp else if (isLandscape) 24.dp else 30.dp))
		// the buttons scale with the row's width: narrower, so they fit a tiny window's height
		Box(if (thumbnail != null) Modifier.padding(horizontal = 32.dp) else Modifier) { NowPlayingButtonsRow() }
	}
}
