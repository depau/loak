package eu.depau.loak.ui.screens.playlist.dialogs

import androidx.compose.material3.Surface
import eu.depau.loak.generated.resources.info_audiomuse_edit_warning
import eu.depau.loak.ui.components.common.displayName
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_save
import eu.depau.loak.generated.resources.info_playlist_public
import eu.depau.loak.generated.resources.label_description
import eu.depau.loak.generated.resources.label_name
import eu.depau.loak.generated.resources.notice_playlist_saved
import eu.depau.loak.generated.resources.option_playlist_public
import eu.depau.loak.generated.resources.title_edit_playlist
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.util.Logger

/** Name, description and visibility of a playlist the user owns. [onSaved] runs after a save. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistSheet(
	playlist: DomainPlaylist,
	onDismissRequest: () -> Unit,
	onSaved: () -> Unit = {}
) {
	val sessionManager = koinInject<SessionManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()
	val playlistName = playlist.displayName()
	val name = rememberTextFieldState(playlistName.display)
	val description = rememberTextFieldState(playlist.comment.orEmpty())
	var public by remember { mutableStateOf(playlist.public == true) }
	var saving by remember { mutableStateOf(false) }

	ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	) {
		Column(
			modifier = Modifier.padding(horizontal = 16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(stringResource(Res.string.title_edit_playlist), style = MaterialTheme.typography.titleLarge)
			if (playlistName.kind.isRebuilt) Surface(
				shape = MaterialTheme.shapes.large,
				color = MaterialTheme.colorScheme.tertiaryContainer,
				contentColor = MaterialTheme.colorScheme.onTertiaryContainer
			) {
				Text(
					stringResource(Res.string.info_audiomuse_edit_warning),
					modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
					style = MaterialTheme.typography.bodyMedium
				)
			}
			TextField(
				state = name,
				modifier = Modifier.fillMaxWidth(),
				label = { Text(stringResource(Res.string.label_name)) },
				lineLimits = TextFieldLineLimits.SingleLine
			)
			TextField(
				state = description,
				modifier = Modifier.fillMaxWidth(),
				label = { Text(stringResource(Res.string.label_description)) },
				lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3)
			)
			ListItem(
				onClick = { public = !public },
				content = { Text(stringResource(Res.string.option_playlist_public)) },
				supportingContent = { Text(stringResource(Res.string.info_playlist_public)) },
				trailingContent = { Switch(checked = public, onCheckedChange = null) }
			)
			Row(
				modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End)
			) {
				TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.action_cancel)) }
				Button(
					onClick = {
						scope.launch {
							saving = true
							try {
								sessionManager.api.updatePlaylist(
									playlist.id,
									name = playlistName.rename(name.text.toString().trim()),
									comment = description.text.toString(),
									public = public
								)
								snackBarManager.notify(Res.string.notice_playlist_saved)
								onSaved()
								onDismissRequest()
							} catch (e: Exception) {
								Logger.e("EditPlaylistSheet", "Failed to save playlist", e)
							} finally {
								saving = false
							}
						}
					},
					enabled = !saving && name.text.isNotBlank()
				) { Text(stringResource(Res.string.action_save)) }
			}
		}
	}
}
