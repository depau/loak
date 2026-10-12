package eu.depau.loak.ui.screens.lyrics.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import eu.depau.loak.domain.models.lyrics.LyricsResult
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_change_lyrics_provider
import eu.depau.loak.generated.resources.action_karaoke
import eu.depau.loak.generated.resources.action_share_lyrics
import eu.depau.loak.generated.resources.action_sync_lyrics
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Fullscreen
import eu.depau.loak.icons.outlined.Lyrics
import eu.depau.loak.icons.outlined.Share
import eu.depau.loak.icons.outlined.SyncAlt
import org.jetbrains.compose.resources.stringResource

/**
 * The lyrics action buttons. Order is a contract (and a regression victim): provider picker,
 * sync, share, full-screen — a highlighted menu keeps them visible while open.
 */
@Composable
fun LyricsActions(
	providerResults: List<LyricsResult>,
	selectedProviderIndex: Int,
	isSelecting: Boolean,
	selectedIndicesSize: Int,
	providerMenuOpen: Boolean,
	onProviderMenuOpenChange: (Boolean) -> Unit,
	onSelectProvider: (Int) -> Unit,
	onSync: () -> Unit,
	onToggleSelecting: () -> Unit,
	onShare: () -> Unit,
	onFullScreen: (() -> Unit)? = null,
) {
	val canSync = providerResults.getOrNull(selectedProviderIndex)?.isSynced == true && providerResults.isNotEmpty()

	Row {
		if (!isSelecting && providerResults.isNotEmpty()) {
			Box {
				IconButton(onClick = { onProviderMenuOpenChange(true) }) {
					Icon(
						Icons.Outlined.Lyrics,
						stringResource(Res.string.action_change_lyrics_provider)
					)
				}
				DropdownMenu(
					expanded = providerMenuOpen,
					onDismissRequest = { onProviderMenuOpenChange(false) }
				) {
					providerResults.forEachIndexed { index, result ->
						DropdownMenuItem(
							text = { Text(result.providerName) },
							leadingIcon = {
								if (index == selectedProviderIndex) {
									Icon(
										Icons.Outlined.Check,
										contentDescription = null
									)
								}
							},
							onClick = { onSelectProvider(index) }
						)
					}
				}
			}
		}
		if (!isSelecting && canSync) IconButton(onClick = onSync) {
			Icon(Icons.Outlined.SyncAlt, stringResource(Res.string.action_sync_lyrics))
		}
		if (isSelecting) IconButton(onClick = onToggleSelecting) {
			Icon(Icons.Outlined.Close, stringResource(Res.string.action_cancel))
		}
		IconButton(
			onClick = onShare,
			enabled = !isSelecting || selectedIndicesSize > 0
		) {
			Icon(
				if (isSelecting) Icons.Outlined.Check else Icons.Outlined.Share,
				stringResource(Res.string.action_share_lyrics)
			)
		}
		if (onFullScreen != null && !isSelecting) IconButton(onClick = onFullScreen) {
			Icon(Icons.Outlined.Fullscreen, stringResource(Res.string.action_karaoke))
		}
	}
}
