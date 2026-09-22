package eu.depau.loak.ui.screens.song.components

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.collections.immutable.persistentListOf
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.DomainSongListType
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Sort
import eu.depau.loak.ui.components.layouts.TopBarButton
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
	var expanded by remember { mutableStateOf(false) }
	if (!nested) {
		IconButton(onClick = {
			expanded = true
		}) {
			Icon(
				imageVector = Icons.Outlined.Sort,
				contentDescription = null
			)
		}
	} else {
		TopBarButton(onClick = { expanded = true }) {
			Icon(
				imageVector = Icons.Outlined.Sort,
				contentDescription = null
			)
		}
	}
	if (expanded) {
		SortSheet(
			entries = entries,
			onDismissRequest = { expanded = false },
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
