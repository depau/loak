package eu.depau.loak.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.PlaylistKind
import eu.depau.loak.domain.models.PlaylistName
import eu.depau.loak.domain.models.parsePlaylistName
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.badge_ai
import eu.depau.loak.generated.resources.badge_smart
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Sparkle
import eu.depau.loak.icons.outlined.Soundwave
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Instant

/** The name to show for a playlist, and what kind it is (AudioMuse-AI, smart, …). */
@Composable
fun playlistName(name: String?, validUntil: Instant?): PlaylistName =
	parsePlaylistName(name, validUntil != null, koinInject<PreferenceManager>().audioMuseIntegration)

@Composable
fun DomainPlaylist.displayName() = playlistName(name, validUntil)

/** "AI" (filled: AudioMuse-AI rebuilds it; outlined: made with it once) or "Smart". */
@Composable
fun PlaylistBadge(kind: PlaylistKind, modifier: Modifier = Modifier) {
	if (kind == PlaylistKind.Regular) return
	val colors = MaterialTheme.colorScheme
	val (container, content) = when (kind) {
		PlaylistKind.Smart -> colors.primaryContainer to colors.onPrimaryContainer
		PlaylistKind.AudioMuseInstant -> Color.Transparent to colors.tertiary
		else -> colors.tertiaryContainer to colors.onTertiaryContainer
	}
	val shape = RoundedCornerShape(6.dp)
	Row(
		modifier = modifier
			.height(20.dp)
			.clip(shape)
			.background(container)
			.then(
				if (kind == PlaylistKind.AudioMuseInstant) Modifier.border(1.dp, colors.tertiary, shape)
				else Modifier
			)
			.padding(horizontal = 6.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(3.dp)
	) {
		Icon(
			if (kind == PlaylistKind.Smart) Icons.Outlined.Soundwave else Icons.Filled.Sparkle,
			contentDescription = null,
			modifier = Modifier.size(12.dp),
			tint = content
		)
		Text(
			stringResource(if (kind == PlaylistKind.Smart) Res.string.badge_smart else Res.string.badge_ai),
			style = MaterialTheme.typography.labelSmall,
			fontWeight = FontWeight.SemiBold,
			color = content
		)
	}
}

/** [text] with the playlist's badge in front, for subtitles. */
@Composable
fun PlaylistBadgedText(kind: PlaylistKind, text: @Composable () -> Unit) {
	if (kind == PlaylistKind.Regular) return text()
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(6.dp)
	) {
		PlaylistBadge(kind)
		text()
	}
}
