package eu.depau.loak.ui.screens.album.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.persistentListOf
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.settings.ListViewMode
import eu.depau.loak.ui.components.common.SortButton
import eu.depau.loak.ui.components.sheets.SortSheet
import eu.depau.loak.ui.util.label

@Composable
fun AlbumListScreenSortButton(
	nested: Boolean,
	selectedSorting: DomainAlbumListType,
	onSetSorting: (DomainAlbumListType) -> Unit,
	selectedReversed: Boolean,
	onSetReversed: (Boolean) -> Unit,
	selectedViewMode: ListViewMode,
	onSetViewMode: (ListViewMode) -> Unit,
	selectedFilters: Set<DomainFilter>,
	onToggleFilter: (DomainFilter) -> Unit
) {
	val entries = remember {
		persistentListOf(
			DomainAlbumListType.AlphabeticalByArtist,
			DomainAlbumListType.AlphabeticalByName,
			DomainAlbumListType.Frequent,
			DomainAlbumListType.Recent,
			DomainAlbumListType.Newest,
			DomainAlbumListType.Highest,
			DomainAlbumListType.Random,
			DomainAlbumListType.Year
		)
	}
	SortButton(nested = nested) { onDismissRequest ->
		SortSheet(
			entries = entries,
			selectedSorting = selectedSorting,
			selectedReversed = selectedReversed,
			label = { it.label() },
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
