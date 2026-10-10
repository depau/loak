package eu.depau.loak.ui.screens.discover.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.domain.manager.DiscoverBadgeState
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.badge_downloading
import eu.depau.loak.generated.resources.badge_in_library
import eu.depau.loak.generated.resources.badge_in_lidarr
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Check
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Where a Discover song, album or artist stands; re-resolved whenever Lidarr's catalog or the
 * library changes.
 */
@Composable
fun rememberDiscoverStatus(item: Any): DiscoverBadgeState {
	val lidarr = koinInject<LidarrManager>()
	val catalog by lidarr.catalog.collectAsState()
	val library by koinInject<LibraryMatcher>().generation.collectAsState()
	val status by produceState<DiscoverBadgeState>(DiscoverBadgeState.None, item, catalog, library) {
		value = when (item) {
			is DiscoverSong -> lidarr.statusFor(item)
			is DiscoverAlbum -> lidarr.statusFor(item)
			is DiscoverArtist -> lidarr.statusFor(item)
			else -> DiscoverBadgeState.None
		}
	}
	return status
}

/** "In library", "In Lidarr" or "Downloading 40%", inline after a Discover item's metadata. */
@Composable
fun DiscoverBadge(state: DiscoverBadgeState, modifier: Modifier = Modifier) {
	val colors = MaterialTheme.colorScheme
	val (text, container, content) = when (state) {
		is DiscoverBadgeState.InLibrary ->
			Triple(stringResource(Res.string.badge_in_library), colors.primaryContainer, colors.onPrimaryContainer)
		DiscoverBadgeState.InLidarr ->
			Triple(stringResource(Res.string.badge_in_lidarr), Color.Transparent, colors.onSurfaceVariant)
		is DiscoverBadgeState.Downloading -> Triple(
			stringResource(Res.string.badge_downloading, (state.progress * 100).toInt()),
			colors.tertiaryContainer,
			colors.onTertiaryContainer
		)
		DiscoverBadgeState.None -> return
	}
	Surface(
		modifier = modifier,
		shape = RoundedCornerShape(6.dp),
		color = container,
		contentColor = content,
		border = if (state == DiscoverBadgeState.InLidarr) BorderStroke(1.dp, colors.outline) else null
	) {
		Row(
			modifier = Modifier.height(20.dp).padding(horizontal = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(3.dp)
		) {
			if (state is DiscoverBadgeState.InLibrary) Icon(Icons.Outlined.Check, null, Modifier.size(12.dp))
			Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
		}
	}
}
