package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.NavidromeManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.settings.OfflineMode
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_offline_mode
import eu.depau.loak.generated.resources.subtitle_offline_mode_toggle
import eu.depau.loak.generated.resources.subtitle_settings_about
import eu.depau.loak.generated.resources.subtitle_settings_advanced
import eu.depau.loak.generated.resources.subtitle_settings_appearance
import eu.depau.loak.generated.resources.subtitle_settings_downloads
import eu.depau.loak.generated.resources.subtitle_settings_playback
import eu.depau.loak.generated.resources.subtitle_settings_player
import eu.depau.loak.generated.resources.subtitle_settings_sound
import eu.depau.loak.generated.resources.subtitle_settings_tabs
import eu.depau.loak.generated.resources.title_about_loak
import eu.depau.loak.generated.resources.title_advanced
import eu.depau.loak.generated.resources.title_appearance
import eu.depau.loak.generated.resources.title_downloads_storage
import eu.depau.loak.generated.resources.title_playback
import eu.depau.loak.generated.resources.title_player
import eu.depau.loak.generated.resources.title_settings
import eu.depau.loak.generated.resources.title_sound_quality
import eu.depau.loak.generated.resources.title_tabs
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.BottomNavigation
import eu.depau.loak.icons.filled.Info
import eu.depau.loak.icons.filled.Palette
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.icons.outlined.Code
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.Queue
import eu.depau.loak.icons.outlined.Soundwave
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import eu.depau.loak.ui.theme.defaultFont
import io.ktor.http.Url
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SettingsScreen() {
	// a detail screen on top: this list is its pane's left neighbour (or off screen)
	val backStack = LocalNavStack.current
	val besideDetail = backStack.lastOrNull() !is Screen.Settings.Root
	val isTab = backStack.firstOrNull() is Screen.Settings
	Scaffold(
		topBar = {
			NestedTopBar(
				{ Text(stringResource(Res.string.title_settings)) },
				// opened as a tab from the rail: nothing to go back to
				navigationAction = { if (!isTab) NestedTopBarDefaults.NavigationAction() },
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
			// at hand; Downloads & storage has the automatic choices
			SettingsGroup {
				val preferenceManager = koinInject<PreferenceManager>()
				SettingsToggleItem(
					checked = preferenceManager.offlineMode == OfflineMode.Forced,
					onCheckedChange = {
						preferenceManager.offlineMode = if (it) OfflineMode.Forced else OfflineMode.Auto
					},
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
					supportingContent = {
						Text(stringResource(Res.string.subtitle_offline_mode_toggle))
					},
					content = { Text(stringResource(Res.string.option_offline_mode)) }
				)
			}
			SettingsGroup {
				AccountRow(SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1))
			}
			SettingsGroup {
				PageRow(Screen.Settings.Appearance, Icons.Filled.Palette,
					Res.string.title_appearance, Res.string.subtitle_settings_appearance, 0, 3)
				PageRow(Screen.Settings.Player, Icons.Filled.Play,
					Res.string.title_player, Res.string.subtitle_settings_player, 1, 3)
				PageRow(Screen.Settings.Tabs, Icons.Filled.BottomNavigation,
					Res.string.title_tabs, Res.string.subtitle_settings_tabs, 2, 3)
			}
			SettingsGroup {
				PageRow(Screen.Settings.Playback, Icons.Outlined.Queue,
					Res.string.title_playback, Res.string.subtitle_settings_playback, 0, 3)
				PageRow(Screen.Settings.Sound, Icons.Outlined.Soundwave,
					Res.string.title_sound_quality, Res.string.subtitle_settings_sound, 1, 3)
				PageRow(Screen.Settings.DataStorage, Icons.Outlined.Download,
					Res.string.title_downloads_storage, Res.string.subtitle_settings_downloads, 2, 3)
			}
			SettingsGroup {
				PageRow(Screen.Settings.Advanced, Icons.Outlined.Code,
					Res.string.title_advanced, Res.string.subtitle_settings_advanced, 0, 2)
				PageRow(Screen.Settings.About, Icons.Filled.Info,
					Res.string.title_about_loak, Res.string.subtitle_settings_about, 1, 2)
			}
		}
	}
}

/** Opens a settings page, swapping out whichever page is open beside the list. */
@Composable
private fun rememberOpenPage(destination: Screen.Settings): () -> Unit {
	val backStack = LocalNavStack.current
	return dropUnlessResumed {
		backStack.lastOrNull()?.let {
			if (it is Screen.Settings) {
				if (it !is Screen.Settings.Root) {
					backStack.removeLastOrNull()
				}
				backStack.add(destination)
			}
		}
	}
}

/** Highlights the row of the page open beside the list (two-pane windows). */
@Composable
private fun pageRowColors() = SegmentedListItemDefaults.segmentedColors(
	selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
	selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
	selectedSupportingContentColor = MaterialTheme.colorScheme.onSecondaryContainer
)

@Composable
private fun isOpen(destination: Screen.Settings) = LocalNavStack.current.lastOrNull() == destination

/** Who's signed in where; opens Server & account. */
@Composable
private fun AccountRow(shapes: ListItemShapes) {
	val sessionManager = koinInject<SessionManager>()
	val navidrome = koinInject<NavidromeManager>()
	val software by produceState<String?>(null) {
		value = navidrome.serverInfo().let { info ->
			listOfNotNull(info.type?.replaceFirstChar { it.uppercase() }, info.version)
				.joinToString(" ").ifBlank { null }
		}
	}
	val host = runCatching { Url(sessionManager.instanceUrl).host }.getOrNull()
		?: sessionManager.instanceUrl
	SegmentedListItem(
		shapes = shapes,
		onClick = rememberOpenPage(Screen.Settings.Server),
		selected = isOpen(Screen.Settings.Server),
		colors = pageRowColors(),
		contentPadding = PaddingValues(16.dp)
	) {
		Row(
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Box(
				Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
				contentAlignment = Alignment.Center
			) {
				Text(
					sessionManager.username.take(1).uppercase(),
					color = MaterialTheme.colorScheme.onPrimary,
					style = MaterialTheme.typography.titleMedium
				)
			}
			PageRowText(
				title = sessionManager.username,
				subtitle = listOfNotNull(host, software).joinToString(" · "),
				modifier = Modifier.weight(1f),
				selected = isOpen(Screen.Settings.Server)
			)
			Icon(Icons.Outlined.ChevronForward, null)
		}
	}
}

@Composable
private fun PageRow(
	destination: Screen.Settings,
	icon: ImageVector,
	title: StringResource,
	subtitle: StringResource,
	index: Int,
	count: Int
) {
	SegmentedListItem(
		shapes = SegmentedListItemDefaults.segmentedShapes(index = index, count = count),
		onClick = rememberOpenPage(destination),
		selected = isOpen(destination),
		colors = pageRowColors(),
		contentPadding = PaddingValues(16.dp)
	) {
		Row(
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
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
					modifier = Modifier.size(24.dp),
					tint = MaterialTheme.colorScheme.onPrimary
				)
			}
			PageRowText(
				stringResource(title),
				stringResource(subtitle),
				Modifier.weight(1f),
				selected = isOpen(destination)
			)
		}
	}
}

@Composable
private fun PageRowText(
	title: String,
	subtitle: String,
	modifier: Modifier = Modifier,
	selected: Boolean = false
) {
	Column(
		modifier,
		verticalArrangement = Arrangement.spacedBy(1.dp)
	) {
		Text(
			title,
			style = MaterialTheme.typography.titleSmall.copy(
				fontFamily = defaultFont(100),
				fontSize = 16.sp,
				lineHeight = 16.sp
			)
		)
		Text(
			subtitle,
			style = MaterialTheme.typography.bodyMedium.copy(
				fontFamily = defaultFont(grade = 10),
				lineHeight = 14.sp
			),
			color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
			else MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}
