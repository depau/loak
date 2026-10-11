package eu.depau.loak.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.ImageLoader
import coil3.compose.AsyncImage
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.discover.DiscoverGenre
import eu.depau.loak.domain.discover.PreviewManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_search_discover
import eu.depau.loak.generated.resources.action_see_all
import eu.depau.loak.generated.resources.option_download_queue
import eu.depau.loak.generated.resources.subtitle_from_deezer
import eu.depau.loak.generated.resources.subtitle_from_listenbrainz
import eu.depau.loak.generated.resources.subtitle_made_for_you
import eu.depau.loak.generated.resources.title_made_for_you
import eu.depau.loak.generated.resources.title_because_you_listen
import eu.depau.loak.generated.resources.title_browse_genres
import eu.depau.loak.generated.resources.title_discover
import eu.depau.loak.generated.resources.title_moods_activities
import eu.depau.loak.generated.resources.title_new_releases
import eu.depau.loak.generated.resources.title_top_albums
import eu.depau.loak.generated.resources.title_top_artists
import eu.depau.loak.generated.resources.title_top_songs
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.RootTopBar
import eu.depau.loak.ui.components.layouts.rootTopBarScrollBehavior
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.discover.components.ChartCountryChip
import eu.depau.loak.ui.screens.discover.components.ChartCountryPickerSheet
import eu.depau.loak.ui.screens.discover.components.ChartSongItem
import eu.depau.loak.ui.screens.discover.components.DiscoverAlbumCard
import eu.depau.loak.ui.screens.discover.components.DiscoverArtistCard
import eu.depau.loak.ui.screens.discover.components.MadeForYouCard
import eu.depau.loak.ui.screens.discover.components.rememberLidarrAddFlow
import eu.depau.loak.ui.screens.discover.components.ChartSongPlaceholder
import eu.depau.loak.ui.screens.discover.components.DiscoverAlbumCardPlaceholder
import eu.depau.loak.ui.screens.discover.components.DiscoverArtistCardPlaceholder
import eu.depau.loak.ui.screens.discover.components.MadeForYouPlaceholder
import eu.depau.loak.ui.screens.discover.components.PlaylistCard
import eu.depau.loak.ui.screens.discover.components.PlaylistCardPlaceholder
import eu.depau.loak.ui.screens.discover.viewmodels.DiscoverHomeViewModel
import eu.depau.loak.ui.screens.discover.components.dotted
import eu.depau.loak.ui.util.shimmerLoading
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Discover: search, charts and recommendations for music you don't have. One scrolling feed of
 * shelves; a shelf whose source is off, empty or unreachable simply isn't there.
 */
@Composable
fun DiscoverScreen() {
	val backStack = LocalNavStack.current
	val scrollBehavior = rootTopBarScrollBehavior()
	// opened from Library rather than as a tab
	val nested = backStack.firstOrNull() != Screen.Discover
	val title = @Composable { Text(stringResource(Res.string.title_discover)) }
	val queue = @Composable {
		IconButton(onClick = dropUnlessResumed { backStack.add(Screen.LidarrQueue) }) {
			Icon(Icons.Outlined.Download, stringResource(Res.string.option_download_queue))
		}
	}
	val listenBrainzUser = koinInject<PreferenceManager>().listenBrainzUsername
	val lidarr = koinInject<LidarrManager>()
	// fresh Lidarr state for the status badges
	LaunchedEffect(Unit) { lidarr.refreshCatalog() }

	val viewModel = koinViewModel<DiscoverHomeViewModel>()
	val home by viewModel.home.collectAsState()
	val charts by viewModel.charts.collectAsState()
	val previewManager = koinInject<PreviewManager>()
	val active = previewManager.activePreview.collectAsState()
	// just the audible song's id: progress ticks don't recompose the list
	val previewing by remember { derivedStateOf { active.value?.takeIf { it.isPlaying }?.song?.id } }
	val addFlow = rememberLidarrAddFlow()
	var pickerOpen by rememberSaveable { mutableStateOf(false) }
	if (pickerOpen) ChartCountryPickerSheet(
		charts = charts,
		selected = viewModel.chartCountry,
		onSelect = viewModel::setChartCountry,
		onDismissRequest = { pickerOpen = false }
	)
	val open = { type: DiscoverListType -> backStack.add(Screen.DiscoverList(type)) }

	Scaffold(
		// only the root bar collapses: without it laid out, the behavior would swallow every scroll
		modifier = if (nested) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		topBar = {
			// its own search below, not the library's
			if (nested) NestedTopBar(title, actions = { queue() })
			else RootTopBar(title = title, scrollBehavior = scrollBehavior, actions = { queue() }, searchCategory = null)
		},
		bottomBar = { if (!nested) RootBottomBar(scrolled = LocalBottomBarScrollManager.current.isTriggered) }
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				top = innerPadding.calculateTopPadding(),
				bottom = innerPadding.calculateBottomPadding() + 24.dp
			)
		) {
			item(key = "search") {
				SearchPill(
					modifier = Modifier.padding(horizontal = 16.dp),
					onClick = dropUnlessResumed { backStack.add(Screen.DiscoverSearch) }
				)
			}
			if (home.loadingMadeForYou || home.madeForYou.isNotEmpty()) item(key = "made-for-you") {
				ShelfHeader(
					stringResource(Res.string.title_made_for_you),
					subtitle = stringResource(Res.string.subtitle_made_for_you, listenBrainzUser)
				)
				Rail {
					if (home.loadingMadeForYou) items(5) { MadeForYouPlaceholder() }
					else items(home.madeForYou, key = { it.mbid }) { playlist ->
						MadeForYouCard(playlist, dropUnlessResumed {
							open(DiscoverListType.MadeForYou(playlist.mbid, playlist.title))
						})
					}
				}
			}
			if (home.loadingNewReleases || home.newReleases.isNotEmpty()) item(key = "new-releases") {
				ShelfHeader(
					stringResource(Res.string.title_new_releases),
					subtitle = stringResource(Res.string.subtitle_from_listenbrainz)
				) {
					SeeAll(dropUnlessResumed { open(DiscoverListType.NewReleases) })
				}
				Rail {
					if (home.loadingNewReleases) items(6) { DiscoverAlbumCardPlaceholder() }
					else items(home.newReleases, key = { it.id }) {
						DiscoverAlbumCard(it, addFlow, dotted(it.artist, it.year?.toString()))
					}
				}
			}
			if (home.loadingTopAlbums || home.topAlbums.isNotEmpty()) item(key = "top-albums") {
				ShelfHeader(
					stringResource(Res.string.title_top_albums),
					subtitle = stringResource(Res.string.subtitle_from_deezer)
				) {
					SeeAll(dropUnlessResumed { open(DiscoverListType.TopAlbums) })
				}
				Rail {
					if (home.loadingTopAlbums) items(6) { DiscoverAlbumCardPlaceholder() }
					else items(home.topAlbums, key = { it.id }) {
						DiscoverAlbumCard(it, addFlow, dotted(it.artist, it.year?.toString()))
					}
				}
			}
			if (home.loadingMoods || home.moods.isNotEmpty()) item(key = "moods") {
				ShelfHeader(
					stringResource(Res.string.title_moods_activities),
					subtitle = stringResource(Res.string.subtitle_from_deezer)
				) {
					SeeAll(dropUnlessResumed { open(DiscoverListType.Moods) })
				}
				Rail {
					if (home.loadingMoods) items(3) { PlaylistCardPlaceholder() }
					else items(home.moods, key = { it.id }) {
						PlaylistCard(it) { open(DiscoverListType.Playlist(it.id, it.title)) }
					}
				}
			}
			if (home.loadingChart || (home.chart != null && home.topSongs.isNotEmpty())) {
				if (home.loadingChart) {
					item(key = "top-songs") {
						ShelfHeader(
							stringResource(Res.string.title_top_songs),
							subtitle = stringResource(Res.string.subtitle_from_deezer)
						) {
							Box(Modifier.width(96.dp).height(32.dp).clip(CircleShape).shimmerLoading())
							Box(Modifier.width(52.dp).height(20.dp).clip(CircleShape).shimmerLoading())
						}
					}
					items(5, key = { "chart-placeholder-$it" }) { ChartSongPlaceholder() }
				} else {
					val chart = home.chart!!
					item(key = "top-songs") {
						ShelfHeader(
							stringResource(Res.string.title_top_songs),
							subtitle = stringResource(Res.string.subtitle_from_deezer)
						) {
							ChartCountryChip(chart.countryName) { pickerOpen = true }
							SeeAll(dropUnlessResumed { open(DiscoverListType.TopSongs) })
						}
					}
					itemsIndexed(home.topSongs, key = { _, song -> "chart-${song.id}" }) { index, song ->
						ChartSongItem(index + 1, song, song.id == previewing, addFlow) {
							previewManager.toggle(song)
						}
					}
				}
			}
			if (home.loadingTopArtists || home.topArtists.isNotEmpty()) item(key = "top-artists") {
				ShelfHeader(
					stringResource(Res.string.title_top_artists),
					subtitle = stringResource(Res.string.subtitle_from_deezer)
				) {
					SeeAll(dropUnlessResumed { open(DiscoverListType.TopArtists) })
				}
				Rail {
					if (home.loadingTopArtists) items(6) { DiscoverArtistCardPlaceholder() }
					else items(home.topArtists, key = { it.id }) { DiscoverArtistCard(it, addFlow) }
				}
			}
			if (home.loadingSimilar || home.seed != null) item(key = "similar") {
				if (home.loadingSimilar) ShelfHeaderPlaceholder()
				else {
					val seed = home.seed!!
					ShelfHeader(
						stringResource(Res.string.title_because_you_listen, seed.name),
						subtitle = stringResource(Res.string.subtitle_from_listenbrainz)
					) {
						SeeAll(dropUnlessResumed {
							open(DiscoverListType.SimilarArtists(seed.name, seed.mbid.orEmpty()))
						})
					}
				}
				Rail {
					if (home.loadingSimilar) items(8) { DiscoverArtistCardPlaceholder() }
					else items(home.similar, key = { it.id }) { DiscoverArtistCard(it, addFlow) }
				}
			}
			if (home.loadingGenres || home.genres.isNotEmpty()) item(key = "genres") {
				ShelfHeader(
					stringResource(Res.string.title_browse_genres),
					subtitle = stringResource(Res.string.subtitle_from_deezer)
				) {
					SeeAll(dropUnlessResumed { open(DiscoverListType.Genres) })
				}
				Column(
					Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					if (home.loadingGenres) repeat(6) {
						Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
							repeat(2) {
								Box(
									Modifier.weight(1f).height(64.dp)
										.clip(MaterialTheme.shapes.medium).shimmerLoading()
								)
							}
						}
					} else home.genres.chunked(2).forEach { pair ->
						Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
							pair.forEach { genre ->
								GenreTile(genre, Modifier.weight(1f), dropUnlessResumed {
									open(DiscoverListType.GenreRadios(genre.id, genre.name))
								})
							}
							// an odd last tile keeps its column instead of stretching across
							if (pair.size == 1) Spacer(Modifier.weight(1f))
						}
					}
				}
			}
		}
	}
}

/** A shelf's header: its title and subtitle, with its chip and "See all" on the right. */
@Composable
private fun ShelfHeader(
	title: String,
	subtitle: String? = null,
	trailing: @Composable RowScope.() -> Unit = {}
) {
	Row(
		modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 18.dp, bottom = 4.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Column(Modifier.weight(1f)) {
			Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
			if (subtitle != null) Text(
				subtitle,
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis
			)
		}
		trailing()
	}
}

@Composable
private fun ShelfHeaderPlaceholder() {
	Row(
		Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 18.dp, bottom = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
			Box(Modifier.width(180.dp).height(22.dp).clip(CircleShape).shimmerLoading())
			Box(Modifier.width(120.dp).height(14.dp).clip(CircleShape).shimmerLoading())
		}
		Box(Modifier.width(52.dp).height(20.dp).clip(CircleShape).shimmerLoading())
	}
}

@Composable
private fun SeeAll(onClick: () -> Unit) = TextButton(onClick = onClick) {
	Text(stringResource(Res.string.action_see_all))
}

/** A shelf's horizontal rail, bleeding to the screen edges. */
@Composable
private fun Rail(content: LazyListScope.() -> Unit) = LazyRow(
	contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
	horizontalArrangement = Arrangement.spacedBy(12.dp),
	content = content
)

/** One "Browse genres" tile: Deezer's picture for the genre, darkened under its name. */
@Composable
fun GenreTile(genre: DiscoverGenre, modifier: Modifier, onClick: () -> Unit) {
	Surface(
		onClick = onClick,
		modifier = modifier.height(64.dp),
		shape = MaterialTheme.shapes.medium,
		color = MaterialTheme.colorScheme.surfaceContainerHigh
	) {
		Box {
			AsyncImage(
				model = genre.pictureUrl,
				imageLoader = koinInject<ImageLoader>(),
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize()
			)
			Box(
				Modifier
					.fillMaxSize()
					.background(Color.Black.copy(alpha = 0.45f))
					.padding(horizontal = 14.dp),
				contentAlignment = Alignment.CenterStart
			) {
				Text(
					genre.name,
					color = Color.White,
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.SemiBold,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
	}
}

@Composable
private fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
	Surface(
		onClick = onClick,
		modifier = modifier.fillMaxWidth().height(56.dp),
		shape = CircleShape,
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		contentColor = MaterialTheme.colorScheme.onSurfaceVariant
	) {
		Row(
			modifier = Modifier.padding(horizontal = 20.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Icon(Icons.Outlined.Search, contentDescription = null)
			Text(stringResource(Res.string.action_search_discover), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
		}
	}
}
