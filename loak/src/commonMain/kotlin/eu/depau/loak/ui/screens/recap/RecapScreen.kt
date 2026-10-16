package eu.depau.loak.ui.screens.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.history.RecapData
import eu.depau.loak.domain.history.RecapDay
import eu.depau.loak.domain.history.RecapEntry
import eu.depau.loak.domain.history.RecapPeriod
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.manager.ShareManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_share
import eu.depau.loak.generated.resources.info_recap_empty
import eu.depau.loak.generated.resources.info_recap_from
import eu.depau.loak.generated.resources.info_recap_plays_fewer
import eu.depau.loak.generated.resources.info_recap_plays_more
import eu.depau.loak.generated.resources.info_recap_plays_same
import eu.depau.loak.generated.resources.info_recap_share
import eu.depau.loak.generated.resources.info_recap_source_local
import eu.depau.loak.generated.resources.info_recap_source_local_and
import eu.depau.loak.generated.resources.info_recap_source_unreadable
import eu.depau.loak.generated.resources.info_recap_plays_count
import eu.depau.loak.generated.resources.label_recap_artists
import eu.depau.loak.generated.resources.label_recap_heatmap_less
import eu.depau.loak.generated.resources.label_recap_heatmap_more
import eu.depau.loak.generated.resources.label_recap_listened
import eu.depau.loak.generated.resources.label_recap_new_artists
import eu.depau.loak.generated.resources.label_recap_plays
import eu.depau.loak.generated.resources.label_recap_tracks
import eu.depau.loak.generated.resources.title_recap
import eu.depau.loak.generated.resources.title_recap_activity
import eu.depau.loak.generated.resources.title_recap_top_albums
import eu.depau.loak.generated.resources.title_recap_top_artists
import eu.depau.loak.generated.resources.title_recap_top_tracks
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.BarChart
import eu.depau.loak.icons.outlined.Share
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.common.ErrorBox
import eu.depau.loak.ui.components.layouts.ArtCarousel
import eu.depau.loak.ui.components.layouts.ArtCarouselItem
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.RootTopBar
import eu.depau.loak.ui.components.layouts.rootTopBarScrollBehavior
import eu.depau.loak.ui.components.sheets.SongActionsSheet
import eu.depau.loak.ui.screens.home.components.ShelfHeader
import eu.depau.loak.ui.theme.SmallCoverArtShape
import eu.depau.loak.ui.util.withoutTop
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.home.components.TAB
import eu.depau.loak.ui.screens.recap.viewmodels.RecapViewModel
import eu.depau.loak.ui.theme.defaultFont
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlin.math.log2
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration

/**
 * Recap: what was listened to over a period — plays, hours, artists, an activity grid and the
 * top artists, albums and tracks, each against the period before it.
 *
 * The whole screen names its sources once, in the eyebrow, rather than tagging every figure:
 * with a scrobbler on, the figures are as much that scrobbler's as this device's.
 */
@Composable
fun RecapScreen() {
	val viewModel = koinViewModel<RecapViewModel>()
	val state by viewModel.state.collectAsStateWithLifecycle()
	val period by viewModel.period.collectAsStateWithLifecycle()
	val shareManager = koinInject<ShareManager>()
	val scope = rememberCoroutineScope()
	val backStack = LocalNavStack.current
	val scrollBehavior = rootTopBarScrollBehavior()
	// a tab of its own, or a page pushed over the Library
	val nested = backStack.firstOrNull() != Screen.Recap
	val recap = state.data
	// hoisted: the share button's lambda is not a composable scope
	val shareText = recap?.shareText()

	Scaffold(
		modifier = if (nested) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		topBar = {
			val share: @Composable RowScope.() -> Unit = {
				// nothing to share until there is something on screen
				if (shareText != null) {
					IconButton(onClick = { scope.launch { shareManager.shareString(shareText) } }) {
						Icon(Icons.Outlined.Share, stringResource(Res.string.action_share))
					}
				}
			}
			val title: @Composable () -> Unit = { Text(stringResource(Res.string.title_recap)) }
			// in the tab bar it is a root of its own; opened from Library it is a page on top
			if (nested) NestedTopBar(title, actions = share)
			else RootTopBar(title, scrollBehavior, actions = share)
		},
		// like the other scrolling screens, the bar stays: it is how the flow leaves the page
		bottomBar = { RootBottomBar(scrolled = LocalBottomBarScrollManager.current.isTriggered) }
	) { innerPadding ->
		LazyColumn(
			// the top inset pads the feed, not its content: the period picker is its first
			// line, and with the inset spent on content it sat under the top bar
			modifier = Modifier.fillMaxWidth().padding(top = innerPadding.calculateTopPadding()),
			contentPadding = innerPadding.withoutTop() + PaddingValues(bottom = 24.dp)
		) {
			item { PeriodPicker(period, viewModel::select) }
			if (state is UiState.Error) {
				item { ErrorBox(state as UiState.Error, onRetry = { viewModel.load() }) }
			} else if (recap == null) {
				// loading: the period picker is the only thing that is already true
			} else {
				item { Hero(recap) }
				// the grid is always the same 27 weeks, so a period with nothing played still
				// shows what was played around it
				item { Activity(recap) }
				if (recap.totals.plays == 0) {
					item { EmptyNotice() }
				} else {
					if (recap.topArtists.isNotEmpty()) item { TopArtists(recap.topArtists) }
					if (recap.topAlbums.isNotEmpty()) item { TopAlbums(recap.topAlbums) }
					if (recap.topTracks.isNotEmpty()) item { TopTracks(recap.topTracks) }
				}
			}
		}
	}
}

/** Day / Week / Month / Year / All, the one control that changes everything below it. */
@Composable
private fun PeriodPicker(selected: RecapPeriod, onSelect: (RecapPeriod) -> Unit) {
	SingleChoiceSegmentedButtonRow(
		Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
	) {
		RecapPeriod.entries.forEachIndexed { index, period ->
			SegmentedButton(
				selected = period == selected,
				onClick = { onSelect(period) },
				shape = SegmentedButtonDefaults.itemShape(index, RecapPeriod.entries.size)
			) { Text(stringResource(period.displayName)) }
		}
	}
}

/** The sources, the number, the four figures and how they compare with the period before. */
@Composable
private fun Hero(recap: RecapData) {
	Column(
		Modifier.padding(horizontal = 16.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Text(
			stringResource(Res.string.info_recap_from, recap.sourceLabel()).uppercase(),
			style = MaterialTheme.typography.labelLarge.copy(fontFamily = defaultFont(grade = 100)),
			color = MaterialTheme.colorScheme.primary
		)
		if (recap.unreadable.isNotEmpty()) {
			// the eyebrow above names only what answered: say what is missing from the figures
			Text(
				stringResource(
					Res.string.info_recap_source_unreadable,
					recap.unreadable.joinToString { it.sourceName() }
				),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.error
			)
		}
		Row(verticalAlignment = Alignment.Bottom) {
			Text(
				recap.totals.plays.toString(),
				style = MaterialTheme.typography.displayMedium.copy(fontFamily = defaultFont(20)),
				fontWeight = FontWeight(600)
			)
			Text(
				" " + pluralStringResource(Res.plurals.label_recap_plays, recap.totals.plays, recap.totals.plays),
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(bottom = 8.dp)
			)
		}
		Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
			Stat(recap.totals.listened.toHoursMinutes(), stringResource(Res.string.label_recap_listened))
			Stat(
				recap.totals.tracks.toString(),
				pluralStringResource(Res.plurals.label_recap_tracks, recap.totals.tracks, recap.totals.tracks)
			)
			Stat(
				recap.totals.artists.toString(),
				pluralStringResource(Res.plurals.label_recap_artists, recap.totals.artists, recap.totals.artists)
			)
			recap.totals.newArtists?.let {
				Stat(
					it.toString(),
					pluralStringResource(Res.plurals.label_recap_new_artists, it, it)
				)
			}
		}
		recap.previous?.let { Comparison(recap.totals.plays, it.plays) }
	}
}

@Composable
private fun Stat(value: String, label: String) {
	Column {
		Text(
			value,
			style = MaterialTheme.typography.titleLarge.copy(fontFamily = defaultFont(10)),
			fontWeight = FontWeight(600)
		)
		Text(
			label,
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

/** The one-line "how does this compare" pill. Within a tenth either way, it says so. */
@Composable
private fun Comparison(plays: Int, previousPlays: Int) {
	if (previousPlays == 0) return
	val ratio = (plays - previousPlays).toDouble() / previousPlays
	val percent = (kotlin.math.abs(ratio) * 100).roundToInt()
	val up = MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
	val flat = MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
	val (colors, text) = when {
		percent < 10 -> flat to stringResource(Res.string.info_recap_plays_same)
		ratio > 0 -> up to stringResource(Res.string.info_recap_plays_more, percent)
		else -> flat to stringResource(Res.string.info_recap_plays_fewer, percent)
	}
	Box(
		Modifier.clip(CircleShape).background(colors.first)
			.padding(horizontal = 12.dp, vertical = 6.dp)
	) { Text(text, style = MaterialTheme.typography.labelMedium, color = colors.second) }
}

/** Plays per day, one square each, in week columns: the picked period stands out of the rest. */
@Composable
private fun Activity(recap: RecapData) {
	val primary = MaterialTheme.colorScheme.primary
	// a day with nothing played is the card, not a filled square: the grid stays a grid
	val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)
	// the app's block, not a floating diagram: a card like the Library's rows hold
	Surface(
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.surfaceContainer,
		modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
	) {
		Column(
			Modifier.padding(SegmentedListItemDefaults.ContentPadding),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(
				stringResource(Res.string.title_recap_activity),
				style = MaterialTheme.typography.titleMediumEmphasized,
				fontWeight = FontWeight(600)
			)
			// one weight per week, so the grid is as wide as the card is and the squares grow
			Row(
				Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(2.dp)
			) {
				recap.activity.toWeeks().forEach { week ->
					Column(
						Modifier.weight(1f),
						verticalArrangement = Arrangement.spacedBy(2.dp)
					) {
						week.forEach { day ->
							Box(
								Modifier.fillMaxWidth()
									.aspectRatio(1f)
									.clip(RoundedCornerShape(2.dp))
									.background(if (day == null) Color.Transparent else day.cellColor(primary, empty))
							)
						}
					}
				}
			}
			Row(
				Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					stringResource(Res.string.label_recap_heatmap_less),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				for (level in 1..4) {
					Box(
						Modifier.size(10.dp).clip(RoundedCornerShape(2.dp))
							.background(lerp(empty, primary, level / 4f))
					)
				}
				Text(
					stringResource(Res.string.label_recap_heatmap_more),
					style = MaterialTheme.typography.labelSmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}

/** The app's cover shelf: the same cards Starred's artists and albums are shown in. */
@Composable
private fun TopArtists(artists: List<RecapEntry>) {
	val backStack = LocalNavStack.current
	ArtCarousel(stringResource(Res.string.title_recap_top_artists), artists.toImmutableList()) { artist ->
		ArtCarouselItem(
			coverArtId = artist.coverArtId,
			title = artist.title,
			subtitle = pluralStringResource(Res.plurals.info_recap_plays_count, artist.plays, artist.plays),
			contentDescription = null,
			id = artist.id,
			onClick = dropUnlessResumed { backStack.add(Screen.ArtistDetail(artist.id)) }
		)
	}
}

@Composable
private fun TopAlbums(albums: List<RecapEntry>) {
	val backStack = LocalNavStack.current
	ArtCarousel(stringResource(Res.string.title_recap_top_albums), albums.toImmutableList()) { album ->
		ArtCarouselItem(
			coverArtId = album.coverArtId,
			title = album.title,
			subtitle = album.subtitle,
			contentDescription = null,
			id = album.id,
			onClick = dropUnlessResumed { backStack.add(Screen.CollectionDetail(album.id, TAB)) }
		)
	}
}

/** The ranked songs as the app's own rows, with the play count where a count belongs. */
@Composable
private fun TopTracks(tracks: List<RecapEntry>) {
	val player = koinInject<MediaPlayerViewModel>()
	// the songs, in the order the sheet and the queue want them: the same list a tap plays
	val queue = tracks.mapNotNull { it.song }
	var sheetSong by remember { mutableStateOf<DomainSong?>(null) }

	sheetSong?.let { song ->
		SongActionsSheet(song = song, open = true, onDismissRequest = { sheetSong = null })
	}

	ShelfHeader(stringResource(Res.string.title_recap_top_tracks))
	Column(
		Modifier.padding(horizontal = 16.dp),
		verticalArrangement = Arrangement.spacedBy(SegmentedListItemDefaults.SegmentedGap)
	) {
		tracks.forEachIndexed { index, track ->
			val song = track.song ?: return@forEachIndexed
			SegmentedListItem(
				onClick = { player.playNow(queue, queue.indexOf(song)) },
				onLongClick = { sheetSong = song },
				shapes = SegmentedListItemDefaults.segmentedShapes(index = index, count = tracks.size),
				leadingContent = {
					CoverArt(
						modifier = Modifier.size(50.dp),
						coverArtId = track.coverArtId,
						shape = SmallCoverArtShape
					)
				},
				content = { MarqueeText(track.title) },
				supportingContent = track.subtitle?.let { info -> { MarqueeText(info) } },
				trailingContent = {
					Text(
						pluralStringResource(
							Res.plurals.info_recap_plays_count,
							track.plays,
							track.plays
						),
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			)
		}
	}
}

@Composable
private fun EmptyNotice() {
	ContentUnavailable(
		icon = Icons.Outlined.BarChart,
		label = stringResource(Res.string.info_recap_empty),
		modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
	)
}

/** "this device", or "this device and Koito": the eyebrow names the sources that answered. */
@Composable
private fun RecapData.sourceLabel(): String {
	val answered = sourceId.split('+').filterNot { it in unreadable }
	val remote = answered.firstOrNull { it != "local" }
	return if (remote == null) stringResource(Res.string.info_recap_source_local)
	else stringResource(Res.string.info_recap_source_local_and, remote.sourceName())
}

/** "koito" → "Koito": the ids are the products' own names. */
private fun String.sourceName() = replaceFirstChar { it.uppercase() }

/** The whole recap as one shareable paragraph, in the labels the screen itself uses. */
@Composable
private fun RecapData.shareText(): String = buildString {
	appendLine(stringResource(Res.string.info_recap_share, stringResource(period.displayName)))
	appendLine(
		pluralStringResource(Res.plurals.info_recap_plays_count, totals.plays, totals.plays) +
			" · " + totals.listened.toHoursMinutes()
	)
	@Composable
	fun line(label: StringResource, entry: RecapEntry?) =
		entry?.let { appendLine(stringResource(label) + ": " + it.title) }

	line(Res.string.title_recap_top_artists, topArtists.firstOrNull())
	line(Res.string.title_recap_top_albums, topAlbums.firstOrNull())
	line(Res.string.title_recap_top_tracks, topTracks.firstOrNull())
}

/** "56h 52m", or "52m" under an hour. */
private fun Duration.toHoursMinutes(): String {
	val totalMinutes = inWholeMinutes
	val hours = totalMinutes / 60
	val minutes = totalMinutes % 60
	return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * The activity grid's squares, as week columns with Monday on top. The days before the first
 * square are null, so the first column lines up with the right weekday.
 */
private fun List<RecapDay>.toWeeks(): List<List<RecapDay?>> {
	if (isEmpty()) return emptyList()
	val cells: List<RecapDay?> = List(first().date.dayOfWeek.ordinal) { null } + this
	val weeks = cells.chunked(7).toMutableList()
	val last = weeks.last()
	if (last.size < 7) weeks[weeks.size - 1] = last + List(7 - last.size) { null }
	return weeks
}

/**
 * How dark a day's square is: nothing for a day with no plays, then one step per doubling of
 * the plays. A log ramp, so one enormous day does not wash every other day out. A day outside
 * the picked period keeps the ramp at [OUTSIDE] of its depth: the rest of the grid still reads
 * as activity, at half the contrast, rather than as one flat block.
 */
private fun RecapDay.cellColor(primary: Color, empty: Color): Color {
	if (plays <= 0) return empty
	val level = (1 + log2(plays.toDouble())).toInt().coerceIn(1, 4)
	return lerp(empty, primary, level / 4f * (if (inPeriod) 1f else OUTSIDE))
}

private const val OUTSIDE = 0.4f
