package eu.depau.loak.ui.screens.playlist.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.PlaylistKind
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.filter_ai
import eu.depau.loak.generated.resources.filter_all
import eu.depau.loak.generated.resources.filter_radios
import eu.depau.loak.generated.resources.filter_smart
import eu.depau.loak.generated.resources.filter_yours
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource

/** The Playlists tab's chips, like YT Music's Library: who made a playlist, and how. */
@Serializable
enum class PlaylistKindFilter {
	All, Yours, AI, Radios, Smart;

	fun matches(playlist: DomainPlaylist, kind: PlaylistKind, username: String) = when (this) {
		All -> true
		Yours -> playlist.owner == username
		AI -> kind.isAudioMuse
		Radios -> kind == PlaylistKind.AudioMuseRadio
		Smart -> kind == PlaylistKind.Smart
	}

	val label get() = when (this) {
		All -> Res.string.filter_all
		Yours -> Res.string.filter_yours
		AI -> Res.string.filter_ai
		Radios -> Res.string.filter_radios
		Smart -> Res.string.filter_smart
	}
}

/** Only the chips that would show something. */
@Composable
fun PlaylistKindFilterRow(
	available: List<PlaylistKindFilter>,
	selected: PlaylistKindFilter,
	onSelect: (PlaylistKindFilter) -> Unit,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		available.forEach { filter ->
			FilterChip(
				selected = filter == selected,
				onClick = { onSelect(filter) },
				label = { Text(stringResource(filter.label)) }
			)
		}
	}
}
