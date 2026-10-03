package eu.depau.loak.ui.screens.playlist.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.manager.NavidromeManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.Rule
import eu.depau.loak.domain.models.RuleFieldType
import eu.depau.loak.domain.models.RuleGroup
import eu.depau.loak.domain.models.RuleNode
import eu.depau.loak.domain.models.fieldType
import eu.depau.loak.domain.manager.SmartPlaylistDetails
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_edit
import eu.depau.loak.generated.resources.info_smart_from_file
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Soundwave
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private fun RuleNode.hasPlaylistRule(): Boolean = when (this) {
	is Rule -> fieldType() == RuleFieldType.Playlist
	is RuleGroup -> children.any { it.hasPlaylistRule() }
}

/**
 * A smart playlist's rules in a line or two, on its page. Navidrome only: the rules come from
 * its own API. [onEdit] is offered when the user may change them.
 */
@Composable
fun SmartRulesCard(
	playlist: DomainPlaylist,
	modifier: Modifier = Modifier,
	onEdit: ((SmartPlaylistDetails) -> Unit)? = null
) {
	if (playlist.validUntil == null) return
	val navidrome = koinInject<NavidromeManager>()
	val sessionManager = koinInject<SessionManager>()
	val details by produceState<SmartPlaylistDetails?>(null, playlist.id, playlist.modifiedAt) {
		value = runCatching {
			if (navidrome.serverInfo().isNavidrome) navidrome.smartPlaylist(playlist.id) else null
		}.getOrNull()
	}
	val criteria = details?.criteria ?: return
	val playlistNames by produceState(emptyMap<String, String>(), criteria) {
		if (criteria.root.hasPlaylistRule()) value = runCatching {
			sessionManager.api.getPlaylists().associate { it.id to it.name }
		}.getOrDefault(emptyMap())
	}

	Surface(
		modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		contentColor = MaterialTheme.colorScheme.onSurfaceVariant
	) {
		Row(
			modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Icon(Icons.Outlined.Soundwave, null, Modifier.size(20.dp))
			Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
				Text(ruleSummary(criteria.root, playlistNames), style = MaterialTheme.typography.bodySmall)
				val second = if (details!!.fromFile) stringResource(Res.string.info_smart_from_file)
				else orderSummary(criteria)
				second?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
			}
			if (onEdit != null && !details!!.fromFile && playlist.owner == sessionManager.username) {
				TextButton(onClick = { onEdit(details!!) }) { Text(stringResource(Res.string.action_edit)) }
			}
		}
	}
}
