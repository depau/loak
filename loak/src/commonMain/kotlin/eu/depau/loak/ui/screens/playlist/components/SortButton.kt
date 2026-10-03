package eu.depau.loak.ui.screens.playlist.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.DomainPlaylistListType
import eu.depau.loak.domain.models.settings.ListViewMode
import eu.depau.loak.ui.components.common.SortButton
import eu.depau.loak.ui.components.sheets.SortSheet

@Composable
fun PlaylistListScreenSortButton(
	nested: Boolean,
	selectedSorting: DomainPlaylistListType,
	onSetSorting: (DomainPlaylistListType) -> Unit,
	selectedReversed: Boolean,
	onSetReversed: (Boolean) -> Unit,
	selectedViewMode: ListViewMode,
	onSetViewMode: (ListViewMode) -> Unit,
	selectedFilters: Set<DomainFilter>,
	onToggleFilter: (DomainFilter) -> Unit
) {
	val entries = remember { DomainPlaylistListType.entries.toImmutableList() }
	SortButton(nested = nested) { onDismissRequest ->
		SortSheet(
			entries = entries,
			selectedSorting = selectedSorting,
			selectedReversed = selectedReversed,
			label = { stringResource(it.displayName) },
			onSetSorting = onSetSorting,
			onSetReversed = onSetReversed,
			onDismissRequest = onDismissRequest,
			selectedViewMode = selectedViewMode,
			onSetViewMode = onSetViewMode,
			selectedFilters = selectedFilters,
			onToggleFilter = onToggleFilter
		)
	}
}
