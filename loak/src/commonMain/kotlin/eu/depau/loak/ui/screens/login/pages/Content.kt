package eu.depau.loak.ui.screens.login.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.LoginManager
import eu.depau.loak.domain.manager.NavidromeManager
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_next
import eu.depau.loak.generated.resources.action_open_settings
import eu.depau.loak.generated.resources.action_rename
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.info_advanced_login
import eu.depau.loak.generated.resources.info_connected_to
import eu.depau.loak.generated.resources.info_device_name_short
import eu.depau.loak.generated.resources.info_login_description
import eu.depau.loak.generated.resources.label_this_device
import eu.depau.loak.generated.resources.notice_local_network_denied
import eu.depau.loak.generated.resources.option_crash_reporting
import eu.depau.loak.generated.resources.subtitle_crash_reporting
import eu.depau.loak.generated.resources.subtitle_local_network_denied
import eu.depau.loak.generated.resources.title_advanced
import eu.depau.loak.generated.resources.title_connect_server
import eu.depau.loak.generated.resources.title_youre_connected
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Dns
import eu.depau.loak.icons.outlined.BugReport
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.icons.outlined.Error
import eu.depau.loak.icons.outlined.Smartphone
import eu.depau.loak.icons.outlined.Tune
import eu.depau.loak.ui.components.dialogs.DialogButton
import eu.depau.loak.ui.components.dialogs.FormDialog
import eu.depau.loak.ui.core.LoginUiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.DeviceNameDialog
import eu.depau.loak.ui.screens.setup.WizardButton
import eu.depau.loak.ui.screens.setup.WizardScaffold
import eu.depau.loak.ui.screens.setup.WizardStep
import eu.depau.loak.ui.theme.defaultFont
import eu.depau.loak.util.instanceHost
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The setup wizard's first step: only the server. Custom headers wait behind Advanced, the
 * device name and crash reports are a line each. Once in, a moment says what was found.
 */
@Composable
fun LoginScreenContent() {
	val viewModel = koinInject<LoginManager>()
	val loginState by viewModel.loginState.collectAsStateWithLifecycle()
	if (loginState is LoginUiState.Success) {
		ConnectedPage()
		return
	}

	val isBusy = loginState is LoginUiState.Loading
	val haptics = LocalHapticFeedback.current
	val backStack = LocalNavStack.current

	val instanceFocusRequester = remember { FocusRequester() }
	val usernameFocusRequester = remember { FocusRequester() }
	val passwordFocusRequester = remember { FocusRequester() }

	val permissionManager = koinInject<PermissionManager>()
	val loginScope = rememberCoroutineScope()
	var localNetworkDenied by rememberSaveable { mutableStateOf(false) }
	val login: () -> Unit = {
		loginScope.launch {
			if (!permissionManager.requestLocalNetworkPermission()) {
				localNetworkDenied = true
				return@launch
			}

			if (!viewModel.login()) {
				haptics.performHapticFeedback(HapticFeedbackType.Reject)
				when {
					viewModel.instanceError -> instanceFocusRequester.requestFocus()
					viewModel.usernameError -> usernameFocusRequester.requestFocus()
					viewModel.passwordError -> passwordFocusRequester.requestFocus()
				}
			}
		}
	}

	WizardScaffold(
		title = stringResource(Res.string.title_connect_server),
		icon = Icons.Filled.Dns,
		description = stringResource(Res.string.info_login_description),
		step = WizardStep(1, 2),
		actions = { WizardButton(stringResource(Res.string.action_next), onClick = login, enabled = !isBusy) }
	) {
		LoginScreenProgress(isBusy = isBusy)
		LoginScreenError(loginUiState = loginState)
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			LoginScreenFields(
				isBusy = isBusy,
				instanceState = viewModel.instanceState,
				instanceError = viewModel.instanceError,
				instanceFocusRequester = instanceFocusRequester,
				onInstanceFocusChanged = { viewModel.validateInstance() },
				usernameState = viewModel.usernameState,
				usernameError = viewModel.usernameError,
				usernameFocusRequester = usernameFocusRequester,
				onUsernameFocusChanged = { viewModel.validateUsername() },
				passwordState = viewModel.passwordState,
				passwordError = viewModel.passwordError,
				passwordFocusRequester = passwordFocusRequester,
				onPasswordFocusChanged = { viewModel.validatePassword() },
				onLogin = login
			)
		}
		// custom headers have to be reachable before logging in (Cloudflare Access and the like)
		Surface(
			onClick = dropUnlessResumed { backStack.add(Screen.Settings.CustomHeaders) },
			shape = MaterialTheme.shapes.largeIncreased,
			border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
			modifier = Modifier.padding(top = 4.dp)
		) {
			Row(
				Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
				horizontalArrangement = Arrangement.spacedBy(16.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(Icons.Outlined.Tune, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
				Column(Modifier.weight(1f)) {
					Text(stringResource(Res.string.title_advanced), style = MaterialTheme.typography.bodyLarge)
					Text(
						stringResource(Res.string.info_advanced_login),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Icon(Icons.Outlined.ChevronForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
			}
		}
		DeviceNameLine(Modifier.padding(top = 16.dp))
		CrashReportsLine()
	}

	if (localNetworkDenied) {
		FormDialog(
			onDismissRequest = { localNetworkDenied = false },
			icon = { Icon(Icons.Outlined.Error, null) },
			title = { Text(stringResource(Res.string.notice_local_network_denied)) },
			content = { Text(stringResource(Res.string.subtitle_local_network_denied)) },
			buttons = {
				DialogButton(
					onClick = {
						localNetworkDenied = false
						permissionManager.openPermissionsSettings()
					},
				) {
					Text(stringResource(Res.string.action_open_settings))
				}
			}
		)
	}
}

/** "This device: Pixel 9", and a way to rename it before other devices see it. */
@Composable
private fun DeviceNameLine(modifier: Modifier = Modifier) {
	val sessionManager = koinInject<SessionManager>()
	var deviceName by remember { mutableStateOf(sessionManager.deviceName) }
	var renaming by rememberSaveable { mutableStateOf(false) }
	Row(
		modifier.padding(start = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(Icons.Outlined.Smartphone, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
		Column(Modifier.weight(1f)) {
			Text(stringResource(Res.string.label_this_device, deviceName), style = MaterialTheme.typography.bodyMedium)
			Text(
				stringResource(Res.string.info_device_name_short),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		TextButton(onClick = { renaming = true }) { Text(stringResource(Res.string.action_rename)) }
	}
	if (renaming) DeviceNameDialog(onDismiss = { renaming = false }, onSaved = { deviceName = it })
}

/** Crash reports are on by default, so the switch is here, before the first one could be sent. */
@Composable
private fun CrashReportsLine() {
	val preferenceManager = koinInject<PreferenceManager>()
	Row(
		Modifier.padding(start = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(12.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Icon(Icons.Outlined.BugReport, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
		Column(Modifier.weight(1f)) {
			Text(stringResource(Res.string.option_crash_reporting), style = MaterialTheme.typography.bodyMedium)
			Text(
				stringResource(Res.string.subtitle_crash_reporting),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		Switch(
			checked = preferenceManager.crashReportingEnabled,
			onCheckedChange = { preferenceManager.crashReportingEnabled = it }
		)
	}
}

/**
 * A moment between the steps: what Lo'ak found at the address, while the library starts
 * syncing, then on to the optional features by itself.
 */
@Composable
private fun ConnectedPage() {
	val backStack = LocalNavStack.current
	val sessionManager = koinInject<SessionManager>()
	val navidrome = koinInject<NavidromeManager>()
	val syncManager = koinInject<SyncManager>()
	val sync by syncManager.syncState.collectAsState()
	val server by produceState<String?>(null) { value = navidrome.serverInfo().label }
	val songs by produceState<Long?>(null) { value = syncManager.scanStatus()?.count?.toLong() }
	LaunchedEffect(Unit) {
		// counted in frames, not delay(): the UI tests' clock moves frames, not coroutine time
		val start = withFrameMillis { it }
		while (withFrameMillis { it } - start < 1800) Unit
		backStack.clear()
		backStack.add(Screen.SetupFeatures)
	}

	Scaffold { innerPadding ->
		Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
			Column(
				Modifier.padding(24.dp),
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.spacedBy(16.dp)
			) {
				Surface(
					modifier = Modifier.size(120.dp).rotate(-8f),
					shape = MaterialTheme.shapes.extraLarge,
					color = MaterialTheme.colorScheme.primaryContainer
				) {
					Box(contentAlignment = Alignment.Center) {
						Icon(Icons.Outlined.Check, null, Modifier.size(64.dp).rotate(8f))
					}
				}
				Text(
					stringResource(Res.string.title_youre_connected),
					modifier = Modifier.padding(top = 12.dp),
					style = MaterialTheme.typography.headlineLarge,
					fontFamily = defaultFont(round = 100f)
				)
				val host = instanceHost(sessionManager.instanceUrl)
				Text(
					server?.let { stringResource(Res.string.info_connected_to, it, host) } ?: host,
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center
				)
				songs?.let {
					SuggestionChip(
						onClick = {},
						label = { Text(pluralStringResource(Res.plurals.count_songs, it.toInt(), it)) }
					)
				}
				if (sync.isSyncing) Column(
					Modifier.width(240.dp).padding(top = 8.dp),
					horizontalAlignment = Alignment.CenterHorizontally,
					verticalArrangement = Arrangement.spacedBy(8.dp)
				) {
					LinearProgressIndicator(progress = { sync.progress }, modifier = Modifier.fillMaxWidth())
					Text(
						stringResource(sync.message),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.Center
					)
				}
			}
		}
	}
}
