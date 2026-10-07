package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.manager.AudioMuseInfo
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Sparkle
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.Info
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
private fun SettingsScaffold(title: String, content: @Composable ColumnScope.() -> Unit) {
	val backStack = LocalNavStack.current
	// hidden in the settings list+detail layout, where the row to the left is the way back;
	// during the setup wizard (no settings pane) the arrow is the only way back
	val hideBack = LocalPlatformContext.current.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium &&
		backStack.any { it is Screen.Settings.Root }
	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(title) },
				navigationAction = { if (!hideBack) NestedTopBarDefaults.NavigationAction() }
			)
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp)
				.padding(bottom = 24.dp),
			verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
		) { content() }
	}
}

/** AudioMuse-AI: connection, what it's doing, and what Lo'ak does with it. */
@Composable
fun AudioMuseSettingsScreen() {
	val manager = koinInject<AudioMuseManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	val info by manager.info.collectAsState()
	var error by remember { mutableStateOf<String?>(null) }
	LaunchedEffect(Unit) {
		if (manager.isConfigured) runCatching { manager.refresh() }.onFailure { error = it.message }
	}

	SettingsScaffold(stringResource(Res.string.title_audiomuse)) {
		if (manager.isConfigured) {
			Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(16.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(14.dp)
				) {
					Icon(Icons.Filled.Sparkle, null, Modifier.size(24.dp))
					Column(Modifier.weight(1f)) {
						Text(stringResource(Res.string.title_connected), style = MaterialTheme.typography.titleMedium)
						Text(
							error?.let { stringResource(Res.string.info_audiomuse_unreachable, it) } ?: statusLine(preferenceManager),
							style = MaterialTheme.typography.bodyMedium
						)
					}
				}
			}
			AnalysisBanner(manager)
		} else {
			Text(stringResource(Res.string.info_audiomuse_not_connected), style = MaterialTheme.typography.bodyMedium)
			Button(onClick = dropUnlessResumed { backStack.add(Screen.Settings.AudioMuseConnect) }) {
				Text(stringResource(Res.string.action_set_up))
			}
		}

		val toggles = when {
			info?.canAsk == true -> 4
			info != null -> 3
			else -> 1
		}
		SettingsGroup(title = { Text(stringResource(Res.string.title_in_loak)) }) {
			SettingsToggleItem(
				content = { Text(stringResource(Res.string.option_audiomuse_tidy)) },
				supportingContent = { Text(stringResource(Res.string.subtitle_audiomuse_tidy)) },
				checked = preferenceManager.audioMuseIntegration,
				onCheckedChange = { preferenceManager.audioMuseIntegration = it },
				shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = toggles)
			)
			if (info != null) {
				SettingsToggleItem(
					content = { Text(stringResource(Res.string.option_audiomuse_home)) },
					supportingContent = { Text(stringResource(Res.string.subtitle_audiomuse_home)) },
					checked = preferenceManager.audioMuseHome,
					onCheckedChange = { preferenceManager.audioMuseHome = it },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = toggles)
				)
				SettingsToggleItem(
					content = { Text(stringResource(Res.string.option_audiomuse_describe)) },
					supportingContent = { Text(stringResource(Res.string.subtitle_audiomuse_describe)) },
					checked = preferenceManager.audioMuseDescribe,
					onCheckedChange = { preferenceManager.audioMuseDescribe = it },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = toggles)
				)
			}
			info?.takeIf { it.canAsk }?.let { info ->
				SettingsToggleItem(
					content = { Text(stringResource(Res.string.option_audiomuse_ask)) },
					supportingContent = { Text(stringResource(Res.string.subtitle_audiomuse_ask, info.aiProviderName)) },
					checked = preferenceManager.audioMuseAskAi,
					onCheckedChange = { preferenceManager.audioMuseAskAi = it },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = toggles)
				)
			}
		}

		if (manager.isConfigured) {
			AudioMuseScheduleGroup(manager)
			SettingsGroup(title = { Text(stringResource(Res.string.title_audiomuse_connection)) }) {
				SettingsToggleItem(
					content = { Text(stringResource(Res.string.option_audiomuse_inherit_server_headers)) },
					supportingContent = { Text(stringResource(Res.string.subtitle_audiomuse_inherit_server_headers)) },
					checked = preferenceManager.audioMuseInheritServerHeaders,
					onCheckedChange = { preferenceManager.audioMuseInheritServerHeaders = it },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 4)
				)
				SettingsNavItem(
					onClick = dropUnlessResumed { backStack.add(Screen.Settings.AudioMuseConnect) },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 4),
					content = { Text(stringResource(Res.string.option_audiomuse_address)) },
					supportingContent = { Text(preferenceManager.audioMuseUrl) }
				)
				SettingsNavItem(
					onClick = dropUnlessResumed { backStack.add(Screen.Settings.AudioMuseConnect) },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 4),
					content = { Text(stringResource(Res.string.option_audiomuse_sign_in)) },
					supportingContent = { Text(signInLine(preferenceManager)) }
				)
				SettingsNavItem(
					onClick = dropUnlessResumed { backStack.add(Screen.Settings.AudioMuseCustomHeaders) },
					shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 4),
					content = { Text(stringResource(Res.string.option_audiomuse_headers)) },
					supportingContent = { Text(stringResource(Res.string.subtitle_audiomuse_headers)) }
				)
			}
			OutlinedButton(
				onClick = { manager.disconnect() },
				colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
			) { Text(stringResource(Res.string.action_disconnect)) }
		}
		info?.let { FeatureList(it) }
	}
}

@Composable
private fun statusLine(preferenceManager: PreferenceManager): String {
	val host = preferenceManager.audioMuseUrl.substringAfter("://")
	return when {
		preferenceManager.audioMuseToken.isNotBlank() -> stringResource(Res.string.info_audiomuse_status_token, host)
		preferenceManager.audioMuseUsername.isNotBlank() ->
			stringResource(Res.string.info_audiomuse_status, host, preferenceManager.audioMuseUsername)
		else -> stringResource(Res.string.info_audiomuse_status_open, host)
	}
}

@Composable
private fun signInLine(preferenceManager: PreferenceManager): String = when {
	preferenceManager.audioMuseToken.isNotBlank() -> stringResource(Res.string.option_sign_in_token)
	else -> "${stringResource(Res.string.option_sign_in_username)} · ${preferenceManager.audioMuseUsername}"
}

/** What AudioMuse-AI is analysing right now (GET /api/active_tasks), refreshed every few seconds. */
@Composable
private fun AnalysisBanner(manager: AudioMuseManager) {
	val task by produceState<Pair<Int, String>?>(null) {
		while (true) {
			value = runCatching {
				val t = manager.getJson("api/active_tasks").jsonObject
				val progress = t["progress"]?.jsonPrimitive?.intOrNull ?: return@runCatching null
				progress to (t["details"]?.let { d ->
					runCatching { d.jsonObject["status_message"]?.jsonPrimitive?.contentOrNull }.getOrNull()
						?: (d as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
				} ?: "")
			}.getOrNull()
			delay(5_000)
		}
	}
	val (progress, details) = task ?: return
	Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
		Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Row {
				Text(stringResource(Res.string.label_analysing), Modifier.weight(1f))
				Text("$progress%", color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
			if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, maxLines = 2)
		}
	}
}

@Composable
private fun FeatureList(info: AudioMuseInfo) {
	SettingsGroup(title = { Text(stringResource(Res.string.title_what_you_get)) }) {
		val features = listOf<Triple<StringResource, String, Boolean>>(
			Triple(Res.string.feature_sounds_like, stringResource(Res.string.feature_sounds_like_info), true),
			Triple(Res.string.feature_alchemy, stringResource(Res.string.feature_alchemy_info), true),
			Triple(Res.string.feature_song_path, stringResource(Res.string.feature_song_path_info), true),
			Triple(Res.string.feature_describe, stringResource(Res.string.feature_describe_info), info.soundSearch || info.lyricsSearch),
			Triple(Res.string.feature_ai_playlists, stringResource(Res.string.feature_ai_playlists_info), true),
			Triple(
				Res.string.feature_ask_ai,
				if (info.canAsk) stringResource(Res.string.feature_ask_ai_info, info.aiProviderName)
				else stringResource(Res.string.feature_ask_ai_missing),
				info.canAsk
			)
		)
		features.forEachIndexed { i, (title, sub, ok) ->
			SegmentedListItem(
				onClick = {},
				shapes = SegmentedListItemDefaults.segmentedShapes(index = i, count = features.size),
				content = { Text(stringResource(title)) },
				supportingContent = { Text(sub) },
				trailingContent = {
					if (ok) Icon(Icons.Outlined.Check, null, tint = Color(0xFF2E6B3A))
					else Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			)
		}
	}
}

/** Address and sign-in for AudioMuse-AI; checks them before keeping them. */
@Composable
fun AudioMuseConnectScreen() {
	val manager = koinInject<AudioMuseManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val permissionManager = koinInject<PermissionManager>()
	val backStack = LocalNavStack.current
	val scope = rememberCoroutineScope()
	val localNetworkNeeded = stringResource(Res.string.info_local_network_needed)
	val address = rememberTextFieldState(preferenceManager.audioMuseUrl.ifBlank { "https://" })
	val username = rememberTextFieldState(preferenceManager.audioMuseUsername)
	val password = rememberTextFieldState(preferenceManager.audioMusePassword)
	val token = rememberTextFieldState(preferenceManager.audioMuseToken)
	var useToken by remember { mutableStateOf(preferenceManager.audioMuseToken.isNotBlank()) }
	var busy by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }
	var connected by remember { mutableStateOf<AudioMuseInfo?>(null) }

	SettingsScaffold(stringResource(Res.string.title_connect_audiomuse)) {
		val done = connected
		if (done != null) {
			Text(stringResource(Res.string.title_connected), style = MaterialTheme.typography.headlineSmall)
			Text(statusLine(preferenceManager), style = MaterialTheme.typography.bodyMedium)
			FeatureList(done)
			Button(onClick = { backStack.removeLastOrNull() }, modifier = Modifier.align(Alignment.End)) {
				Text(stringResource(Res.string.action_done))
			}
			return@SettingsScaffold
		}
		Text(stringResource(Res.string.info_connect_audiomuse), style = MaterialTheme.typography.bodyLarge)
		OutlinedTextField(
			state = address,
			modifier = Modifier.fillMaxWidth(),
			label = { Text(stringResource(Res.string.label_audiomuse_address)) },
			lineLimits = TextFieldLineLimits.SingleLine,
			keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
			enabled = !busy
		)
		SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
			listOf(false to Res.string.option_sign_in_username, true to Res.string.option_sign_in_token)
				.forEachIndexed { i, (tokenMode, label) ->
					SegmentedButton(
						selected = useToken == tokenMode,
						onClick = { useToken = tokenMode },
						shape = SegmentedButtonDefaults.itemShape(i, 2)
					) { Text(stringResource(label)) }
				}
		}
		if (useToken) {
			OutlinedSecureTextField(
				state = token,
				modifier = Modifier.fillMaxWidth(),
				label = { Text(stringResource(Res.string.label_api_token)) },
				enabled = !busy
			)
		} else {
			OutlinedTextField(
				state = username,
				modifier = Modifier.fillMaxWidth(),
				label = { Text(stringResource(Res.string.option_account_username)) },
				lineLimits = TextFieldLineLimits.SingleLine,
				keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
				enabled = !busy
			)
			OutlinedSecureTextField(
				state = password,
				modifier = Modifier.fillMaxWidth(),
				label = { Text(stringResource(Res.string.option_account_password)) },
				enabled = !busy
			)
		}
		Text(
			error ?: stringResource(Res.string.info_audiomuse_sign_in),
			style = MaterialTheme.typography.bodySmall,
			color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
		)
		Button(
			modifier = Modifier.align(Alignment.End),
			enabled = !busy && address.text.length > "https://".length,
			onClick = {
				busy = true
				error = null
				scope.launch {
					// like the music server, it usually sits on a private address
					if (!permissionManager.requestLocalNetworkPermission()) {
						error = localNetworkNeeded
						busy = false
						return@launch
					}
					try {
						connected = manager.connect(
							url = address.text.toString(),
							username = if (useToken) "" else username.text.toString(),
							password = if (useToken) "" else password.text.toString(),
							token = if (useToken) token.text.toString() else ""
						)
					} catch (e: Exception) {
						error = e.message ?: e.toString()
					}
					busy = false
				}
			}
		) {
			if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(stringResource(Res.string.action_connect))
		}
	}
}
