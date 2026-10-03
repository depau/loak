package eu.depau.loak.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
	onSetSongIsStarred: (Boolean) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Column {
			NowPlayingInfoRow(
				songIsStarred = songIsStarred,
				onSetSongIsStarred = onSetSongIsStarred
			)
			NowPlayingProgressBar()
			NowPlayingDurationsRow()
			if (preferenceManager.nowPlayingSongInfo) {
				NowPlayingTechnicalInfoRow()
			}
		}
		Spacer(modifier = Modifier.height(if (isLandscape) 24.dp else 30.dp))
		NowPlayingButtonsRow()
	}
}
