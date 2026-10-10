package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_brainz
import eu.depau.loak.generated.resources.info_deezer_off_fallback
import eu.depau.loak.generated.resources.info_use_deezer
import eu.depau.loak.generated.resources.title_always_on
import eu.depau.loak.generated.resources.title_brainz
import eu.depau.loak.generated.resources.title_discover_sources
import eu.depau.loak.generated.resources.title_use_deezer
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.brand.Deezer
import eu.depau.loak.icons.brand.Musicbrainz
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Where Discover's search, previews and charts come from: Deezer, or MusicBrainz alone. */
@Composable
fun DiscoverSearchSettingsScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	SettingsScaffold(stringResource(Res.string.title_discover_sources)) {
		SettingsGroup {
			SettingsToggleItem(
				checked = preferenceManager.deezerEnabled,
				onCheckedChange = { preferenceManager.deezerEnabled = it },
				shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
				leadingContent = { BrandChip(Icons.Brand.Deezer) },
				content = { Text(stringResource(Res.string.title_use_deezer)) },
				supportingContent = { Text(stringResource(Res.string.info_use_deezer)) }
			)
			Text(
				stringResource(Res.string.info_deezer_off_fallback),
				modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}

		SettingsGroup(title = { Text(stringResource(Res.string.title_always_on)) }) {
			Surface(
				shape = SegmentedListItemDefaults.OverrideShape,
				color = MaterialTheme.colorScheme.surfaceContainer
			) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(14.dp),
					horizontalArrangement = Arrangement.spacedBy(14.dp)
				) {
					BrandChip(Icons.Brand.Musicbrainz)
					Column(Modifier.weight(1f)) {
						Text(stringResource(Res.string.title_brainz), style = MaterialTheme.typography.bodyLarge)
						Text(
							stringResource(Res.string.info_brainz),
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSurfaceVariant
						)
					}
				}
			}
		}
	}
}

@Composable
private fun BrandChip(icon: ImageVector) {
	Box(
		modifier = Modifier
			.size(40.dp)
			.background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
		contentAlignment = Alignment.Center
	) {
		Icon(icon, null, Modifier.size(22.dp))
	}
}
