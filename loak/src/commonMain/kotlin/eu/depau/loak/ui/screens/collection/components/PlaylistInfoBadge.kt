package eu.depau.loak.ui.screens.collection.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.PlaylistKind
import eu.depau.loak.domain.models.PlaylistName
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_got_it
import eu.depau.loak.generated.resources.action_make_a_copy
import eu.depau.loak.generated.resources.info_badge_audiomuse_automatic
import eu.depau.loak.generated.resources.info_badge_audiomuse_instant
import eu.depau.loak.generated.resources.info_badge_audiomuse_scheduled
import eu.depau.loak.generated.resources.info_badge_smart
import eu.depau.loak.generated.resources.title_badge_audiomuse
import eu.depau.loak.generated.resources.title_badge_audiomuse_instant
import eu.depau.loak.generated.resources.title_badge_smart
import eu.depau.loak.ui.components.common.PlaylistBadge
import eu.depau.loak.ui.screens.playlist.dialogs.CopyPlaylistDialog
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** The playlist's badge; tapping it explains what it means. */
@Composable
fun PlaylistInfoBadge(playlist: DomainPlaylist, name: PlaylistName) {
	val (title, info) = when (name.kind) {
		PlaylistKind.Regular -> return
		PlaylistKind.Smart -> Res.string.title_badge_smart to Res.string.info_badge_smart
		PlaylistKind.AudioMuseInstant ->
			Res.string.title_badge_audiomuse_instant to Res.string.info_badge_audiomuse_instant
		PlaylistKind.AudioMuseScheduled ->
			Res.string.title_badge_audiomuse to Res.string.info_badge_audiomuse_scheduled
		PlaylistKind.AudioMuseAutomatic ->
			Res.string.title_badge_audiomuse to Res.string.info_badge_audiomuse_automatic
	}
	val state = rememberTooltipState(isPersistent = true)
	val scope = rememberCoroutineScope()
	var copying by rememberSaveable { mutableStateOf(false) }

	TooltipBox(
		positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
			positioning = TooltipAnchorPosition.Below
		),
		tooltip = {
			RichTooltip(
				title = { Text(stringResource(title)) },
				action = {
					Row {
						if (name.kind.isRebuilt) TextButton(onClick = {
							state.dismiss()
							copying = true
						}) { Text(stringResource(Res.string.action_make_a_copy)) }
						TextButton(onClick = { state.dismiss() }) {
							Text(stringResource(Res.string.action_got_it))
						}
					}
				}
			) { Text(stringResource(info)) }
		},
		state = state
	) {
		PlaylistBadge(name.kind, Modifier.clickable { scope.launch { state.show() } })
	}

	if (copying) CopyPlaylistDialog(playlist = playlist, onDismissRequest = { copying = false })
}
