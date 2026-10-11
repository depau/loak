package eu.depau.loak.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.discover.PreviewManager
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_all_genres
import eu.depau.loak.generated.resources.action_preview_all
import eu.depau.loak.generated.resources.info_discover_list_unavailable
import eu.depau.loak.generated.resources.action_hide_songs_i_have
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.info_listenbrainz_playlist
import eu.depau.loak.generated.resources.info_top_songs_chart
import eu.depau.loak.generated.resources.title_because_you_listen
import eu.depau.loak.generated.resources.title_moods_activities
import eu.depau.loak.generated.resources.title_new_releases
import eu.depau.loak.generated.resources.title_radio_stations
import eu.depau.loak.generated.resources.title_top_albums
import eu.depau.loak.generated.resources.title_top_artists
import eu.depau.loak.generated.resources.title_top_songs
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.Album
import eu.depau.loak.icons.outlined.Artist
import eu.depau.loak.icons.outlined.BarChart
import eu.depau.loak.icons.outlined.Genre
import eu.depau.loak.icons.outlined.Radio
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.icons.outlined.PlaylistPlay
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.screens.discover.components.AlbumItem
import eu.depau.loak.ui.screens.discover.components.ArtistItem
import eu.depau.loak.ui.screens.discover.components.ChartCountryChip
import eu.depau.loak.ui.screens.discover.components.ChartCountryPickerSheet
import eu.depau.loak.ui.screens.discover.components.ChartSongItem
import eu.depau.loak.ui.screens.discover.components.DiscoverPageState
import eu.depau.loak.ui.screens.discover.components.DiscoverSongPlaceholder
import eu.depau.loak.ui.screens.discover.components.PlaylistRow
import eu.depau.loak.ui.screens.discover.components.RadioTile
import eu.depau.loak.ui.screens.discover.components.RadiosRail
import eu.depau.loak.ui.screens.discover.components.SongItem
import eu.depau.loak.ui.screens.discover.components.hasPreview
import eu.depau.loak.ui.screens.discover.components.rememberLidarrAddFlow
import eu.depau.loak.ui.screens.discover.components.playlistGradient
import eu.depau.loak.ui.screens.discover.viewmodels.DiscoverListViewModel
import eu.depau.loak.ui.navigation.Screen
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.jetbrains.compose.resources.pluralStringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Which Discover shelf a [Screen.DiscoverList] shows in full. */
@Immutable
@Serializable
sealed interface DiscoverListType {
	/** The whole chart of the picked country: 100 songs. */
	@Immutable
	@Serializable
	data object TopSongs : DiscoverListType

	@Immutable
	@Serializable
	data object NewReleases : DiscoverListType

	/** The global Deezer chart's top albums. */
	@Immutable
	@Serializable
	data object TopAlbums : DiscoverListType

	/** The global Deezer chart's top artists. */
	@Immutable
	@Serializable
	data object TopArtists : DiscoverListType

	/** Deezer's "Moods & activities" editorial playlists. */
	@Immutable
	@Serializable
	data object Moods : DiscoverListType

	@Immutable
	@Serializable
	data class SimilarArtists(val artistName: String, val artistMbid: String) : DiscoverListType

	/** All the genres, for the "Browse genres" See all. */
	@Immutable
	@Serializable
	data object Genres : DiscoverListType

	/** One genre's radios and chart songs. */
	@Immutable
	@Serializable
	data class GenreRadios(val genreId: Long, val genreName: String) : DiscoverListType

	/** All the radios of one genre. */
	@Immutable
	@Serializable
	data class Radios(val genreId: Long, val genreName: String) : DiscoverListType

	/** The tracks of one Deezer radio. */
	@Immutable
	@Serializable
	data class Radio(val radioId: Long, val title: String) : DiscoverListType

	/** The tracks of one Deezer editorial ("Moods & activities") playlist. */
	@Immutable
	@Serializable
	data class Playlist(val playlistId: Long, val title: String) : DiscoverListType

	/** One ListenBrainz "made for you" playlist, by its MBID. */
	@Immutable
	@Serializable
	data class MadeForYou(val playlistMbid: String, val title: String) : DiscoverListType
}

/** A Discover shelf in full: the chart, fresh releases, similar artists or a genre's songs. */
@Composable
fun DiscoverListScreen(key: Screen.DiscoverList) {
	val viewModel = koinViewModel<DiscoverListViewModel>(
		key = key.toString(),
		parameters = { parametersOf(key.type) }
	)
	val page by viewModel.page.collectAsState()
	val charts by viewModel.charts.collectAsState()
	val lidarr = koinInject<LidarrManager>()
	// fresh Lidarr state for the status badges
	LaunchedEffect(Unit) { lidarr.refreshCatalog() }
	val previewManager = koinInject<PreviewManager>()
	val active = previewManager.activePreview.collectAsState()
	// just the audible song's id: progress ticks don't recompose the list
	val previewing by remember { derivedStateOf { active.value?.takeIf { it.isPlaying }?.song?.id } }
	val addFlow = rememberLidarrAddFlow()
	var pickerOpen by rememberSaveable { mutableStateOf(false) }
	var hideOwned by rememberSaveable { mutableStateOf(false) }
	if (pickerOpen) ChartCountryPickerSheet(
		charts = charts,
		selected = viewModel.chartCountry,
		onSelect = viewModel::setChartCountry,
		onDismissRequest = { pickerOpen = false }
	)

	val type = key.type
	val backStack = LocalNavStack.current
	val open = { next: DiscoverListType -> backStack.add(Screen.DiscoverList(next)) }
	Scaffold(
		topBar = { NestedTopBar(title = { Text(listTitle(type), maxLines = 1) }) },
		bottomBar = { RootBottomBar(scrolled = LocalBottomBarScrollManager.current.isTriggered) }
	) { padding ->
		// "Hide songs I have" only ever filters a made-for-you playlist; elsewhere it stays off
		val songs = if (hideOwned) page.songs.filterNot { it.id in page.ownedIds } else page.songs
		val empty = page.songs.isEmpty() && page.albums.isEmpty() && page.artists.isEmpty() &&
			page.genres.isEmpty() && page.radios.isEmpty() && page.playlists.isEmpty()
		if (empty) {
			DiscoverPageState(page.loading, Res.string.info_discover_list_unavailable, listIcon(type), padding)
			return@Scaffold
		}
		LazyColumn(
			Modifier.fillMaxSize(),
			contentPadding = padding,
			verticalArrangement = Arrangement.spacedBy(4.dp)
		) {
			if (type == DiscoverListType.TopSongs) item(key = "chart-header") {
				Row(
					Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					page.chart?.let { ChartCountryChip(it.countryName) { pickerOpen = true } }
					OutlinedButton(
						onClick = { previewManager.playAll(page.songs) },
						enabled = page.songs.any { it.hasPreview }
					) {
						Icon(Icons.Filled.Play, null, Modifier.size(ButtonDefaults.IconSize))
						Text(stringResource(Res.string.action_preview_all), Modifier.padding(start = ButtonDefaults.IconSpacing))
					}
				}
				page.chart?.let {
					Text(
						stringResource(Res.string.info_top_songs_chart, it.countryName),
						Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
			if (type is DiscoverListType.MadeForYou) item(key = "playlist-header") {
				MadeForYouHeader(
					type,
					songs = page.songs.size,
					previewable = songs.any { it.hasPreview },
					hideOwned = hideOwned,
					onHideOwned = { hideOwned = it },
					onPreviewAll = { previewManager.playAll(songs) }
				)
			}
			// a genre page's radios rail: its sub-genres, each of which opens a radio's tracks
			if (type is DiscoverListType.GenreRadios && page.radios.isNotEmpty()) item(key = "genre-radios") {
				RadiosRail(
					stringResource(Res.string.title_radio_stations),
					page.radios,
					onRadio = { radio -> open(DiscoverListType.Radio(radio.id, radio.title)) },
					onSeeAll = { open(DiscoverListType.Radios(type.genreId, type.genreName)) }
				)
			}
			// the chart is a ranking; a genre's, playlist's or radio's songs are just a list
			if (type == DiscoverListType.TopSongs) itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
				ChartSongItem(index + 1, song, song.id == previewing, addFlow) { previewManager.toggle(song) }
			} else items(songs, key = { it.id }) { song ->
				if (song.id in page.previewLoadingIds) {
					DiscoverSongPlaceholder()
				} else {
					SongItem(song, song.id == previewing, addFlow) { previewManager.toggle(song) }
				}
			}
			items(page.albums, key = { it.id }) { AlbumItem(it, addFlow) }
			items(page.artists, key = { it.id }) { ArtistItem(it, addFlow) }
			if (type is DiscoverListType.Radios) items(page.radios, key = { it.id }) {
				RadioTile(it, Modifier.fillMaxWidth()) { open(DiscoverListType.Radio(it.id, it.title)) }
			}
			if (type is DiscoverListType.Moods) items(page.playlists, key = { it.id }) {
				PlaylistRow(it) { open(DiscoverListType.Playlist(it.id, it.title)) }
			}
			if (type is DiscoverListType.Genres) items(page.genres, key = { it.id }) {
				GenreTile(it, Modifier.fillMaxWidth()) { open(DiscoverListType.GenreRadios(it.id, it.name)) }
			}
		}
	}
}

@Composable
private fun listTitle(type: DiscoverListType) = when (type) {
	DiscoverListType.TopSongs -> stringResource(Res.string.title_top_songs)
	DiscoverListType.NewReleases -> stringResource(Res.string.title_new_releases)
	DiscoverListType.TopAlbums -> stringResource(Res.string.title_top_albums)
	DiscoverListType.TopArtists -> stringResource(Res.string.title_top_artists)
	DiscoverListType.Moods -> stringResource(Res.string.title_moods_activities)
	DiscoverListType.Genres -> stringResource(Res.string.action_all_genres)
	is DiscoverListType.SimilarArtists -> stringResource(Res.string.title_because_you_listen, type.artistName)
	is DiscoverListType.GenreRadios -> type.genreName
	is DiscoverListType.Radios -> type.genreName
	is DiscoverListType.Radio -> type.title
	is DiscoverListType.Playlist -> type.title
	is DiscoverListType.MadeForYou -> type.title
}

private fun listIcon(type: DiscoverListType) = when (type) {
	DiscoverListType.TopSongs -> Icons.Outlined.BarChart
	DiscoverListType.NewReleases -> Icons.Outlined.Album
	DiscoverListType.TopAlbums -> Icons.Outlined.Album
	DiscoverListType.TopArtists -> Icons.Outlined.Artist
	DiscoverListType.Moods -> Icons.Outlined.PlaylistPlay
	DiscoverListType.Genres -> Icons.Outlined.Genre
	is DiscoverListType.SimilarArtists -> Icons.Outlined.Artist
	is DiscoverListType.GenreRadios -> Icons.Outlined.Genre
	is DiscoverListType.Radios -> Icons.Outlined.Genre
	is DiscoverListType.Radio -> Icons.Outlined.Radio
	is DiscoverListType.Playlist -> Icons.Outlined.PlaylistPlay
	is DiscoverListType.MadeForYou -> Icons.Outlined.PlaylistPlay
}

/**
 * A "made for you" playlist's header: its stand-in cover, who ListenBrainz built it for, and
 * the two list-wide actions — preview the lot, or drop the songs the library already has.
 */
@Composable
private fun MadeForYouHeader(
	type: DiscoverListType.MadeForYou,
	songs: Int,
	previewable: Boolean,
	hideOwned: Boolean,
	onHideOwned: (Boolean) -> Unit,
	onPreviewAll: () -> Unit
) {
	val username = koinInject<PreferenceManager>().listenBrainzUsername
	Row(
		Modifier.padding(horizontal = 16.dp),
		horizontalArrangement = Arrangement.spacedBy(16.dp),
		verticalAlignment = Alignment.Bottom
	) {
		Box(
			Modifier.size(132.dp)
				.clip(MaterialTheme.shapes.large)
				.background(playlistGradient(type.playlistMbid))
		)
		Column {
			Text(
				type.title,
				style = MaterialTheme.typography.headlineSmall,
				maxLines = 2,
				overflow = TextOverflow.Ellipsis
			)
			Text(
				stringResource(Res.string.info_listenbrainz_playlist, username),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
			Text(
				pluralStringResource(Res.plurals.count_songs, songs, songs),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
	Row(
		Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		Button(onClick = onPreviewAll, enabled = previewable) {
			Icon(Icons.Filled.Play, null, Modifier.size(ButtonDefaults.IconSize))
			Text(stringResource(Res.string.action_preview_all), Modifier.padding(start = ButtonDefaults.IconSpacing))
		}
		FilterChip(
			selected = hideOwned,
			onClick = { onHideOwned(!hideOwned) },
			label = { Text(stringResource(Res.string.action_hide_songs_i_have)) }
		)
	}
}
