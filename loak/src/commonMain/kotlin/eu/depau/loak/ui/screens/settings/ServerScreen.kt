package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import com.russhwolf.settings.Settings
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.LoginFailureException
import eu.depau.loak.domain.manager.LoginManager
import eu.depau.loak.domain.manager.NavidromeManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_change_server
import eu.depau.loak.generated.resources.action_log_out
import eu.depau.loak.generated.resources.action_ok
import eu.depau.loak.generated.resources.action_save_and_reconnect
import eu.depau.loak.generated.resources.action_sync_now
import eu.depau.loak.generated.resources.count_albums
import eu.depau.loak.generated.resources.count_artists
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.info_change_server
import eu.depau.loak.generated.resources.info_change_server_switch
import eu.depau.loak.generated.resources.info_device_name
import eu.depau.loak.generated.resources.info_device_name_default
import eu.depau.loak.generated.resources.info_error
import eu.depau.loak.generated.resources.info_log_out
import eu.depau.loak.generated.resources.info_server_signed_in
import eu.depau.loak.generated.resources.info_sync_never
import eu.depau.loak.generated.resources.info_synced_ago
import eu.depau.loak.generated.resources.notice_server_saved
import eu.depau.loak.generated.resources.option_account_instance
import eu.depau.loak.generated.resources.option_account_password
import eu.depau.loak.generated.resources.option_account_username
import eu.depau.loak.generated.resources.option_crash_reporting
import eu.depau.loak.generated.resources.option_custom_headers
import eu.depau.loak.generated.resources.option_device_name
import eu.depau.loak.generated.resources.status_server_ping
import eu.depau.loak.generated.resources.info_server_unreachable
import eu.depau.loak.generated.resources.subtitle_crash_reporting
import eu.depau.loak.generated.resources.subtitle_custom_headers
import eu.depau.loak.generated.resources.title_connection
import eu.depau.loak.generated.resources.title_library
import eu.depau.loak.generated.resources.title_server_account
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Dns
import eu.depau.loak.icons.outlined.Edit
import eu.depau.loak.icons.outlined.Logout
import eu.depau.loak.icons.outlined.SyncAlt
import eu.depau.loak.icons.outlined.Tune
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import eu.depau.loak.ui.util.messageResource
import eu.depau.loak.ui.util.timeAgo
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.instanceHost
import eu.depau.loak.util.systemDeviceName
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The account and nothing else: where, who, and whether it answers; the library it gave this
 * device; custom headers; logging out. The optional features live in Settings › Integrations.
 */
@Composable
fun SettingsServerScreen() {
	val sessionManager = koinInject<SessionManager>()
	val loginManager = koinInject<LoginManager>()
	val syncManager = koinInject<SyncManager>()
	val navidrome = koinInject<NavidromeManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val songDao = koinInject<SongDao>()
	val albumDao = koinInject<AlbumDao>()
	val artistDao = koinInject<ArtistDao>()
	val backStack = LocalNavStack.current
	val sync by syncManager.syncState.collectAsState()

	val server by produceState<String?>(null) { value = navidrome.serverInfo().label }
	// null until the first answer; then the time it took, or null in the pair when it didn't
	val ping by produceState<Pair<Duration?, Boolean>?>(null) { value = sessionManager.pingTime() to true }
	val counts by produceState<List<Int>?>(null, sync.isSyncing) {
		value = withContext(IoDispatcher) {
			listOf(songDao.getSongCount(), albumDao.getAlbumCount(), artistDao.getAllArtistIds().size)
		}
	}

	SettingsScaffold(stringResource(Res.string.title_server_account)) {
		Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer) {
			Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
				Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
					Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
						Icon(Icons.Filled.Dns, null, Modifier.padding(14.dp).size(28.dp))
					}
					Column(Modifier.weight(1f)) {
						Text(instanceHost(sessionManager.instanceUrl), style = MaterialTheme.typography.titleLarge)
						Text(
							server?.let { stringResource(Res.string.info_server_signed_in, it, sessionManager.username) }
								?: sessionManager.username,
							style = MaterialTheme.typography.bodyMedium,
							color = MaterialTheme.colorScheme.onSecondaryContainer
						)
					}
				}
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
					ping?.let { (time, _) ->
						StatusChip(
							if (time != null) stringResource(Res.string.status_server_ping, time.inWholeMilliseconds)
							else stringResource(Res.string.info_server_unreachable),
							dot = if (time != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
						)
					}
					val synced = preferenceManager.lastFullSyncTime
					StatusChip(
						when {
							sync.isSyncing -> stringResource(sync.message)
							synced == 0L -> stringResource(Res.string.info_sync_never)
							else -> stringResource(Res.string.info_synced_ago, Instant.fromEpochMilliseconds(synced).timeAgo())
						},
						icon = Icons.Outlined.SyncAlt
					)
				}
				Button(
					onClick = dropUnlessResumed { backStack.add(Screen.Settings.ChangeServer) },
					modifier = Modifier.fillMaxWidth()
				) {
					Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
					Text(stringResource(Res.string.action_change_server), Modifier.padding(start = 8.dp))
				}
			}
		}

		SettingsGroup(title = { Text(stringResource(Res.string.title_library)) }) {
			SegmentedListItem(
				onClick = {},
				shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2),
				content = {
					Text(counts?.let { (songs, albums, artists) ->
						listOf(
							pluralStringResource(Res.plurals.count_songs, songs, songs),
							pluralStringResource(Res.plurals.count_albums, albums, albums),
							pluralStringResource(Res.plurals.count_artists, artists, artists)
						).joinToString(" · ")
					} ?: "")
				}
			)
			SettingsNavItem(
				onClick = { syncManager.triggerManualSync() },
				enabled = !sync.isSyncing,
				shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2),
				leadingContent = { Icon(Icons.Outlined.SyncAlt, null) },
				content = { Text(stringResource(Res.string.action_sync_now)) }
			)
		}

		SettingsGroup(title = { Text(stringResource(Res.string.title_connection)) }) {
			CustomHeadersItem()
		}

		SettingsGroup {
			SegmentedListItem(
				onClick = {
					loginManager.logout()
					backStack.clear()
					backStack.add(Screen.Login)
				},
				shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1),
				leadingContent = { Icon(Icons.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
				content = { Text(stringResource(Res.string.action_log_out), color = MaterialTheme.colorScheme.error) },
				supportingContent = { Text(stringResource(Res.string.info_log_out)) }
			)
		}
	}
}

@Composable
private fun StatusChip(text: String, dot: androidx.compose.ui.graphics.Color? = null, icon: ImageVector? = null) {
	Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
		Row(
			Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
			horizontalArrangement = Arrangement.spacedBy(6.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			dot?.let { Box(Modifier.size(8.dp).background(it, CircleShape)) }
			icon?.let { Icon(it, null, Modifier.size(16.dp)) }
			Text(text, style = MaterialTheme.typography.labelLarge)
		}
	}
}

/**
 * New connection details, checked before anything changes; a different server or user starts
 * over with a fresh library, which is said before it happens.
 */
@Composable
fun SettingsChangeServerScreen() {
	val sessionManager = koinInject<SessionManager>()
	val loginManager = koinInject<LoginManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val settings = koinInject<Settings>()
	val backStack = LocalNavStack.current
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

	SettingsScaffold(stringResource(Res.string.action_change_server)) {
		Text(
			stringResource(Res.string.info_change_server),
			style = MaterialTheme.typography.bodyLarge,
			modifier = Modifier.padding(horizontal = 4.dp)
		)
		Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
			OutlinedTextField(
				state = instance,
				modifier = Modifier.fillMaxWidth(),
				label = { Text(stringResource(Res.string.option_account_instance)) },
				leadingIcon = { Icon(Icons.Filled.Dns, null) },
				lineLimits = TextFieldLineLimits.SingleLine,
				enabled = !busy,
				isError = error != null,
				keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri)
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
				keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password)
			)
		}
		Text(
			stringResource(Res.string.info_change_server_switch),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(horizontal = 4.dp)
		)
		Button(
			modifier = Modifier.align(Alignment.End),
			enabled = changed && !busy && instance.text.isNotBlank() && username.text.isNotBlank(),
			onClick = {
				busy = true
				error = null
				scope.launch {
					try {
						loginManager.reconnect(instance.text.toString(), username.text.toString(), password.text.toString())
						snackBarManager.notify(Res.string.notice_server_saved)
						backStack.removeLastOrNull()
					} catch (e: Exception) {
						error = (e as? LoginFailureException)?.let { getString(it.reason.messageResource) }
							?: getString(Res.string.info_error)
					}
					busy = false
				}
			}
		) {
			Text(stringResource(Res.string.action_save_and_reconnect))
		}
	}
}

/** Opens the custom HTTP headers sent to the server. */
@Composable
fun CustomHeadersItem() {
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val headers = preferenceManager.customHeadersMap().size
	SettingsNavItem(
		onClick = dropUnlessResumed { backStack.add(Screen.Settings.CustomHeaders) },
		leadingContent = { Icon(Icons.Outlined.Tune, null) },
		content = { Text(stringResource(Res.string.option_custom_headers)) },
		supportingContent = {
			Text(
				if (headers == 0) stringResource(Res.string.subtitle_custom_headers)
				else preferenceManager.customHeadersMap().keys.joinToString(", ")
			)
		},
		shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
	)
}

/** The crash reporting toggle (About). */
@Composable
fun CrashReportingItem(shapes: ListItemShapes) {
	val preferenceManager = koinInject<PreferenceManager>()
	SettingsToggleItem(
		checked = preferenceManager.crashReportingEnabled,
		onCheckedChange = { preferenceManager.crashReportingEnabled = it },
		content = { Text(stringResource(Res.string.option_crash_reporting)) },
		supportingContent = { Text(stringResource(Res.string.subtitle_crash_reporting)) },
		shapes = shapes
	)
}

/** The device name override, as other devices see a queue shared from here (Playback). */
@Composable
fun DeviceNameItem(shapes: ListItemShapes) {
	val sessionManager = koinInject<SessionManager>()
	var dialogOpen by rememberSaveable { mutableStateOf(false) }
	// read after the dialog writes it (deviceName isn't observable)
	var deviceName by remember { mutableStateOf(sessionManager.deviceName) }

	SegmentedListItem(
		onClick = { dialogOpen = true },
		shapes = shapes,
		supportingContent = { Text(deviceName) },
		content = { Text(stringResource(Res.string.option_device_name)) }
	)
	if (dialogOpen) DeviceNameDialog(onDismiss = { dialogOpen = false }, onSaved = { deviceName = it })
}

/** Renames this device; [onSaved] gets the name now in use (the system's, if left blank). */
@Composable
fun DeviceNameDialog(onDismiss: () -> Unit, onSaved: (String) -> Unit) {
	val sessionManager = koinInject<SessionManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val name = rememberTextFieldState(preferenceManager.deviceName)
	AlertDialog(
		onDismissRequest = onDismiss,
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
				onSaved(sessionManager.deviceName)
				onDismiss()
			}) { Text(stringResource(Res.string.action_ok)) }
		},
		dismissButton = {
			TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
		}
	)
}
