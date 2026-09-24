package eu.depau.loak.ui.screens.playlist.components

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_no_playlists_short
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.settings.ListViewMode
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.PlaylistRemove
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.components.layouts.artGridPlaceholder
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.util.loakAnimateItem

fun LazyGridScope.playlistListScreenContent(
	state: UiState<List<DomainPlaylist>>,
	selectedPlaylist: DomainPlaylist?,
	selectedViewMode: ListViewMode,
	onUpdateSelection: (DomainPlaylist) -> Unit,
	onClearSelection: () -> Unit,
	onSetShareId: (String) -> Unit,
	onSetDeletionId: (String) -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
) {
	val data = state.data.orEmpty()
	if (data.isNotEmpty()) {
		items(data, { it.id }) { playlist ->
			if (selectedViewMode == ListViewMode.Grid) {
				PlaylistListScreenGridItem(
					modifier = loakAnimateItem(),
					tab = "playlists",
					playlist = playlist,
					selected = playlist == selectedPlaylist,
					onSelect = { onUpdateSelection(playlist) },
					onDeselect = { onClearSelection() },
					onSetShareId = onSetShareId,
					onSetDeletionId = onSetDeletionId,
					onPlayNext = onPlayNext,
					onAddToQueue = onAddToQueue,
				)
			} else {
				PlaylistListScreenListItem(
					modifier = loakAnimateItem(),
					playlist = playlist,
					selected = playlist == selectedPlaylist,
					onSelect = { onUpdateSelection(playlist) },
					onDeselect = { onClearSelection() },
					onSetShareId = onSetShareId,
					onSetDeletionId = onSetDeletionId,
					onPlayNext = onPlayNext,
					onAddToQueue = onAddToQueue,
				)
			}
		}
	} else {
		when (state) {
			is UiState.Loading -> {
				artGridPlaceholder(viewMode = selectedViewMode)
			}

			else -> {
				item(span = { GridItemSpan(maxLineSpan) }) {
					ContentUnavailable(
						icon = Icons.Outlined.PlaylistRemove,
						label = stringResource(Res.string.info_no_playlists_short)
					)
				}
			}
		}
	}
}
