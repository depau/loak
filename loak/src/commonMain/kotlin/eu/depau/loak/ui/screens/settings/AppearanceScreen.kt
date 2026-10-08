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
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_choose_theme
import eu.depau.loak.generated.resources.option_dynamic_theming
import eu.depau.loak.generated.resources.option_enable_ratings
import eu.depau.loak.generated.resources.subtitle_dynamic_theming
import eu.depau.loak.generated.resources.subtitle_enable_ratings
import eu.depau.loak.generated.resources.title_appearance
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SettingsAppearanceScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_appearance)) },
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
					SegmentedListItem(
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
						onClick = dropUnlessResumed {
							backStack.add(Screen.Settings.Themes)
						},
						content = { Text(stringResource(Res.string.option_choose_theme)) },
						supportingContent = { Text(stringResource(preferenceManager.theme.title)) }
					)
				}

				SettingsGroup {
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_dynamic_theming)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_dynamic_theming)) },
						checked = preferenceManager.dynamicTheming,
						onCheckedChange = { preferenceManager.dynamicTheming = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_enable_ratings)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_enable_ratings)) },
						checked = preferenceManager.enableRatings,
						onCheckedChange = { preferenceManager.enableRatings = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2)
					)
				}
			}
		}
	}
}
