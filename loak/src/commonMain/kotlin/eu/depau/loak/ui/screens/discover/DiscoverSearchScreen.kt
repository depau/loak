package eu.depau.loak.ui.screens.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSearchResults
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.PreviewManager
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_to_lidarr
import eu.depau.loak.generated.resources.action_got_it
import eu.depau.loak.generated.resources.action_preview
import eu.depau.loak.generated.resources.action_search_discover
import eu.depau.loak.generated.resources.action_pause_preview
import eu.depau.loak.generated.resources.info_deezer_search_notice
import eu.depau.loak.generated.resources.info_no_search_results
import eu.depau.loak.generated.resources.label_kind_artist
import eu.depau.loak.generated.resources.label_kind_song
import eu.depau.loak.generated.resources.title_albums
import eu.depau.loak.generated.resources.title_artists
import eu.depau.loak.generated.resources.title_deezer_search_notice
import eu.depau.loak.generated.resources.title_recent
import eu.depau.loak.generated.resources.title_settings
import eu.depau.loak.generated.resources.title_songs
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.brand.Deezer
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.ArrowOutward
import eu.depau.loak.icons.filled.Pause
import eu.depau.loak.icons.outlined.History
import eu.depau.loak.icons.outlined.NoSearchResults
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.components.common.ErrorBox
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.discover.viewmodels.DiscoverSearchViewModel
import eu.depau.loak.ui.screens.discover.components.DiscoverArt
import eu.depau.loak.ui.screens.discover.components.AlbumGestures
import eu.depau.loak.ui.screens.discover.components.AlbumItem
import eu.depau.loak.ui.screens.discover.components.ArtistGestures
import eu.depau.loak.ui.screens.discover.components.ArtistItem
import eu.depau.loak.ui.screens.discover.components.LidarrAddFlow
import eu.depau.loak.ui.screens.discover.components.SectionTitle
import eu.depau.loak.ui.screens.discover.components.SongGestures
import eu.depau.loak.ui.screens.discover.components.SongItem
import eu.depau.loak.ui.screens.discover.components.WithBadge
import eu.depau.loak.ui.screens.discover.components.albumKind
import eu.depau.loak.ui.screens.discover.components.dotted
import eu.depau.loak.ui.screens.discover.components.rememberDiscoverStatus
import eu.depau.loak.ui.screens.discover.components.rememberLidarrAddFlow
import eu.depau.loak.ui.screens.search.SearchCategory
import eu.depau.loak.ui.screens.search.components.SearchScreenChips
import eu.depau.loak.ui.screens.search.components.SearchScreenTopBar
import eu.depau.loak.ui.util.windowControlsInsets
import eu.depau.loak.ui.util.windowDragArea
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private val categories = listOf(
	SearchCategory.ALL, SearchCategory.SONGS, SearchCategory.ALBUMS, SearchCategory.ARTISTS
)

/** Discover's own search, on Deezer: music outside the library. Blank query: recent searches. */
@Composable
fun DiscoverSearchScreen() {
	val viewModel = koinViewModel<DiscoverSearchViewModel>()
	val state by viewModel.results.collectAsState()
	val category by viewModel.category.collectAsState()
	val lidarr = koinInject<LidarrManager>()
	// fresh Lidarr state for the status badges
	LaunchedEffect(Unit) { lidarr.refreshCatalog() }

	Scaffold(
		topBar = {
			Column(
				modifier = Modifier
					.windowDragArea()
					.background(MaterialTheme.colorScheme.surface)
					.padding(
						TopAppBarDefaults.windowInsets.add(windowControlsInsets()).asPaddingValues()
					)
			) {
				SearchScreenTopBar(
					query = viewModel.query,
					nested = true,
					onSearch = { viewModel.submit() },
					placeholder = Res.string.action_search_discover
				)
				if (state != null) SearchScreenChips(
					selectedCategory = category,
					onCategorySelect = { viewModel.category.value = it },
					categories = categories
				)
				Box(Modifier.fillMaxWidth().height(4.dp)) {
					if (state is UiState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
				}
			}
		},
		bottomBar = { RootBottomBar(scrolled = LocalBottomBarScrollManager.current.isTriggered) }
	) { padding ->
		val s = state
		val results = s?.data
		when {
			s == null -> RecentSearches(viewModel, padding)
			s is UiState.Error -> ErrorBox(s, padding = padding)
			results == null -> {}
			results.songs.isEmpty() && results.albums.isEmpty() && results.artists.isEmpty() ->
				ContentUnavailable(
					icon = Icons.Outlined.NoSearchResults,
					label = stringResource(Res.string.info_no_search_results)
				)
			else -> Results(results, category, padding)
		}
	}
}

@Composable
private fun RecentSearches(viewModel: DiscoverSearchViewModel, padding: PaddingValues) {
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current
	LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
		val recent = viewModel.recentSearches
		if (recent.isNotEmpty()) item { SectionTitle(Res.string.title_recent) }
		items(recent) { query ->
			ListItem(
				modifier = Modifier.clickable { viewModel.submit(query) },
				leadingContent = {
					Icon(Icons.Outlined.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
				},
				headlineContent = { Text(query, maxLines = 1, overflow = TextOverflow.Ellipsis) },
				trailingContent = {
					Icon(Icons.Outlined.ArrowOutward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
				}
			)
		}
		if (!preferenceManager.deezerSearchNoticeDismissed) item {
			DeezerNotice(
				onDismiss = { preferenceManager.deezerSearchNoticeDismissed = true },
				onSettings = dropUnlessResumed {
					// the Discover group lives in Server settings; Root first, like AppActions.about
					backStack.add(Screen.Settings.Root)
					backStack.add(Screen.Settings.Server)
				}
			)
		}
	}
}

@Composable
private fun DeezerNotice(onDismiss: () -> Unit, onSettings: () -> Unit) {
	Surface(
		modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp),
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.surfaceContainer
	) {
		Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
			Box(
				modifier = Modifier
					.size(40.dp)
					.background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
				contentAlignment = Alignment.Center
			) {
				Icon(Icons.Brand.Deezer, null, Modifier.size(22.dp))
			}
			Column(Modifier.weight(1f)) {
				Text(stringResource(Res.string.title_deezer_search_notice), style = MaterialTheme.typography.bodyLarge)
				Text(
					stringResource(Res.string.info_deezer_search_notice),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				Row(Modifier.padding(top = 8.dp).offset(x = (-12).dp)) {
					TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_got_it)) }
					TextButton(onClick = onSettings) { Text(stringResource(Res.string.title_settings)) }
				}
			}
		}
	}
}

@Composable
private fun Results(results: DiscoverSearchResults, category: SearchCategory, padding: PaddingValues) {
	val all = category == SearchCategory.ALL
	val limit = if (all) 5 else Int.MAX_VALUE
	val top: Any? = if (all) results.songs.firstOrNull() ?: results.albums.firstOrNull() ?: results.artists.firstOrNull() else null
	val songs = if (all || category == SearchCategory.SONGS) results.songs.filter { it != top }.take(limit) else emptyList()
	val artists = if (all || category == SearchCategory.ARTISTS) results.artists.filter { it != top }.take(limit) else emptyList()
	val albums = if (all || category == SearchCategory.ALBUMS) results.albums.filter { it != top }.take(limit) else emptyList()

	val previewManager = koinInject<PreviewManager>()
	val active = previewManager.activePreview.collectAsState()
	// just the audible song's id: progress ticks don't recompose the list
	val previewing by remember { derivedStateOf { active.value?.takeIf { it.isPlaying }?.song?.id } }
	val addFlow = rememberLidarrAddFlow()

	LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
		if (top != null) item(key = "top") { TopResult(top, previewing, previewManager::toggle, addFlow) }
		section(Res.string.title_songs, songs) {
			SongItem(it, it.id == previewing, addFlow) { previewManager.toggle(it) }
		}
		section(Res.string.title_artists, artists) { ArtistItem(it, addFlow) }
		section(Res.string.title_albums, albums) { AlbumItem(it, addFlow) }
	}
}

private fun <T> LazyListScope.section(
	title: StringResource,
	items: List<T>,
	row: @Composable (T) -> Unit
) {
	if (items.isEmpty()) return
	item(key = title.key) { SectionTitle(title) }
	items(items) { row(it) }
}

@Composable
private fun TopResult(top: Any, previewing: String?, onPreview: (DiscoverSong) -> Unit, addFlow: LidarrAddFlow) {
	val (art, title, subtitle) = when (top) {
		is DiscoverSong -> Triple(top.coverUrl, top.title, dotted(stringResource(Res.string.label_kind_song), top.artist))
		is DiscoverAlbum -> Triple(top.coverUrl, top.title, dotted(albumKind(top), top.artist, top.year?.toString()))
		is DiscoverArtist -> Triple(top.pictureUrl, top.name, stringResource(Res.string.label_kind_artist))
		else -> return
	}
	val status = rememberDiscoverStatus(top)
	val card = @Composable { gestures: Modifier ->
		Surface(
			modifier = Modifier
				.padding(start = 16.dp, end = 16.dp, top = 16.dp)
				.clip(MaterialTheme.shapes.large)
				.then(gestures),
			shape = MaterialTheme.shapes.large,
			color = MaterialTheme.colorScheme.surfaceContainer
		) {
			Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
					DiscoverArt(art, 88.dp, if (top is DiscoverArtist) CircleShape else MaterialTheme.shapes.small)
					Column(Modifier.weight(1f)) {
						Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
						WithBadge(status) {
							Text(
								subtitle,
								modifier = Modifier.weight(1f, fill = false),
								style = MaterialTheme.typography.bodyMedium,
								color = MaterialTheme.colorScheme.onSurfaceVariant
							)
						}
						(top as? DiscoverSong)?.album?.let {
							Text(
								it,
								style = MaterialTheme.typography.bodySmall,
								color = MaterialTheme.colorScheme.onSurfaceVariant,
								maxLines = 1,
								overflow = TextOverflow.Ellipsis
							)
						}
					}
				}
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					if (top is DiscoverSong) FilledTonalButton(onClick = { onPreview(top) }) {
						val playing = top.id == previewing
						Icon(
							if (playing) Icons.Filled.Pause else Icons.Filled.Play,
							null,
							Modifier.size(ButtonDefaults.IconSize)
						)
						Text(
							stringResource(if (playing) Res.string.action_pause_preview else Res.string.action_preview),
							Modifier.padding(start = ButtonDefaults.IconSpacing)
						)
					}
					Button(onClick = {
						when (top) {
							is DiscoverSong -> addFlow.addSong(top)
							is DiscoverAlbum -> addFlow.addAlbum(top)
							is DiscoverArtist -> addFlow.addArtist(top)
						}
					}) {
						Icon(Icons.Outlined.Add, null, Modifier.size(ButtonDefaults.IconSize))
						Text(stringResource(Res.string.action_add_to_lidarr), Modifier.padding(start = ButtonDefaults.IconSpacing))
					}
				}
			}
		}
	}
	when (top) {
		is DiscoverSong -> SongGestures(top, status, onPreview, addFlow) { gestures, _ -> card(gestures) }
		is DiscoverAlbum -> AlbumGestures(top, addFlow) { gestures, _ -> card(gestures) }
		is DiscoverArtist -> ArtistGestures(top, addFlow) { gestures, _ -> card(gestures) }
	}
}
