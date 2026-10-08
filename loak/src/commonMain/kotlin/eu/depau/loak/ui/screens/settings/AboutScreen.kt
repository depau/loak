package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_app_version
import eu.depau.loak.generated.resources.option_check_for_updates
import eu.depau.loak.generated.resources.subtitle_check_for_updates
import eu.depau.loak.generated.resources.title_about
import eu.depau.loak.generated.resources.title_github
import eu.depau.loak.generated.resources.title_navic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.dialogs.LinkConfirmationDialog
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem

@Composable
fun SettingsAboutScreen() {
	val preferenceManager = koinInject<PreferenceManager>()

	@Suppress("DEPRECATION")
	val clipboard = LocalClipboardManager.current
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_about)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				}
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
				val text = buildString {
					append(platformContext.name + "\n")
					append(
						stringResource(
							Res.string.info_app_version,
							platformContext.appVersion
						)
					)
				}
				SegmentedListItem(
					onClick = { clipboard.setText(AnnotatedString(text)) },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
					content = { Text(text) }
				)
			}

			SettingsGroup {
				SettingsNavItem(
					onClick = { linkToOpen = "https://github.com/depau/loak" },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2),
					content = { Text(stringResource(Res.string.title_github)) }
				)
				SettingsNavItem(
					onClick = { linkToOpen = "https://github.com/ssalggnikool/Navic" },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2),
					content = { Text(stringResource(Res.string.title_navic)) }
				)
			}

			if (platformContext.platformType == PlatformType.Android) {
				SettingsGroup {
					SettingsToggleItem(
						content = { Text(stringResource(Res.string.option_check_for_updates)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_check_for_updates)) },
						checked = preferenceManager.checkForUpdates,
						onCheckedChange = { preferenceManager.checkForUpdates = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
					)
				}
			}
		}
	}

	if (linkToOpen != null) {
		LinkConfirmationDialog(
			linkToOpen = linkToOpen!!,
			onDismissRequest = { linkToOpen = null }
		)
	}
}
