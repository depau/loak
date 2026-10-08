package eu.depau.loak.ui.screens.nowPlaying.components.rows

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.formatSampleRate
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.sheets.AudioQualitySheet

@Composable
fun NowPlayingTechnicalInfoRow(
	song: DomainSong?,
	modifier: Modifier = Modifier,
	onClick: (() -> Unit)? = null
) {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val details = playerState.playbackDetails?.takeIf { it.songId == song?.id }
	var showSheet by rememberSaveable { mutableStateOf(false) }

	val style = MaterialTheme.typography.bodySmall
	val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)

	if (showSheet && song != null) {
		AudioQualitySheet(
			song = song,
			onDismissRequest = { showSheet = false }
		)
	}

	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(horizontal = 16.dp),
		horizontalArrangement = Arrangement.Center
	) {
		Box(contentAlignment = Alignment.Center) {
			Box(
				modifier = Modifier
					.matchParentSize()
					.clip(CircleShape)
					.blur(8.dp)
					.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
			)

			Row(
				modifier = Modifier
					.clip(CircleShape)
					.clickable(role = Role.Button) {
						if (onClick != null) onClick() else if (song != null) showSheet = true
					}
					.padding(horizontal = 12.dp, vertical = 2.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				val format = details?.codec
					?: song?.fileExtension?.uppercase()
					?: "--"

				val sampleRateFormatted = (details?.sampleRateHz ?: song?.sampleRate)
					?.let { formatSampleRate(it) }
					?: "-- kHz"

				val bitrateFormatted = (details?.bitrateKbps ?: song?.bitRate)?.let { "$it kbps" }
					?: "-- kbps"

				Text(
					text = "$format • $sampleRateFormatted • $bitrateFormatted",
					style = style,
					color = color
				)
			}
		}
	}
}
