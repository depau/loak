package eu.depau.loak.ui.screens.artist.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.domain.models.DomainArtistListType
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.settings.ListViewMode
import eu.depau.loak.ui.components.common.SortButton
import eu.depau.loak.ui.components.sheets.SortSheet

@Composable
fun ArtistListScreenSortButton(
	nested: Boolean,
	selectedSorting: DomainArtistListType,
	onSetSorting: (DomainArtistListType) -> Unit,
	selectedViewMode: ListViewMode,
	onSetViewMode: (ListViewMode) -> Unit,
	selectedFilters: Set<DomainFilter>,
	onToggleFilter: (DomainFilter) -> Unit
) {
	val entries = remember {
		persistentListOf(
			DomainArtistListType.AlphabeticalByName,
			DomainArtistListType.Random
		)
	}
	SortButton(nested = nested) { onDismissRequest ->
		SortSheet(
			entries = entries,
			selectedSorting = selectedSorting,
			label = { stringResource(it.displayName) },
			onSetSorting = onSetSorting,
			onDismissRequest = onDismissRequest,
			selectedViewMode = selectedViewMode,
			onSetViewMode = onSetViewMode,
			selectedFilters = selectedFilters,
			onToggleFilter = onToggleFilter
		)
	}
}
