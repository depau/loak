package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.*
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Lidarr: the connect form until it's set up, then what it's doing and its connection. */
@Composable
fun LidarrSettingsScreen() {
	val manager = koinInject<LidarrManager>()
	var connecting by remember { mutableStateOf(false) }
	SettingsScaffold(stringResource(Res.string.title_lidarr)) {
		// connect() saves the address before checking it: keep the form up until it answers
		if (manager.isConfigured && !connecting) LidarrConnected(manager)
		else LidarrConnectForm(manager, connecting, { connecting = it }, onConnected = {})
	}
}

/** A new address or API key for Lidarr; checks them before keeping them. */
@Composable
fun LidarrConnectScreen() {
	val manager = koinInject<LidarrManager>()
	val backStack = LocalNavStack.current
	var connecting by remember { mutableStateOf(false) }
	SettingsScaffold(stringResource(Res.string.title_lidarr)) {
		LidarrConnectForm(manager, connecting, { connecting = it }, onConnected = { backStack.removeLastOrNull() })
	}
}

@Composable
private fun ColumnScope.LidarrConnectForm(
	manager: LidarrManager,
	busy: Boolean,
	onBusy: (Boolean) -> Unit,
	onConnected: () -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val permissionManager = koinInject<PermissionManager>()
	val scope = rememberCoroutineScope()
	val localNetworkNeeded = stringResource(Res.string.info_local_network_needed_lidarr)
	val address = rememberTextFieldState(preferenceManager.lidarrUrl.ifBlank { "https://" })
	val apiKey = rememberTextFieldState(preferenceManager.lidarrApiKey)
	var error by remember { mutableStateOf<String?>(null) }

	Text(stringResource(Res.string.title_connect_lidarr), style = MaterialTheme.typography.headlineSmall)
	Text(stringResource(Res.string.info_connect_lidarr), style = MaterialTheme.typography.bodyLarge)
	OutlinedTextField(
		state = address,
		modifier = Modifier.fillMaxWidth(),
		label = { Text(stringResource(Res.string.label_lidarr_address)) },
		lineLimits = TextFieldLineLimits.SingleLine,
		keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
		enabled = !busy
	)
	OutlinedSecureTextField(
		state = apiKey,
		modifier = Modifier.fillMaxWidth(),
		label = { Text(stringResource(Res.string.label_api_key)) },
		isError = error != null,
		supportingText = { Text(error ?: stringResource(Res.string.info_lidarr_api_key)) },
		enabled = !busy
	)
	SettingsGroup { LidarrHeaderItems(first = 0, count = 2) }
	Button(
		modifier = Modifier.align(Alignment.End),
		enabled = !busy && address.text.length > "https://".length && apiKey.text.isNotBlank(),
		onClick = {
			onBusy(true)
			error = null
			scope.launch {
				try {
					// like the music server, it usually sits on a private address
					if (!permissionManager.requestLocalNetworkPermission()) error = localNetworkNeeded
					else {
						manager.connect(address.text.toString(), apiKey.text.toString())
						onConnected()
					}
				} catch (e: Exception) {
					error = e.message ?: e.toString()
				}
				onBusy(false)
			}
		}
	) {
		if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(stringResource(Res.string.action_connect))
	}
}

@Composable
private fun LidarrConnected(manager: LidarrManager) {
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val info by manager.info.collectAsState()
	var error by remember { mutableStateOf<String?>(null) }
	LaunchedEffect(Unit) {
		runCatching { manager.refresh() }.onFailure { error = it.message ?: it.toString() }
	}

	Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
		Column(Modifier.fillMaxWidth().padding(16.dp)) {
			Text(stringResource(Res.string.title_connected), style = MaterialTheme.typography.titleMedium)
			val status = error?.let { stringResource(Res.string.info_lidarr_unreachable, it) } ?: listOfNotNull(
				preferenceManager.lidarrUrl.substringAfter("://"),
				info?.version,
				info?.let { pluralStringResource(Res.plurals.count_root_folders, it.rootFolderCount, it.rootFolderCount) },
				info?.takeIf { it.queueCount > 0 }
					?.let { pluralStringResource(Res.plurals.count_downloading, it.queueCount, it.queueCount) }
			).joinToString(" · ")
			Text(status, style = MaterialTheme.typography.bodyMedium)
		}
	}

	SettingsGroup(title = { Text(stringResource(Res.string.title_in_loak)) }) {
		SettingsToggleItem(
			content = { Text(stringResource(Res.string.option_discover_tab)) },
			supportingContent = { Text(stringResource(Res.string.subtitle_discover_tab)) },
			checked = preferenceManager.discoverTabEnabled,
			onCheckedChange = { preferenceManager.discoverTabEnabled = it },
			shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
		)
		SettingsNavItem(
			onClick = dropUnlessResumed { backStack.add(Screen.LidarrQueue) },
			shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2),
			content = { Text(stringResource(Res.string.option_download_queue)) },
			supportingContent = info?.let {
				{
					Text(
						if (it.queueCount > 0) pluralStringResource(Res.plurals.count_downloading, it.queueCount, it.queueCount)
						else stringResource(Res.string.info_nothing_downloading)
					)
				}
			}
		)
	}

	SettingsGroup(title = { Text(stringResource(Res.string.title_connection)) }) {
		SettingsNavItem(
			onClick = dropUnlessResumed { backStack.add(Screen.Settings.LidarrConnect) },
			shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 4),
			content = { Text(stringResource(Res.string.option_lidarr_address)) },
			supportingContent = { Text(preferenceManager.lidarrUrl) }
		)
		SettingsNavItem(
			onClick = dropUnlessResumed { backStack.add(Screen.Settings.LidarrConnect) },
			shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 4),
			content = { Text(stringResource(Res.string.label_api_key)) },
			supportingContent = { Text("••••••••" + preferenceManager.lidarrApiKey.takeLast(4)) }
		)
		LidarrHeaderItems(first = 2, count = 4)
	}

	OutlinedButton(
		onClick = { manager.disconnect() },
		colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
	) { Text(stringResource(Res.string.action_disconnect)) }
}

/** "Use the server's custom headers" and Lidarr's own: rows [first] and [first] + 1 of [count]. */
@Composable
private fun LidarrHeaderItems(first: Int, count: Int) {
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val headers = preferenceManager.customHeadersMap(preferenceManager.lidarrCustomHeaders).size
	SettingsToggleItem(
		content = { Text(stringResource(Res.string.option_lidarr_inherit_server_headers)) },
		supportingContent = { Text(stringResource(Res.string.subtitle_lidarr_inherit_server_headers)) },
		checked = preferenceManager.lidarrInheritServerHeaders,
		onCheckedChange = { preferenceManager.lidarrInheritServerHeaders = it },
		shapes = SegmentedListItemDefaults.segmentedShapes(index = first, count = count)
	)
	SettingsNavItem(
		onClick = dropUnlessResumed { backStack.add(Screen.Settings.LidarrCustomHeaders) },
		shapes = SegmentedListItemDefaults.segmentedShapes(index = first + 1, count = count),
		content = { Text(stringResource(Res.string.option_lidarr_headers)) },
		supportingContent = {
			Text(
				if (headers == 0) stringResource(Res.string.label_none)
				else pluralStringResource(Res.plurals.count_headers, headers, headers)
			)
		}
	)
}
