package eu.depau.loak.ui.components.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_download
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.ui.components.dialogs.DialogButton

@Composable
fun BulkDownloadDialog(
	title: String,
	message: String,
	showDialog: Boolean,
	onDismissRequest: () -> Unit,
	onConfirm: () -> Unit
) {
	if (showDialog) {
		FormDialog(
			onDismissRequest = onDismissRequest,
			icon = { Icon(Icons.Outlined.Download, null) },
			title = { Text(title) },
			content = { Text(message) },
			buttons = {
				DialogButton(
					onClick = onDismissRequest,
				) {
					Text(stringResource(Res.string.action_cancel))
				}
				DialogButton(
					onClick = {
						onConfirm()
						onDismissRequest()
					}
				) {
					Text(stringResource(Res.string.action_download))
				}
			}
		)
	}
}
