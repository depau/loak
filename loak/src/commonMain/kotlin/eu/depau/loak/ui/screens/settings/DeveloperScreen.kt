package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_ok
import eu.depau.loak.generated.resources.action_test_exception_handler
import eu.depau.loak.generated.resources.info_exception_handler
import eu.depau.loak.generated.resources.option_custom_headers
import eu.depau.loak.generated.resources.title_confirm
import eu.depau.loak.generated.resources.title_developer
import eu.depau.loak.generated.resources.title_logs
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.ui.components.common.SegmentedListButton
import eu.depau.loak.ui.components.common.SegmentedListButtonDefaults
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.dialogs.FormDialog
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem

@Composable
fun SettingsDeveloperScreen() {
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val backStack = LocalNavStack.current
	var exceptionConfirmationShown by rememberSaveable { mutableStateOf(false) }

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
					val count = if (isAndroid) 2 else 1

					SettingsNavItem(
						onClick = dropUnlessResumed {
							backStack.lastOrNull()?.let {
								if (it is Screen.Settings.Developer) {
									backStack.add(Screen.Settings.CustomHeaders)
								}
							}
						},
						content = { Text(stringResource(Res.string.option_custom_headers)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
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
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = count)
						)
					}
				}

				SettingsGroup {
					SegmentedListItem(
						onClick = { exceptionConfirmationShown = true },
						content = { Text(stringResource(Res.string.action_test_exception_handler)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
						colors = SegmentedListItemDefaults.segmentedErrorColors()
					)
				}
			}
		}
	}

	if (exceptionConfirmationShown) {
		FormDialog(
			onDismissRequest = { exceptionConfirmationShown = false },
			title = { Text(stringResource(Res.string.title_confirm)) },
			content = { Text(stringResource(Res.string.info_exception_handler)) },
			buttons = {
				SegmentedListButton(
					modifier = Modifier.fillMaxWidth(),
					onClick = {
						exceptionConfirmationShown = false
						throw Error("Testing exception handler")
					},
					shapes = SegmentedListButtonDefaults.shapes(index = 0, count = 2),
					colors = SegmentedListButtonDefaults.errorColors()
				) {
					Text(stringResource(Res.string.action_ok))
				}
				SegmentedListButton(
					modifier = Modifier.fillMaxWidth(),
					onClick = { exceptionConfirmationShown = false },
					shapes = SegmentedListButtonDefaults.shapes(index = 1, count = 2)
				) {
					Text(stringResource(Res.string.action_cancel))
				}
			},
		)
	}
}
