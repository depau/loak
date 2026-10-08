package eu.depau.loak.ui.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isExpanded
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel_download
import eu.depau.loak.generated.resources.action_delete_download
import eu.depau.loak.generated.resources.action_download
import eu.depau.loak.generated.resources.info_click_to_retry
import eu.depau.loak.generated.resources.info_download_failed
import eu.depau.loak.generated.resources.label_open_in
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.brand.Lastfm
import eu.depau.loak.icons.brand.Musicbrainz
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.DownloadOff
import eu.depau.loak.ui.components.common.TooltipBox
import org.jetbrains.compose.resources.stringResource

/**
 * One button of a [SheetActionBar]. [label] is its tooltip and what screen readers say.
 * [checked] is null for plain actions, or the state of a toggle (starred, downloaded).
 */
class SheetAction(
	val label: String,
	val icon: ImageVector,
	val onClick: () -> Unit,
	val checked: Boolean? = null,
	val enabled: Boolean = true,
	/** Instead of the content colour, for states that need it (a failed download). */
	val tint: Color? = null
)

/**
 * The options sheets' main verbs as one connected group of icon buttons, the same on phones and
 * in the desktop menu (denser there). Toggles that are on take the round, filled shape.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SheetActionBar(actions: List<SheetAction>, modifier: Modifier = Modifier) {
	if (actions.isEmpty()) return
	val height = if (LocalPlatformContext.current.isExpanded()) 44.dp else 56.dp
	Row(
		modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
		horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
	) {
		actions.forEachIndexed { index, action ->
			val shapes = when {
				actions.size == 1 -> ToggleButtonDefaults.shapes()
				index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
				index == actions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
				else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
			}
			// weighted through TooltipBox, the first button took the whole row
			Box(Modifier.weight(1f)) {
				TooltipBox(action.label) {
					ToggleButton(
						checked = action.checked == true,
						onCheckedChange = { action.onClick() },
						enabled = action.enabled,
						shapes = shapes,
						contentPadding = PaddingValues(0.dp),
						modifier = Modifier
							.fillMaxWidth()
							.height(height)
							.let {
								// plain actions are buttons, not switches, to screen readers
								if (action.checked != null) it
								else it.clearAndSetSemantics {
									role = Role.Button
									contentDescription = action.label
									onClick { action.onClick(); true }
								}
							}
					) {
						Icon(action.icon, action.label, tint = action.tint ?: LocalContentColor.current)
					}
				}
			}
		}
	}
}

/**
 * The download button for an item in [status]: download it, cancel it, delete it (shown as
 * on) or, after a failure, retry. Null when there's nothing to offer.
 */
@Composable
fun downloadAction(
	status: DownloadStatus?,
	onDownload: (() -> Unit)?,
	onCancel: (() -> Unit)?,
	onDelete: (() -> Unit)?,
	onDismissRequest: () -> Unit,
	/** For starting a download: false when there's nothing to download. */
	enabled: Boolean = true
): SheetAction? {
	fun then(action: (() -> Unit)?): () -> Unit = {
		action?.invoke()
		onDismissRequest()
	}
	val download = stringResource(Res.string.action_download)
	return when (status) {
		DownloadStatus.DOWNLOADING ->
			SheetAction(stringResource(Res.string.action_cancel_download), Icons.Outlined.Close, then(onCancel))
		DownloadStatus.DOWNLOADED -> SheetAction(
			stringResource(Res.string.action_delete_download),
			Icons.Outlined.Check,
			then(onDelete),
			checked = true
		)
		DownloadStatus.FAILED -> SheetAction(
			"${stringResource(Res.string.info_download_failed)} · ${stringResource(Res.string.info_click_to_retry)}",
			Icons.Outlined.DownloadOff,
			then(onDownload),
			tint = MaterialTheme.colorScheme.error
		)
		null -> onDownload?.let { SheetAction(download, Icons.Outlined.Download, then(it), enabled = enabled) }
		else -> SheetAction(download, Icons.Outlined.Download, then(onDownload), enabled = enabled)
	}
}

/** An item's pages elsewhere, as chips after "Open in"; nothing when it has none. */
@Composable
fun SheetLinks(lastFmUrl: String?, musicBrainzUrl: String?, onOpen: (url: String) -> Unit) {
	if (lastFmUrl == null && musicBrainzUrl == null) return
	Row(
		Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(
			stringResource(Res.string.label_open_in),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
		// brand names, not translated
		lastFmUrl?.let { LinkChip("last.fm", Icons.Brand.Lastfm) { onOpen(it) } }
		musicBrainzUrl?.let { LinkChip("MusicBrainz", Icons.Brand.Musicbrainz) { onOpen(it) } }
	}
}

@Composable
private fun LinkChip(label: String, icon: ImageVector, onClick: () -> Unit) {
	AssistChip(
		onClick = onClick,
		label = { Text(label) },
		leadingIcon = { Icon(icon, null, Modifier.size(AssistChipDefaults.IconSize)) }
	)
}
