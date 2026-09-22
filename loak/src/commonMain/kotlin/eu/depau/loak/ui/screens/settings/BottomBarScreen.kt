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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_bottom_bar_collapse_mode
import eu.depau.loak.generated.resources.option_bottom_bar_visibility_mode
import eu.depau.loak.generated.resources.option_hide_bottom_bar_if_idle
import eu.depau.loak.generated.resources.option_mini_player_progress_style
import eu.depau.loak.generated.resources.option_mini_player_style
import eu.depau.loak.generated.resources.option_navigation_bar_label_visibility
import eu.depau.loak.generated.resources.option_navigation_bar_style
import eu.depau.loak.generated.resources.option_navigation_bar_tabs
import eu.depau.loak.generated.resources.option_swipe_to_skip
import eu.depau.loak.generated.resources.title_bottom_app_bar
import eu.depau.loak.generated.resources.title_mini_player
import eu.depau.loak.generated.resources.title_navigation_bar
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isLandscape
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.BottomBarCollapseMode
import eu.depau.loak.domain.models.settings.BottomBarVisibilityMode
import eu.depau.loak.domain.models.settings.MiniPlayerProgressStyle
import eu.depau.loak.domain.models.settings.MiniPlayerStyle
import eu.depau.loak.domain.models.settings.NavigationBarLabelVisibility
import eu.depau.loak.domain.models.settings.NavigationBarStyle
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsChoiceItem
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import eu.depau.loak.ui.screens.settings.dialogs.NavtabsDialog

@Composable
fun BottomBarScreen() {
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	var tabsDialogOpen by rememberSaveable { mutableStateOf(false) }
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
					val count = if (!platformContext.isLandscape()) 2 else 1
					SettingsChoiceItem(
						choices = BottomBarCollapseMode.entries.toImmutableList(),
						selectedChoice = preferenceManager.bottomBarCollapseMode,
						onChoiceSelected = { preferenceManager.bottomBarCollapseMode = it },
						content = { Text(stringResource(Res.string.option_bottom_bar_collapse_mode)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
					)

					if (!platformContext.isLandscape()) {
						SettingsChoiceItem(
							choices = BottomBarVisibilityMode.entries.toImmutableList(),
							selectedChoice = preferenceManager.bottomBarVisibilityMode,
							onChoiceSelected = { preferenceManager.bottomBarVisibilityMode = it },
							content = { Text(stringResource(Res.string.option_bottom_bar_visibility_mode)) },
							label = { stringResource(it.displayName) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = count)
						)
					}
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_navigation_bar)) }) {
					SettingsChoiceItem(
						choices = NavigationBarStyle.entries.toImmutableList(),
						selectedChoice = preferenceManager.navigationBarStyle,
						onChoiceSelected = { preferenceManager.navigationBarStyle = it },
						content = { Text(stringResource(Res.string.option_navigation_bar_style)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 3)
					)

					SettingsChoiceItem(
						choices = NavigationBarLabelVisibility.entries.toImmutableList(),
						selectedChoice = preferenceManager.navigationBarLabelVisibility,
						onChoiceSelected = { preferenceManager.navigationBarLabelVisibility = it },
						content = { Text(stringResource(Res.string.option_navigation_bar_label_visibility)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 3)
					)

					SettingsNavItem(
						onClick = { tabsDialogOpen = true },
						content = { Text(stringResource(Res.string.option_navigation_bar_tabs)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 3)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_mini_player)) }) {
					SettingsChoiceItem(
						choices = MiniPlayerStyle.entries.toImmutableList(),
						selectedChoice = preferenceManager.miniPlayerStyle,
						onChoiceSelected = { preferenceManager.miniPlayerStyle = it },
						content = { Text(stringResource(Res.string.option_mini_player_style)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 4)
					)
					SettingsChoiceItem(
						choices = MiniPlayerProgressStyle.entries.toImmutableList(),
						selectedChoice = preferenceManager.miniPlayerProgressStyle,
						onChoiceSelected = { preferenceManager.miniPlayerProgressStyle = it },
						content = { Text(stringResource(Res.string.option_mini_player_progress_style)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 4)
					)
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_swipe_to_skip)) },
						checked = preferenceManager.swipeToSkip,
						onCheckedChange = { preferenceManager.swipeToSkip = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 4)
					)
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_hide_bottom_bar_if_idle)) },
						checked = preferenceManager.hideIfIdle,
						onCheckedChange = { preferenceManager.hideIfIdle = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 4)
					)
				}
			}
		}
	}

	NavtabsDialog(
		presented = tabsDialogOpen,
		onDismissRequest = { tabsDialogOpen = false }
	)
}
