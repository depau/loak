package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.subtitle_about
import eu.depau.loak.generated.resources.subtitle_appearance
import eu.depau.loak.generated.resources.subtitle_bottom_app_bar
import eu.depau.loak.generated.resources.subtitle_data_storage
import eu.depau.loak.generated.resources.subtitle_developer
import eu.depau.loak.generated.resources.subtitle_now_playing
import eu.depau.loak.generated.resources.subtitle_playback
import eu.depau.loak.generated.resources.subtitle_server
import eu.depau.loak.generated.resources.title_about
import eu.depau.loak.generated.resources.title_appearance
import eu.depau.loak.generated.resources.title_bottom_app_bar
import eu.depau.loak.generated.resources.title_data_storage
import eu.depau.loak.generated.resources.title_developer
import eu.depau.loak.generated.resources.title_now_playing
import eu.depau.loak.generated.resources.title_playback
import eu.depau.loak.generated.resources.title_server
import eu.depau.loak.generated.resources.title_settings
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Dns
import eu.depau.loak.icons.filled.BottomNavigation
import eu.depau.loak.icons.filled.Info
import eu.depau.loak.icons.filled.Palette
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.icons.outlined.Code
import eu.depau.loak.icons.outlined.DataTable
import eu.depau.loak.icons.outlined.Note
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.theme.defaultFont

@Composable
fun SettingsScreen() {
	// a detail screen on top: this list is its pane's left neighbour (or off screen)
	val besideDetail = LocalNavStack.current.lastOrNull() !is Screen.Settings.Root
	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_settings)) },
				trailing = !besideDetail
			)
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp),
			verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
		) {
			SettingsGroup {
				PageRow(
					destination = Screen.Settings.Server,
					icon = Icons.Filled.Dns,
					iconSize = 24.dp,
					title = Res.string.title_server,
					subtitle = Res.string.subtitle_server,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
				)
			}
			SettingsGroup {
				PageRow(
					destination = Screen.Settings.Appearance,
					icon = Icons.Filled.Palette,
					iconSize = 24.dp,
					title = Res.string.title_appearance,
					subtitle = Res.string.subtitle_appearance,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 6)
				)
				PageRow(
					destination = Screen.Settings.NowPlaying,
					icon = Icons.Filled.Play,
					iconSize = 24.dp,
					title = Res.string.title_now_playing,
					subtitle = Res.string.subtitle_now_playing,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 6)
				)
				PageRow(
					destination = Screen.Settings.BottomAppBar,
					icon = Icons.Filled.BottomNavigation,
					iconSize = 24.dp,
					title = Res.string.title_bottom_app_bar,
					subtitle = Res.string.subtitle_bottom_app_bar,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 6)
				)
				PageRow(
					destination = Screen.Settings.Playback,
					icon = Icons.Outlined.Note,
					iconSize = 24.dp,
					title = Res.string.title_playback,
					subtitle = Res.string.subtitle_playback,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 6)
				)
				PageRow(
					destination = Screen.Settings.DataStorage,
					icon = Icons.Outlined.DataTable,
					iconSize = 24.dp,
					title = Res.string.title_data_storage,
					subtitle = Res.string.subtitle_data_storage,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 4, count = 6)
				)
				PageRow(
					destination = Screen.Settings.Developer,
					icon = Icons.Outlined.Code,
					iconSize = 24.dp,
					title = Res.string.title_developer,
					subtitle = Res.string.subtitle_developer,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 5, count = 6)
				)
			}
			SettingsGroup {
				PageRow(
					destination = Screen.Settings.About,
					icon = Icons.Filled.Info,
					title = Res.string.title_about,
					subtitle = Res.string.subtitle_about,
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
				)
			}
		}
	}
}

@Composable
private fun PageRow(
	destination: Screen? = null,
	icon: ImageVector,
	iconSize: Dp = 22.dp,
	title: StringResource,
	subtitle: StringResource,
	shapes: ListItemShapes
) {
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	SegmentedListItem(
		shapes = shapes,
		onClick = dropUnlessResumed {
			destination?.let { destination ->
				backStack.lastOrNull()?.let {
					if (it is Screen.Settings) {
						if (it !is Screen.Settings.Root) {
							backStack.removeLastOrNull()
						}
						backStack.add(destination)
					}
				}
			}
		},
		contentPadding = PaddingValues(if (preferenceManager.theme.isMaterialLike()) 16.dp else 12.dp)
	) {
		Row(
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			if (preferenceManager.theme.isMaterialLike()) {
				Column(
					modifier = Modifier
						.size(40.dp)
						.background(MaterialTheme.colorScheme.primary, CircleShape),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.Center
				) {
					Icon(
						imageVector = icon,
						contentDescription = null,
						modifier = Modifier.size(iconSize),
						tint = MaterialTheme.colorScheme.onPrimary
					)
				}
			} else {
				Icon(
					icon,
					contentDescription = null,
					modifier = Modifier.padding(start = 8.dp, end = 5.dp).size(22.dp),
					tint = MaterialTheme.colorScheme.primary
				)
			}
			Column(
				Modifier.weight(1f),
				verticalArrangement = Arrangement.spacedBy(1.dp)
			) {
				Text(
					stringResource(title),
					style = MaterialTheme.typography.titleSmall.copy(
						fontFamily = defaultFont(100),
						fontSize = 16.sp,
						lineHeight = 16.sp
					)
				)
				Text(
					stringResource(subtitle),
					style = MaterialTheme.typography.bodyMedium.copy(
						fontFamily = defaultFont(grade = 10),
						lineHeight = 14.sp
					),
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			if (!preferenceManager.theme.isMaterialLike()) {
				Icon(
					Icons.Outlined.ChevronForward,
					null,
					tint = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}
