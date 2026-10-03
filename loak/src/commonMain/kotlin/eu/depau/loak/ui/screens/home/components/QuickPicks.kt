package eu.depau.loak.ui.screens.home.components

import eu.depau.loak.ui.util.verticalWheelToParent
import eu.depau.loak.ui.util.pageBy
import eu.depau.loak.ui.util.HorizontalScrollArrows
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.SongRow
import eu.depau.loak.ui.screens.home.viewmodels.HomeViewModel
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.koinInject

/**
 * Songs in columns of 4, swiped sideways like YT Music's Quick picks. [columnWidth] null:
 * one column across the screen with the next one peeking in.
 */
@Composable
fun SongColumns(
	songs: List<DomainSong>,
	viewModel: HomeViewModel,
	columnWidth: Int?,
	onSetShareId: (String) -> Unit,
	onPlay: (index: Int) -> Unit
) {
	val player = koinInject<MediaPlayerViewModel>()
	val downloadManager = koinInject<DownloadManager>()
	val connectivityManager = koinInject<ConnectivityManager>()
	val allDownloads by downloadManager.allDownloads.collectAsStateWithLifecycle(persistentListOf())
	val isOnline by connectivityManager.isOnline.collectAsStateWithLifecycle()
	val selection by viewModel.selectedSong.collectAsStateWithLifecycle()
	val starred by viewModel.selectedSongStarred.collectAsStateWithLifecycle()
	val rating by viewModel.selectedSongRating.collectAsStateWithLifecycle()
	val gridState = rememberLazyGridState()

	BoxWithConstraints(Modifier.fillMaxWidth()) {
		val width = columnWidth?.dp ?: (maxWidth - 16.dp - 48.dp)
		HorizontalScrollArrows(
			canScrollBackward = gridState.canScrollBackward,
			canScrollForward = gridState.canScrollForward,
			onBackward = { gridState.pageBy(-1) },
			onForward = { gridState.pageBy(1) }
		) {
			LazyHorizontalGrid(
				rows = GridCells.Fixed(4),
				state = gridState,
				flingBehavior = rememberSnapFlingBehavior(lazyGridState = gridState),
				contentPadding = PaddingValues(horizontal = 4.dp),
				modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT * 4).verticalWheelToParent()
			) {
				itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
					SongRow(
						modifier = Modifier.width(width),
						song = song,
						selected = selection == song,
						onClick = { onPlay(index) },
						onLongClick = { viewModel.selectSong(song) },
						isOnline = isOnline,
						onDismissRequest = { viewModel.clearSongSelection() },
						onRemoveStar = { viewModel.starSelectedSong(false) },
						onAddStar = { viewModel.starSelectedSong(true) },
						onShare = { onSetShareId(song.id) },
						starredState = if (selection == song) starred else song.starredAt != null,
						download = allDownloads.find { it.songId == song.id },
						onDownload = { downloadManager.downloadSong(song) },
						onCancelDownload = { downloadManager.cancelDownload(song.id) },
						onDeleteDownload = { downloadManager.deleteDownload(song.id) },
						onPlayNext = { player.playNextSingle(song) },
						onAddToQueue = { player.addToQueueSingle(song) },
						rating = rating,
						onSetRating = { viewModel.rateSelectedSong(it) }
					)
				}
			}
		}
	}
}

private val ROW_HEIGHT = 80.dp
