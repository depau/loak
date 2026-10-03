package eu.depau.loak.ui.screens.explore

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavKey
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isExpanded
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.repositories.SoundMapPoint
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Sparkle
import eu.depau.loak.icons.outlined.Album
import eu.depau.loak.icons.outlined.BarChart
import eu.depau.loak.icons.outlined.Calendar
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.icons.outlined.Flask
import eu.depau.loak.icons.outlined.Grid
import eu.depau.loak.icons.outlined.RecentlyAdded
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.icons.outlined.Shuffle
import eu.depau.loak.icons.outlined.SonicPath
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.icons.outlined.Trophy
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.layouts.ArtGridItem
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.RootTopBar
import eu.depau.loak.ui.components.layouts.rootTopBarScrollBehavior
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.genre.components.GenreListScreenCard
import eu.depau.loak.ui.navigation.PersistentViewModelStoreOwner
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val TAB = "explore"

private class Lens(val icon: ImageVector, val title: StringResource, val destination: NavKey)

private class Tool(
	val icon: ImageVector,
	val title: StringResource,
	val subtitle: StringResource,
	val container: Color,
	val content: Color,
	val screen: Screen
)

/**
 * Explore: ways into what's already in the library (Discover is for what isn't). The
 * AudioMuse-AI sections only show while it's connected.
 */
@Composable
fun ExploreScreen() {
	val viewModel = koinViewModel<ExploreViewModel>(viewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>())
	val state by viewModel.state.collectAsStateWithLifecycle()
	val info by koinInject<AudioMuseManager>().info.collectAsState()
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val backStack = LocalNavStack.current
	val scrollBehavior = rootTopBarScrollBehavior()
	val expanded = LocalPlatformContext.current.isExpanded()
	// 6 or 12 columns: 2 or 4 lenses a row, 3 or 6 tiles
	val columns = if (expanded) 12 else 6
	val full: LazyGridItemSpanScope.() -> GridItemSpan = { GridItemSpan(maxLineSpan) }
	val gridState = rememberLazyGridState()

	LaunchedEffect(info) { if (info != null && state.moods.isEmpty()) viewModel.refresh() }

	val lenses = listOf(
		Lens(Icons.Outlined.Album, Res.string.title_albums, Screen.AlbumList(nested = true)),
		Lens(Icons.Outlined.RecentlyAdded, Res.string.lens_recently_added, Screen.AlbumList(true, DomainAlbumListType.Newest)),
		Lens(Icons.Outlined.BarChart, Res.string.lens_most_played, Screen.AlbumList(true, DomainAlbumListType.Frequent)),
		Lens(Icons.Outlined.Trophy, Res.string.lens_highest_rated, Screen.AlbumList(true, DomainAlbumListType.Highest)),
		Lens(Icons.Outlined.Star, Res.string.title_starred, Screen.Starred(nested = true)),
		Lens(Icons.Outlined.Shuffle, Res.string.lens_random, Screen.AlbumList(true, DomainAlbumListType.Random)),
		Lens(Icons.Outlined.Calendar, Res.string.lens_by_year, Screen.AlbumList(true, DomainAlbumListType.Year)),
		Lens(Icons.Outlined.Grid, Res.string.title_genres, Screen.GenreList(nested = true))
	)
	val colors = MaterialTheme.colorScheme
	val tools = info?.let { i ->
		listOfNotNull(
			Tool(Icons.Outlined.Flask, Res.string.title_song_alchemy, Res.string.info_tool_alchemy, colors.tertiaryContainer, colors.onTertiaryContainer, Screen.Alchemy()),
			Tool(Icons.Outlined.Search, Res.string.title_describe_mix, Res.string.info_tool_describe, colors.primaryContainer, colors.onPrimaryContainer, Screen.DescribeMix())
				.takeIf { (i.soundSearch || i.lyricsSearch) && preferenceManager.audioMuseDescribe },
			Tool(Icons.Outlined.SonicPath, Res.string.title_song_path, Res.string.info_tool_path, colors.secondaryContainer, colors.onSecondaryContainer, Screen.SongPath),
			Tool(Icons.Filled.Sparkle, Res.string.action_ask_ai_playlist, Res.string.info_tool_ask, colors.surfaceContainerHighest, colors.onSurface, Screen.AskAI())
				.takeIf { i.canAsk && preferenceManager.audioMuseAskAi }
		)
	}.orEmpty()

	Scaffold(
		modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		topBar = {
			RootTopBar(
				title = { Text(stringResource(Res.string.title_explore)) },
				scrollBehavior = scrollBehavior,
				subtitle = { Text(stringResource(Res.string.subtitle_explore)) }
			)
		},
		bottomBar = { RootBottomBar(scrolled = LocalBottomBarScrollManager.current.isTriggered) }
	) { innerPadding ->
		LazyVerticalGrid(
			columns = GridCells.Fixed(columns),
			state = gridState,
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = 16.dp, end = 16.dp,
				top = innerPadding.calculateTopPadding(),
				bottom = innerPadding.calculateBottomPadding() + 24.dp
			),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			section(Res.string.title_browse)
			items(lenses, span = { GridItemSpan(3) }) { lens ->
				Surface(
					onClick = dropUnlessResumed { backStack.add(lens.destination) },
					shape = RoundedCornerShape(16.dp),
					color = colors.surfaceContainerHigh
				) {
					Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						Box(Modifier.size(32.dp).clip(CircleShape).background(colors.secondaryContainer), contentAlignment = Alignment.Center) {
							Icon(lens.icon, null, Modifier.size(18.dp), tint = colors.onSecondaryContainer)
						}
						Text(stringResource(lens.title), maxLines = 1)
					}
				}
			}

			if (state.genres.isNotEmpty()) {
				section(Res.string.title_genres)
				// 3 a row on phones, 3 wider ones on tablets: long names wrap less
				val genreSpan = if (expanded) 4 else 2
				items(state.genres, key = { "genre ${it.name}" }, span = { GridItemSpan(genreSpan) }) { genre ->
					GenreListScreenCard(Modifier.height(88.dp), genre)
				}
				item(key = "all genres", span = { GridItemSpan(genreSpan) }) {
					Surface(
						onClick = dropUnlessResumed { backStack.add(Screen.GenreList(nested = true)) },
						modifier = Modifier.height(88.dp),
						shape = MaterialTheme.shapes.medium,
						color = colors.surfaceContainerHigh
					) {
						Column(Modifier.padding(10.dp)) {
							Text(stringResource(Res.string.action_all_genres_tile), style = MaterialTheme.typography.titleSmall)
							Text(stringResource(Res.string.info_genres_count, state.genreCount), style = MaterialTheme.typography.bodySmall)
							Spacer(Modifier.weight(1f))
							Icon(Icons.Outlined.ChevronForward, null, Modifier.align(Alignment.End))
						}
					}
				}
			}

			if (info != null && state.moods.isNotEmpty()) {
				section(Res.string.title_moods, overline = Res.string.overline_audiomuse)
				items(state.moods, key = { "mood ${it.id}" }, span = { GridItemSpan(2) }) { mood ->
					MoodTile(mood.label, Modifier.height(72.dp)) { backStack.add(Screen.Mood(mood)) }
				}
			}

			if (tools.isNotEmpty()) {
				section(Res.string.title_make_something_new)
				items(tools, key = { "tool ${it.title.key}" }, span = { GridItemSpan(3) }) { tool ->
					Surface(
						onClick = dropUnlessResumed { backStack.add(tool.screen) },
						shape = RoundedCornerShape(20.dp),
						color = tool.container,
						contentColor = tool.content
					) {
						Column(Modifier.padding(14.dp).height(96.dp), verticalArrangement = Arrangement.SpaceBetween) {
							Icon(tool.icon, null, Modifier.size(24.dp))
							Column {
								Text(stringResource(tool.title), style = MaterialTheme.typography.titleSmall)
								Text(stringResource(tool.subtitle), style = MaterialTheme.typography.bodySmall, maxLines = 2)
							}
						}
					}
				}
			}

			if (info != null && state.soundMap.isNotEmpty()) {
				section(Res.string.title_sound_map, overline = Res.string.overline_audiomuse)
				item(key = "sound map", span = full) {
					SoundMapCard(state.soundMap) { backStack.add(Screen.SoundMap) }
				}
			}

			if (state.forgotten.isNotEmpty() || state.neverPlayed.isNotEmpty() || state.deepCuts.isNotEmpty()) {
				section(Res.string.title_off_beaten_path)
				albumShelf(Res.string.title_forgotten_favourites, Res.string.info_forgotten_favourites, state.forgotten)
				albumShelf(Res.string.title_never_played, Res.string.info_never_played, state.neverPlayed)
				if (state.deepCuts.isNotEmpty()) {
					item(key = "deep cuts header", span = full) {
						ShelfTitle(Res.string.title_deep_cuts, Res.string.info_deep_cuts) {
							TextButton(onClick = { player.playNow(state.deepCuts) }) { Text(stringResource(Res.string.action_play_all)) }
						}
					}
					itemsIndexed(state.deepCuts, key = { _, song -> "deep ${song.id}" }, span = { _, _ -> GridItemSpan(6) }) { i, song ->
						ListItem(
							modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { player.playNow(state.deepCuts, i) },
							leadingContent = { CoverArt(coverArtId = song.coverArtId, modifier = Modifier.size(48.dp)) },
							headlineContent = { Text(song.title, maxLines = 1) },
							supportingContent = { Text(listOfNotNull(song.artistName, song.albumTitle).joinToString(" · "), maxLines = 1) },
							colors = ListItemDefaults.colors(containerColor = Color.Transparent)
						)
					}
				}
			}
		}
	}
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.section(title: StringResource, overline: StringResource? = null) {
	item(key = "section ${title.key}", span = { GridItemSpan(maxLineSpan) }) {
		Column(Modifier.padding(top = 16.dp, bottom = 4.dp)) {
			overline?.let {
				Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
		}
	}
}

@Composable
private fun ShelfTitle(title: StringResource, subtitle: StringResource, action: (@Composable () -> Unit)? = null) {
	Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
			Text(stringResource(subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
		}
		action?.invoke()
	}
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.albumShelf(
	title: StringResource,
	subtitle: StringResource,
	albums: List<DomainAlbum>
) {
	if (albums.isEmpty()) return
	item(key = "shelf ${title.key}", span = { GridItemSpan(maxLineSpan) }) {
		val backStack = LocalNavStack.current
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			ShelfTitle(title, subtitle)
			// ponytail: no See all; these lists only exist here
			LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				items(albums, key = { it.id }) { album ->
					ArtGridItem(
						modifier = Modifier.width(150.dp),
						onClick = dropUnlessResumed { backStack.add(Screen.CollectionDetail(album.id, TAB)) },
						coverArtId = album.coverArtId,
						title = album.name.orEmpty(),
						subtitle = album.artistName,
						id = album.id,
						tab = TAB
					)
				}
			}
		}
	}
}

/** A colour of its own per mood, like the genre cards. */
@Composable
fun MoodTile(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
	val color = moodColor(label)
	Surface(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.medium, color = color, contentColor = Color.White) {
		Box(Modifier.padding(10.dp)) {
			Text(label, style = MaterialTheme.typography.titleSmall)
		}
	}
}

private val moodColors = mapOf(
	"aggressive" to Color(0xFFA63A3A), "danceable" to Color(0xFF7A4FB0), "happy" to Color(0xFFB07A1E),
	"party" to Color(0xFFB0426F), "relaxed" to Color(0xFF2F7F72), "sad" to Color(0xFF3E5A8C)
)

fun moodColor(label: String) = moodColors[label.lowercase()]
	?: Color.hsv((label.hashCode().mod(360)).toFloat(), 0.55f, 0.55f)

@Composable
private fun SoundMapCard(points: List<SoundMapPoint>, onClick: () -> Unit) {
	val styles = remember(points) { soundMapStyles(points) }
	Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
		Column {
			val sample = remember(points) { points.shuffled(kotlin.random.Random(0)).take(600) }
			Canvas(Modifier.fillMaxWidth().height(140.dp).padding(12.dp)) {
				val (minX, maxX) = points.minOf { it.x } to points.maxOf { it.x }
				val (minY, maxY) = points.minOf { it.y } to points.maxOf { it.y }
				sample.groupBy { styleOf(it.style, styles) }.forEach { (style, group) ->
					drawPoints(
						group.map { Offset((it.x - minX) / (maxX - minX) * size.width, (maxY - it.y) / (maxY - minY) * size.height) },
						PointMode.Points, style.color, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
					)
				}
			}
			Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Text(stringResource(Res.string.info_sound_map_card), style = MaterialTheme.typography.titleSmall)
					Text(
						stringResource(Res.string.info_sound_map_count, points.size.toString()),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Icon(Icons.Outlined.ChevronForward, null)
			}
			FlowRow(Modifier.padding(16.dp, 8.dp, 16.dp, 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
				styles.take(6).forEach { style ->
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
						Box(Modifier.size(8.dp).clip(CircleShape).background(style.color))
						Text(style.label, style = MaterialTheme.typography.labelSmall)
					}
				}
			}
		}
	}
}
