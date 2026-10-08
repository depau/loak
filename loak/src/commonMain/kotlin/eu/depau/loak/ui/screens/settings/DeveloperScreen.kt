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
import org.koin.compose.koinInject
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_crash_reporting
import eu.depau.loak.generated.resources.option_custom_headers
import eu.depau.loak.generated.resources.subtitle_crash_reporting
import eu.depau.loak.generated.resources.title_developer
import eu.depau.loak.generated.resources.title_logs
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import eu.depau.loak.domain.manager.PreferenceManager

@Composable
fun SettingsDeveloperScreen() {
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_developer)) },
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
					val isAndroid = platformContext.platformType == PlatformType.Android
					val count = if (isAndroid) 3 else 2

					SettingsToggleItem(
						checked = preferenceManager.crashReportingEnabled,
						onCheckedChange = { preferenceManager.crashReportingEnabled = it },
						content = { Text(stringResource(Res.string.option_crash_reporting)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_crash_reporting)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
					)

					SettingsNavItem(
						onClick = dropUnlessResumed {
							backStack.lastOrNull()?.let {
								if (it is Screen.Settings.Developer) {
									backStack.add(Screen.Settings.CustomHeaders)
								}
							}
						},
						content = { Text(stringResource(Res.string.option_custom_headers)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = count)
					)

					if (isAndroid) {
						SettingsNavItem(
							onClick = dropUnlessResumed {
								backStack.lastOrNull()?.let {
									if (it is Screen.Settings.Developer) {
										backStack.add(Screen.Settings.Logs)
									}
								}
							},
							content = { Text(stringResource(Res.string.title_logs)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = count)
						)
					}
				}
			}
		}
	}
}
