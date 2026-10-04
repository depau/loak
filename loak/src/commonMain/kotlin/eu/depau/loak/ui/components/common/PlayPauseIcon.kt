package eu.depau.loak.ui.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_pause
import eu.depau.loak.generated.resources.action_play
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Pause
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.ui.util.playPauseIconPainter
import org.jetbrains.compose.resources.stringResource

/**
 * The play/pause icon for [state], ringed by a spinner filling the enclosing button while the
 * player is buffering, so a stalled track doesn't look frozen. [animated] uses the platform's
 * animated icon where there is one.
 */
@Composable
fun PlayPauseIcon(state: PlayerUiState, modifier: Modifier = Modifier, animated: Boolean = false) {
	val description = stringResource(
		if (state.isPaused) Res.string.action_play else Res.string.action_pause
	)
	Box(contentAlignment = Alignment.Center) {
		if (state.isLoading) CircularProgressIndicator(
			Modifier.fillMaxSize(),
			color = LocalContentColor.current,
			trackColor = Color.Transparent,
			strokeWidth = 3.dp
		)
		val painter = if (animated) playPauseIconPainter(state.isPaused) else null
		if (painter != null) {
			Icon(painter, description, modifier)
		} else {
			val icon = if (state.isPaused) Icons.Filled.Play else Icons.Filled.Pause
			Icon(icon, description, modifier)
		}
	}
}
