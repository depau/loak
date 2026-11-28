package eu.depau.loak.ui.screens.home.components

import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.unavailable
import eu.depau.loak.ui.components.common.SpecialPlaylistCover
import eu.depau.loak.domain.models.PlaylistKind
import eu.depau.loak.ui.components.common.displayName
import eu.depau.loak.icons.filled.Sparkle
import eu.depau.loak.icons.outlined.Soundwave
import eu.depau.loak.ui.util.verticalWheelToParent
import eu.depau.loak.ui.util.HorizontalScrollArrows
import eu.depau.loak.ui.util.onSecondaryClick
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.material3.Surface
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
import eu.depau.loak.ui.util.shimmerLoading
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import eu.depau.loak.icons.filled.Play
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.repositories.SpeedDialItem
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.subtitle_feeling_lucky
import eu.depau.loak.generated.resources.title_feeling_lucky
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Keep
import eu.depau.loak.icons.outlined.Casino
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.sheets.CollectionSheet
import eu.depau.loak.ui.components.sheets.SongActionsSheet
import eu.depau.loak.ui.navigation.Screen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** How Speed dial lays out its pages of [PAGE_SIZE]. */
enum class SpeedDialLayout(val columns: Int) {
	/** Phones: a 2×3 page across the width. */
	Grid(3),

	/** Medium windows: a 2×3 page with the next one peeking in. */
	GridPeek(3),

	/** Expanded windows: each page is one row of 6, with the next one peeking in. */
	Row(PAGE_SIZE);

	/** A tile's side in [width]: phones fill it, wider windows keep tiles at most 140 dp. */
	fun tile(width: Dp, gap: Dp): Dp {
		val fit = (width - gap * (columns - 1)) / columns
		return if (this == Grid) fit else minOf(140.dp, fit)
	}
}

private const val PAGE_SIZE = 6

/** Speed dial: pages of [PAGE_SIZE] tiles. */
@Composable
fun SpeedDial(
	items: List<SpeedDialItem>,
	layout: SpeedDialLayout,
	onPlayRadio: (SpeedDialItem.Song) -> Unit
) {
	val pages = items.chunked(PAGE_SIZE)
	val pagerState = rememberPagerState { pages.size }
	val gap = 8.dp

	BoxWithConstraints(Modifier.fillMaxWidth()) {
		val tile = layout.tile(maxWidth - 32.dp, gap)
		val pageWidth = tile * layout.columns + gap * (layout.columns - 1)
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
					Column(verticalArrangement = Arrangement.spacedBy(gap)) {
						pages[page].chunked(layout.columns).forEach { row ->
							Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
								row.forEach { SpeedDialTile(it, tile, onPlayRadio) }
							}
						}
					}
				}
			}
			if (pages.size > 1) PageDots(pages.size, pagerState.currentPage)
		}
	}
}

/** [SpeedDial]'s first page as a skeleton, while Home loads. */
@Composable
fun SpeedDialPlaceholder(layout: SpeedDialLayout) {
	val gap = 8.dp
	BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
		val tile = layout.tile(maxWidth, gap)
		Column(verticalArrangement = Arrangement.spacedBy(gap)) {
			repeat(PAGE_SIZE / layout.columns) {
				Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
					repeat(layout.columns) {
						Box(Modifier.size(tile).clip(RoundedCornerShape(12.dp)).shimmerLoading())
					}
				}
			}
		}
	}
}

/** Feeling lucky, under Speed dial: plays a random song's radio. Texts sized as song rows'. */
@Composable
fun FeelingLuckyCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
	// a Surface, as Sonic journey's card: it sets the content color the title is drawn in
	Surface(
		onClick = onClick,
		shape = RoundedCornerShape(12.dp),
		color = MaterialTheme.colorScheme.surfaceContainer,
		modifier = modifier.fillMaxWidth()
	) {
		Row(
			Modifier.height(56.dp).padding(start = 10.dp, end = 12.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			Box(
				Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
				contentAlignment = Alignment.Center
			) {
				Icon(
					Icons.Outlined.Casino,
					null,
					Modifier.size(22.dp),
					tint = MaterialTheme.colorScheme.onPrimaryContainer
				)
			}
			Column(Modifier.weight(1f)) {
				Text(
					stringResource(Res.string.title_feeling_lucky),
					style = MaterialTheme.typography.bodyLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				Text(
					stringResource(Res.string.subtitle_feeling_lucky),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
			Icon(Icons.Filled.Play, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
		}
	}
}

/** [FeelingLuckyCard]'s skeleton, while Home loads. */
@Composable
fun FeelingLuckyCardPlaceholder(modifier: Modifier = Modifier) {
	Box(modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(12.dp)).shimmerLoading())
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
		is SpeedDialItem.Playlist -> item.playlist.displayName().display to item.playlist.coverArtId
		is SpeedDialItem.Album -> item.album.name.orEmpty() to item.album.coverArtId
		is SpeedDialItem.Song -> item.song.title to item.song.coverArtId
	}
	val availability = LocalAvailability.current
	val available = when (item) {
		is SpeedDialItem.Playlist -> availability.collection(item.playlist.id)
		is SpeedDialItem.Album -> availability.collection(item.album.id)
		// a song tile plays its instant mix, from the server
		is SpeedDialItem.Song -> availability.online
	}

	Box(
		Modifier
			.size(size)
			.unavailable(!available)
			.clip(RoundedCornerShape(12.dp))
			.onSecondaryClick { menuShown = true }
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
		(item as? SpeedDialItem.Playlist)?.let {
			SpecialPlaylistCover(it.playlist.displayName(), Modifier.matchParentSize(), showTitle = false)
		}
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
		val kind = (item as? SpeedDialItem.Playlist)?.playlist?.displayName()?.kind
		if (kind != null && kind != PlaylistKind.Regular) {
			Box(
				Modifier
					.align(Alignment.TopEnd)
					.padding(6.dp)
					.size(22.dp)
					.background(Color.Black.copy(alpha = .45f), CircleShape),
				contentAlignment = Alignment.Center
			) {
				Icon(
					if (kind == PlaylistKind.Smart) Icons.Outlined.Soundwave else Icons.Filled.Sparkle,
					null, Modifier.size(13.dp), tint = Color.White
				)
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

	when (item) {
		is SpeedDialItem.Song -> SongActionsSheet(
			song = item.song,
			open = menuShown,
			onDismissRequest = { menuShown = false }
		)
		else -> if (menuShown) {
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
