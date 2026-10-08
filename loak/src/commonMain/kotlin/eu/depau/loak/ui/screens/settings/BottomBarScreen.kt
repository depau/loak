package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_bottom_bar_collapse_mode
import eu.depau.loak.generated.resources.option_mini_player_style
import eu.depau.loak.generated.resources.option_swipe_to_skip
import eu.depau.loak.generated.resources.title_bottom_app_bar
import eu.depau.loak.generated.resources.title_mini_player
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.BottomBarCollapseMode
import eu.depau.loak.domain.models.settings.MiniPlayerStyle
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsChoiceItem
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.generated.resources.info_tabs_moved
import androidx.compose.material3.TextButton
import androidx.lifecycle.compose.dropUnlessResumed

@Composable
fun BottomBarScreen() {
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_bottom_app_bar)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				}
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				SettingsGroup {
					SettingsChoiceItem(
						choices = BottomBarCollapseMode.entries.toImmutableList(),
						selectedChoice = preferenceManager.bottomBarCollapseMode,
						onChoiceSelected = { preferenceManager.bottomBarCollapseMode = it },
						content = { Text(stringResource(Res.string.option_bottom_bar_collapse_mode)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
					)
				}
				TextButton(onClick = dropUnlessResumed { backStack.add(Screen.Settings.Tabs) }) {
					Text(stringResource(Res.string.info_tabs_moved))
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_mini_player)) }) {
					SettingsChoiceItem(
						choices = MiniPlayerStyle.entries.toImmutableList(),
						selectedChoice = preferenceManager.miniPlayerStyle,
						onChoiceSelected = { preferenceManager.miniPlayerStyle = it },
						content = { Text(stringResource(Res.string.option_mini_player_style)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_swipe_to_skip)) },
						checked = preferenceManager.swipeToSkip,
						onCheckedChange = { preferenceManager.swipeToSkip = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2)
					)
				}
			}
		}
	}
}
