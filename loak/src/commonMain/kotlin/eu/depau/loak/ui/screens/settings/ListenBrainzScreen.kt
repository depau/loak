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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.discover.ListenBrainzClient
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_connect
import eu.depau.loak.generated.resources.action_disconnect
import eu.depau.loak.generated.resources.error_no_listenbrainz_user
import eu.depau.loak.generated.resources.info_connect_listenbrainz
import eu.depau.loak.generated.resources.info_listenbrainz_saved
import eu.depau.loak.generated.resources.info_listenbrainz_username
import eu.depau.loak.generated.resources.label_listenbrainz_username
import eu.depau.loak.generated.resources.title_connect_listenbrainz
import eu.depau.loak.generated.resources.title_connected
import eu.depau.loak.generated.resources.title_listenbrainz
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Soundwave
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * ListenBrainz: the username form until one is set, then who Discover builds "Made for you"
 * for. No token — the "made for you" playlists and fresh releases are public.
 */
@Composable
fun ListenBrainzSettingsScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	SettingsScaffold(stringResource(Res.string.title_listenbrainz)) {
		if (preferenceManager.listenBrainzUsername.isBlank()) ListenBrainzConnectForm(preferenceManager)
		else ListenBrainzConnected(preferenceManager)
	}
}

@Composable
private fun ColumnScope.ListenBrainzConnectForm(preferenceManager: PreferenceManager) {
	val client = koinInject<ListenBrainzClient>()
	val snackBar = koinInject<SnackBarManager>()
	val backStack = LocalNavStack.current
	val scope = rememberCoroutineScope()
	val username = rememberTextFieldState(preferenceManager.listenBrainzUsername)
	var busy by remember { mutableStateOf(false) }
	var unknownUser by remember { mutableStateOf(false) }

	Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
		Icon(Icons.Outlined.Soundwave, null, Modifier.padding(12.dp).size(32.dp))
	}
	Text(stringResource(Res.string.title_connect_listenbrainz), style = MaterialTheme.typography.headlineSmall)
	Text(stringResource(Res.string.info_connect_listenbrainz), style = MaterialTheme.typography.bodyLarge)
	OutlinedTextField(
		state = username,
		modifier = Modifier.fillMaxWidth(),
		label = { Text(stringResource(Res.string.label_listenbrainz_username)) },
		lineLimits = TextFieldLineLimits.SingleLine,
		keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
		isError = unknownUser,
		supportingText = {
			Text(
				stringResource(
					if (unknownUser) Res.string.error_no_listenbrainz_user
					else Res.string.info_listenbrainz_username
				)
			)
		},
		enabled = !busy
	)
	Button(
		modifier = Modifier.align(Alignment.End),
		enabled = !busy && username.text.isNotBlank(),
		onClick = {
			busy = true
			unknownUser = false
			scope.launch {
				val name = username.text.toString().trim()
				if (client.checkUser(name)) {
					preferenceManager.listenBrainzUsername = name
					snackBar.notify(Res.string.info_listenbrainz_saved, name)
					backStack.removeLastOrNull()
				} else {
					unknownUser = true
				}
				busy = false
			}
		}
	) {
		if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(stringResource(Res.string.action_connect))
	}
}

@Composable
private fun ColumnScope.ListenBrainzConnected(preferenceManager: PreferenceManager) {
	Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
		Column(Modifier.fillMaxWidth().padding(16.dp)) {
			Text(stringResource(Res.string.title_connected), style = MaterialTheme.typography.titleMedium)
			Text(preferenceManager.listenBrainzUsername, style = MaterialTheme.typography.bodyMedium)
		}
	}
	Text(stringResource(Res.string.info_connect_listenbrainz), style = MaterialTheme.typography.bodyMedium)
	OutlinedButton(
		modifier = Modifier.align(Alignment.End),
		onClick = { preferenceManager.listenBrainzUsername = "" },
		colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
	) { Text(stringResource(Res.string.action_disconnect)) }
}
