package eu.depau.loak.ui.screens.settings

import eu.depau.loak.domain.manager.NavidromeManager
import eu.depau.loak.domain.manager.ServerInfo
import androidx.compose.runtime.produceState
import eu.depau.loak.generated.resources.option_server_software
import eu.depau.loak.generated.resources.option_smart_playlists
import eu.depau.loak.generated.resources.info_smart_playlists_available
import eu.depau.loak.generated.resources.info_smart_playlists_unavailable
import eu.depau.loak.generated.resources.title_server_features
import eu.depau.loak.generated.resources.option_audiomuse_integration
import eu.depau.loak.generated.resources.subtitle_audiomuse_integration
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.russhwolf.settings.Settings
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.manager.LoginManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_ok
import eu.depau.loak.generated.resources.action_save_and_reconnect
import eu.depau.loak.generated.resources.info_device_name
import eu.depau.loak.generated.resources.info_device_name_default
import eu.depau.loak.generated.resources.notice_server_saved
import eu.depau.loak.generated.resources.option_account_instance
import eu.depau.loak.generated.resources.option_account_password
import eu.depau.loak.generated.resources.option_account_username
import eu.depau.loak.generated.resources.option_custom_headers
import eu.depau.loak.generated.resources.option_device_name
import eu.depau.loak.generated.resources.subtitle_custom_headers
import eu.depau.loak.generated.resources.title_connection
import eu.depau.loak.generated.resources.title_server
import eu.depau.loak.generated.resources.title_this_device
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.util.systemDeviceName
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Server address and account, plus how this device presents itself to the server. */
@Composable
fun SettingsServerScreen() {
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val sessionManager = koinInject<SessionManager>()
	val loginManager = koinInject<LoginManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val settings = koinInject<Settings>()
	val scope = rememberCoroutineScope()

	val savedPassword = settings.getString("password", "")
	val instance = rememberTextFieldState(sessionManager.instanceUrl)
	val username = rememberTextFieldState(sessionManager.username)
	val password = rememberTextFieldState(savedPassword)
	var busy by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }

	val changed = instance.text.toString() != sessionManager.instanceUrl
		|| username.text.toString() != sessionManager.username
		|| password.text.toString() != savedPassword

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_server)) },
				navigationAction = {
					if (!hideBack) NestedTopBarDefaults.NavigationAction()
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
			SettingsGroup(title = { Text(stringResource(Res.string.title_connection)) }) {
				OutlinedTextField(
					state = instance,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(Res.string.option_account_instance)) },
					lineLimits = TextFieldLineLimits.SingleLine,
					enabled = !busy,
					isError = error != null,
					keyboardOptions = KeyboardOptions(
						autoCorrectEnabled = false,
						keyboardType = KeyboardType.Uri
					)
				)
				OutlinedTextField(
					state = username,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(Res.string.option_account_username)) },
					lineLimits = TextFieldLineLimits.SingleLine,
					enabled = !busy,
					isError = error != null,
					keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
				)
				OutlinedSecureTextField(
					state = password,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(Res.string.option_account_password)) },
					enabled = !busy,
					isError = error != null,
					supportingText = error?.let { { Text(it) } },
					keyboardOptions = KeyboardOptions(
						autoCorrectEnabled = false,
						keyboardType = KeyboardType.Password
					)
				)
				Button(
					modifier = Modifier.align(Alignment.End).padding(top = 6.dp),
					enabled = changed && !busy
						&& instance.text.isNotBlank() && username.text.isNotBlank(),
					onClick = {
						busy = true
						error = null
						scope.launch {
							try {
								loginManager.reconnect(
									instance.text.toString(),
									username.text.toString(),
									password.text.toString()
								)
								snackBarManager.notify(Res.string.notice_server_saved)
							} catch (e: Exception) {
								error = e.message
							}
							busy = false
						}
					}
				) {
					Text(stringResource(Res.string.action_save_and_reconnect))
				}
				CustomHeadersItem()
			}

			ThisDeviceGroup()

			ServerFeaturesGroup()
		}
	}
}

@Composable
private fun ServerFeaturesGroup() {
	val preferenceManager = koinInject<PreferenceManager>()
	val navidrome = koinInject<NavidromeManager>()
	val info by produceState<ServerInfo?>(null) { value = navidrome.serverInfo() }
	val known = info?.type != null
	val count = if (known) 3 else 2
	SettingsGroup(title = { Text(stringResource(Res.string.title_server_features)) }) {
		info?.takeIf { known }?.let { info ->
			SegmentedListItem(
				onClick = {},
				shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count),
				content = { Text(stringResource(Res.string.option_server_software)) },
				supportingContent = {
					Text(listOfNotNull(info.type?.replaceFirstChar { it.uppercase() }, info.version).joinToString(" "))
				}
			)
		}
		SettingsToggleItem(
			content = { Text(stringResource(Res.string.option_audiomuse_integration)) },
			supportingContent = { Text(stringResource(Res.string.subtitle_audiomuse_integration)) },
			checked = preferenceManager.audioMuseIntegration,
			onCheckedChange = { preferenceManager.audioMuseIntegration = it },
			shapes = SegmentedListItemDefaults.segmentedShapes(index = count - 2, count = count)
		)
		SegmentedListItem(
			onClick = {},
			shapes = SegmentedListItemDefaults.segmentedShapes(index = count - 1, count = count),
			content = { Text(stringResource(Res.string.option_smart_playlists)) },
			supportingContent = {
				Text(stringResource(
					if (info?.canEditSmartPlaylists == true) Res.string.info_smart_playlists_available
					else Res.string.info_smart_playlists_unavailable
				))
			}
		)
	}
}

/** Opens the custom HTTP headers sent to the server; shared with the login screen. */
@Composable
fun CustomHeadersItem() {
	val backStack = LocalNavStack.current
	SettingsNavItem(
		onClick = dropUnlessResumed { backStack.add(Screen.Settings.CustomHeaders) },
		content = { Text(stringResource(Res.string.option_custom_headers)) },
		supportingContent = { Text(stringResource(Res.string.subtitle_custom_headers)) },
		shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
	)
}

/** The device name override; shared with the login screen. */
@Composable
fun ThisDeviceGroup() {
	val sessionManager = koinInject<SessionManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	var deviceNameDialogOpen by rememberSaveable { mutableStateOf(false) }
	// read after the dialog writes it (deviceName isn't observable)
	var deviceName by remember { mutableStateOf(sessionManager.deviceName) }

	SettingsGroup(title = { Text(stringResource(Res.string.title_this_device)) }) {
		SegmentedListItem(
			onClick = { deviceNameDialogOpen = true },
			shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
			supportingContent = { Text(deviceName) },
			content = { Text(stringResource(Res.string.option_device_name)) }
		)
	}

	if (deviceNameDialogOpen) {
		val name = rememberTextFieldState(preferenceManager.deviceName)
		AlertDialog(
			onDismissRequest = { deviceNameDialogOpen = false },
			title = { Text(stringResource(Res.string.option_device_name)) },
			text = {
				Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
					Text(
						stringResource(Res.string.info_device_name),
						style = MaterialTheme.typography.bodyMedium
					)
					OutlinedTextField(
						state = name,
						lineLimits = TextFieldLineLimits.SingleLine,
						placeholder = { Text(systemDeviceName()) },
						supportingText = {
							Text(stringResource(Res.string.info_device_name_default, systemDeviceName()))
						}
					)
				}
			},
			confirmButton = {
				TextButton(onClick = {
					preferenceManager.deviceName = name.text.toString().trim()
					// the client name carries the device name
					sessionManager.refreshClient()
					deviceName = sessionManager.deviceName
					deviceNameDialogOpen = false
				}) { Text(stringResource(Res.string.action_ok)) }
			},
			dismissButton = {
				TextButton(onClick = { deviceNameDialogOpen = false }) {
					Text(stringResource(Res.string.action_cancel))
				}
			}
		)
	}
}
