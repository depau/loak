package eu.depau.loak.ui.screens.home.components

import eu.depau.loak.ui.util.verticalWheelToParent
import eu.depau.loak.ui.util.pageBy
import eu.depau.loak.ui.util.HorizontalScrollArrows
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.settings.ListViewMode
import eu.depau.loak.ui.components.common.SongRow
import eu.depau.loak.ui.components.layouts.ArtGridPlaceholder
import kotlinx.collections.immutable.persistentListOf
import org.koin.compose.koinInject

/**
 * Songs in columns of 4, swiped sideways like YT Music's Quick picks. [columnWidth] null:
 * one column across the screen with the next one peeking in.
 */
@Composable
fun SongColumns(
	songs: List<DomainSong>,
	columnWidth: Int?,
	onPlay: (index: Int) -> Unit
) {
	val downloadManager = koinInject<DownloadManager>()
	val allDownloads by downloadManager.allDownloads.collectAsStateWithLifecycle(persistentListOf())
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
						onClick = { onPlay(index) },
						download = allDownloads.find { it.songId == song.id },
						// sideways-scrolling: a swipe scrolls the list
						swipeable = false
					)
				}
			}
		}
	}
}

/** [SongColumns]' skeleton, while Quick picks are being built. */
@Composable
fun SongColumnsPlaceholder(columnWidth: Int?) {
	BoxWithConstraints(Modifier.fillMaxWidth()) {
		val width = columnWidth?.dp ?: (maxWidth - 16.dp - 48.dp)
		val columns = (maxWidth / width).toInt() + 1
		Row(Modifier.padding(horizontal = 4.dp)) {
			repeat(columns) {
				Column {
					repeat(4) {
						ArtGridPlaceholder(Modifier.width(width).height(ROW_HEIGHT), ListViewMode.List)
					}
				}
			}
		}
	}
}

private val ROW_HEIGHT = 80.dp
