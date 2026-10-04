package eu.depau.loak.ui.screens.playlist.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.snackbars.PlayerEvent
import eu.depau.loak.domain.repositories.PlaylistRepository
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_make_a_copy
import eu.depau.loak.generated.resources.action_open
import eu.depau.loak.generated.resources.info_copy_playlist
import eu.depau.loak.generated.resources.label_name
import eu.depau.loak.generated.resources.label_playlist_copy_name
import eu.depau.loak.generated.resources.notice_copied_playlist
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Copy
import eu.depau.loak.ui.components.common.displayName
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.util.Logger
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Saves the playlist's songs, as they are now, into a new playlist of the user's. Works for
 * any playlist: shared, smart, or one AudioMuse-AI will rebuild (the copy drops its marker).
 */
@Composable
fun CopyPlaylistDialog(playlist: DomainPlaylist, onDismissRequest: () -> Unit) {
	val snackBarManager = koinInject<SnackBarManager>()
	val playlistRepository = koinInject<PlaylistRepository>()
	val backStack = LocalNavStack.current
	val shown = playlist.displayName().display
	val name = rememberTextFieldState(stringResource(Res.string.label_playlist_copy_name, shown))
	var copying by remember { mutableStateOf(false) }

	AlertDialog(
		onDismissRequest = onDismissRequest,
		icon = { Icon(Icons.Outlined.Copy, null) },
		title = { Text(stringResource(Res.string.action_make_a_copy)) },
		text = {
			Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
				Text(stringResource(Res.string.info_copy_playlist, shown))
				TextField(
					state = name,
					label = { Text(stringResource(Res.string.label_name)) },
					lineLimits = TextFieldLineLimits.SingleLine
				)
			}
		},
		confirmButton = {
			TextButton(
				enabled = !copying && name.text.isNotBlank(),
				onClick = {
					copying = true
					val newName = name.text.toString().trim()
					// outlives the dialog, which closes right away
					snackBarManager.launch {
						try {
							// the songs as last synced, so this works offline too
							val copyId = playlistRepository.create(newName, playlist.songs)
							snackBarManager.notify(
								PlayerEvent(
									Res.string.notice_copied_playlist, listOf(newName),
									action = Res.string.action_open,
									onAction = { backStack.add(Screen.CollectionDetail(copyId, "")) }
								)
							)
						} catch (e: Exception) {
							Logger.e("CopyPlaylistDialog", "Failed to copy playlist", e)
						}
					}
					onDismissRequest()
				}
			) { Text(stringResource(Res.string.action_make_a_copy)) }
		},
		dismissButton = {
			TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.action_cancel)) }
		}
	)
}
