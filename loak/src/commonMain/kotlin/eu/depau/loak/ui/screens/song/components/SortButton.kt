package eu.depau.loak.ui.screens.song.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.collections.immutable.persistentListOf
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.DomainSongListType
import eu.depau.loak.ui.components.common.SortButton
import eu.depau.loak.ui.components.sheets.SortSheet
import eu.depau.loak.ui.util.label

@Composable
fun SongListScreenSortButton(
	nested: Boolean,
	selectedSorting: DomainSongListType,
	onSetSorting: (listType: DomainSongListType) -> Unit,
	selectedReversed: Boolean,
	onSetReversed: (Boolean) -> Unit,
	selectedFilters: Set<DomainFilter>,
	onToggleFilter: (DomainFilter) -> Unit
) {
	val entries = remember {
		persistentListOf(
			DomainSongListType.FrequentlyPlayed,
			DomainSongListType.Newest,
			DomainSongListType.Random,
			DomainSongListType.Rating,
			DomainSongListType.Year
		)
	}
	SortButton(nested = nested) { onDismissRequest ->
		SortSheet(
			entries = entries,
			onDismissRequest = onDismissRequest,
			selectedSorting = selectedSorting,
			onSetSorting = onSetSorting,
			selectedReversed = selectedReversed,
			label = { it.label() },
			onSetReversed = onSetReversed,
			selectedFilters = selectedFilters,
			onToggleFilter = onToggleFilter
		)
	}
}
