package eu.depau.loak.ui.screens.home.components

import eu.depau.loak.ui.util.verticalWheelToParent
import eu.depau.loak.ui.util.HorizontalScrollArrows
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.repositories.SpeedDialItem
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_feeling_lucky
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Keep
import eu.depau.loak.icons.outlined.Casino
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.sheets.CollectionSheet
import eu.depau.loak.ui.components.sheets.SongSheet
import eu.depau.loak.ui.navigation.Screen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** How Speed dial lays out its pages of 9. */
enum class SpeedDialLayout {
	/** Phones: a 3×3 page across the width. */
	Grid,

	/** Medium windows: a 3×3 page with the next one peeking in. */
	GridPeek,

	/** Expanded windows: each page is one row of 9. */
	Row
}

/**
 * Speed dial: pages of 9 tiles; the first ends with the dice, which plays a random song's
 * radio.
 */
@Composable
fun SpeedDial(
	items: List<SpeedDialItem>,
	layout: SpeedDialLayout,
	onPlayRadio: (SpeedDialItem.Song) -> Unit,
	onFeelingLucky: () -> Unit
) {
	// the dice takes the first page's last slot
	val pages = (listOf<SpeedDialItem?>() + items.take(8) + null + items.drop(8)).chunked(9)
	val pagerState = rememberPagerState { pages.size }
	val gap = 8.dp

	BoxWithConstraints(Modifier.fillMaxWidth()) {
		val width = maxWidth - 32.dp
		val tile = when (layout) {
			SpeedDialLayout.Grid -> (width - gap * 2) / 3
			SpeedDialLayout.GridPeek -> minOf(140.dp, (width - gap * 2) / 3)
			SpeedDialLayout.Row -> (width - gap * 8) / 9
		}
		val pageWidth = if (layout == SpeedDialLayout.Row) tile * 9 + gap * 8 else tile * 3 + gap * 2
		Column {
			HorizontalScrollArrows(
				canScrollBackward = pagerState.canScrollBackward,
				canScrollForward = pagerState.canScrollForward,
				onBackward = { pagerState.animateScrollToPage(pagerState.currentPage - 1) },
				onForward = { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
			) {
				HorizontalPager(
					state = pagerState,
					modifier = Modifier.verticalWheelToParent(),
					pageSize = PageSize.Fixed(pageWidth),
					pageSpacing = 24.dp,
					contentPadding = PaddingValues(horizontal = 16.dp),
					verticalAlignment = Alignment.Top
				) { page ->
					val cells = pages[page]
					val rows = if (layout == SpeedDialLayout.Row) listOf(cells) else cells.chunked(3)
					Column(verticalArrangement = Arrangement.spacedBy(gap)) {
						rows.forEach { row ->
							Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
								row.forEach { item ->
									if (item == null) DiceTile(tile, onFeelingLucky)
									else SpeedDialTile(item, tile, onPlayRadio)
								}
							}
						}
					}
				}
			}
			if (pages.size > 1) PageDots(pages.size, pagerState.currentPage)
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialTile(
	item: SpeedDialItem,
	size: Dp,
	onPlayRadio: (SpeedDialItem.Song) -> Unit
) {
	val backStack = LocalNavStack.current
	val player = koinInject<MediaPlayerViewModel>()
	var menuShown by rememberSaveable { mutableStateOf(false) }
	val (title, coverArtId) = when (item) {
		is SpeedDialItem.Playlist -> item.playlist.name.orEmpty() to item.playlist.coverArtId
		is SpeedDialItem.Album -> item.album.name.orEmpty() to item.album.coverArtId
		is SpeedDialItem.Song -> item.song.title to item.song.coverArtId
	}

	Box(
		Modifier
			.size(size)
			.clip(RoundedCornerShape(12.dp))
			.combinedClickable(
				onClick = dropUnlessResumed {
					when (item) {
						is SpeedDialItem.Playlist ->
							backStack.add(Screen.CollectionDetail(item.playlist.id, TAB))
						is SpeedDialItem.Album ->
							backStack.add(Screen.CollectionDetail(item.album.id, TAB))
						is SpeedDialItem.Song -> onPlayRadio(item)
					}
				},
				onLongClick = { menuShown = true }
			)
	) {
		CoverArt(coverArtId = coverArtId, modifier = Modifier.size(size), shape = RoundedCornerShape(12.dp))
		Row(
			modifier = Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .62f))))
				.padding(start = 8.dp, end = 6.dp, top = 18.dp, bottom = 7.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Text(
				title,
				modifier = Modifier.weight(1f, fill = false),
				color = Color.White,
				style = MaterialTheme.typography.labelMedium,
				fontWeight = FontWeight.Medium,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
			// a caret tells lists, which open, from songs, which play
			if (item !is SpeedDialItem.Song) {
				Icon(Icons.Outlined.ChevronForward, null, Modifier.size(14.dp), tint = Color.White)
			}
		}
		if (item.pinned) {
			Box(
				Modifier
					.padding(6.dp)
					.size(22.dp)
					.background(Color.Black.copy(alpha = .45f), CircleShape),
				contentAlignment = Alignment.Center
			) {
				Icon(Icons.Filled.Keep, null, Modifier.size(14.dp), tint = Color.White)
			}
		}
	}

	if (menuShown) when (item) {
		is SpeedDialItem.Song -> SongSheet(
			onDismissRequest = { menuShown = false },
			song = item.song,
			onPlayNext = { player.playNextSingle(item.song) },
			onAddToQueue = { player.addToQueueSingle(item.song) }
		)
		else -> {
			val collection = (item as? SpeedDialItem.Album)?.album
				?: (item as SpeedDialItem.Playlist).playlist
			CollectionSheet(
				onDismissRequest = { menuShown = false },
				collection = collection,
				onPlayNext = { player.playNext(collection) },
				onAddToQueue = { player.addToQueue(collection) }
			)
		}
	}
}

@Composable
private fun DiceTile(size: Dp, onClick: () -> Unit) {
	val label = stringResource(Res.string.action_feeling_lucky)
	Box(
		Modifier
			.size(size)
			.clip(RoundedCornerShape(12.dp))
			.background(MaterialTheme.colorScheme.primaryContainer)
			.combinedClickable(onClick = onClick)
			.semantics { contentDescription = label },
		contentAlignment = Alignment.Center
	) {
		Icon(
			Icons.Outlined.Casino,
			null,
			Modifier.size(minOf(48.dp, size / 2)),
			tint = MaterialTheme.colorScheme.onPrimaryContainer
		)
	}
}

@Composable
private fun PageDots(count: Int, current: Int) {
	Row(
		Modifier.fillMaxWidth().padding(top = 10.dp).height(6.dp),
		horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
	) {
		repeat(count) { page ->
			Box(
				Modifier.size(6.dp).background(
					if (page == current) MaterialTheme.colorScheme.primary
					else MaterialTheme.colorScheme.outlineVariant,
					CircleShape
				)
			)
		}
	}
}

/** The shared-element tab name for cards opened from Home. */
internal const val TAB = "home"
