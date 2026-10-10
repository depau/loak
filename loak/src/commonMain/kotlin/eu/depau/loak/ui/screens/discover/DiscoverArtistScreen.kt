package eu.depau.loak.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.PreviewManager
import eu.depau.loak.domain.discover.deezerImageUrl
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_artist
import eu.depau.loak.generated.resources.action_preview_top_songs
import eu.depau.loak.generated.resources.action_see_all
import eu.depau.loak.generated.resources.info_discover_artist_unavailable
import eu.depau.loak.generated.resources.title_albums_eps
import eu.depau.loak.generated.resources.title_popular
import eu.depau.loak.generated.resources.title_similar_artists
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.Artist
import eu.depau.loak.ui.components.dialogs.LinkConfirmationDialog
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.sheets.SheetLinks
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.discover.components.AlbumGestures
import eu.depau.loak.ui.screens.discover.components.ArtistGestures
import eu.depau.loak.ui.screens.discover.components.DiscoverArt
import eu.depau.loak.ui.screens.discover.components.DiscoverArtistSheet
import eu.depau.loak.ui.screens.discover.components.DiscoverBadge
import eu.depau.loak.ui.screens.discover.components.DiscoverPageState
import eu.depau.loak.ui.screens.discover.components.DiscoverPageTopBar
import eu.depau.loak.ui.screens.discover.components.LidarrAddFlow
import eu.depau.loak.ui.screens.discover.components.SectionTitle
import eu.depau.loak.ui.screens.discover.components.SongItem
import eu.depau.loak.ui.screens.discover.components.WithBadge
import eu.depau.loak.ui.screens.discover.components.albumKind
import eu.depau.loak.ui.screens.discover.components.dotted
import eu.depau.loak.ui.screens.discover.components.hasPreview
import eu.depau.loak.ui.screens.discover.components.lastFmUrlOf
import eu.depau.loak.ui.screens.discover.components.musicBrainzUrlOf
import eu.depau.loak.ui.screens.discover.components.rememberDiscoverStatus
import eu.depau.loak.ui.screens.discover.components.rememberLidarrAddFlow
import eu.depau.loak.ui.screens.discover.viewmodels.DiscoverArtistPage
import eu.depau.loak.ui.screens.discover.viewmodels.DiscoverArtistViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** An artist the library doesn't have: popular songs to preview, discography, similar artists. */
@Composable
fun DiscoverArtistScreen(key: Screen.DiscoverArtist) {
	val viewModel = koinViewModel<DiscoverArtistViewModel>(key = key.toString(), parameters = { parametersOf(key) })
	val state by viewModel.state.collectAsState()
	val page = state.data
	val addFlow = rememberLidarrAddFlow()
	var sheetOpen by rememberSaveable { mutableStateOf(false) }
	if (sheetOpen && page != null) DiscoverArtistSheet(page.artist, onDismissRequest = { sheetOpen = false }, addFlow = addFlow)

	Scaffold(
		topBar = { DiscoverPageTopBar(onMore = page?.let { { sheetOpen = true } }) },
		bottomBar = { RootBottomBar(scrolled = LocalBottomBarScrollManager.current.isTriggered) }
	) { padding ->
		if (page == null) DiscoverPageState(
			state is UiState.Loading, Res.string.info_discover_artist_unavailable, Icons.Outlined.Artist, padding
		)
		else ArtistPage(page, padding, addFlow)
	}
}

@Composable
private fun ArtistPage(page: DiscoverArtistPage, padding: PaddingValues, addFlow: LidarrAddFlow) {
	val artist = page.artist
	val lidarr = koinInject<LidarrManager>()
	val previewManager = koinInject<PreviewManager>()
	// fresh Lidarr state for the status badges
	LaunchedEffect(Unit) { lidarr.refreshCatalog() }
	val active = previewManager.activePreview.collectAsState()
	// just the audible song's id: progress ticks don't recompose the list
	val previewing by remember { derivedStateOf { active.value?.takeIf { it.isPlaying }?.song?.id } }
	// the rail: albums and EPs; singles and compilations wait behind "See all"
	var showAll by rememberSaveable { mutableStateOf(false) }
	val rail = page.albums.filter { it.recordType == "album" || it.recordType == "ep" }
	val albums = if (showAll || rail.isEmpty()) page.albums else rail
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }

	LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
		item(key = "header") {
			Column(
				Modifier.fillMaxWidth().padding(horizontal = 16.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				DiscoverArt(deezerImageUrl(artist.pictureUrl, 500), 160.dp, CircleShape)
				Spacer(Modifier.height(16.dp))
				Text(artist.name, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
				WithBadge(rememberDiscoverStatus(artist)) {
					Text(
						dotted(page.genre, page.area),
						Modifier.weight(1f, fill = false),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Button(onClick = { addFlow.addArtist(artist) }) {
						Icon(Icons.Outlined.Add, null, Modifier.size(ButtonDefaults.IconSize))
						Text(stringResource(Res.string.action_add_artist), Modifier.padding(start = ButtonDefaults.IconSpacing))
					}
					FilledTonalButton(
						onClick = { previewManager.playAll(page.topTracks) },
						enabled = page.topTracks.any { it.hasPreview }
					) {
						Icon(Icons.Filled.Play, null, Modifier.size(ButtonDefaults.IconSize))
						Text(stringResource(Res.string.action_preview_top_songs), Modifier.padding(start = ButtonDefaults.IconSpacing))
					}
				}
			}
		}
		if (page.topTracks.isNotEmpty()) item(key = "popular") { SectionTitle(Res.string.title_popular) }
		items(page.topTracks, key = { "song-${it.id}" }) { song ->
			SongItem(song, song.id == previewing, addFlow, subtitle = dotted(song.album)) { previewManager.toggle(song) }
		}
		if (albums.isNotEmpty()) item(key = "albums") {
			Row(verticalAlignment = Alignment.CenterVertically) {
				SectionTitle(Res.string.title_albums_eps, Modifier.weight(1f))
				if (albums.size < page.albums.size) TextButton(
					onClick = { showAll = true },
					modifier = Modifier.padding(top = 14.dp, end = 4.dp)
				) { Text(stringResource(Res.string.action_see_all)) }
			}
			LazyRow(
				contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				items(albums, key = { it.id }) { AlbumCard(it, addFlow) }
			}
		}
		if (page.similar.isNotEmpty()) item(key = "similar") {
			SectionTitle(Res.string.title_similar_artists)
			LazyRow(
				contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				items(page.similar, key = { it.id }) { SimilarArtist(it, addFlow) }
			}
		}
		item(key = "links") {
			SheetLinks(
				lastFmUrl = lastFmUrlOf(artist.name),
				musicBrainzUrl = musicBrainzUrlOf(artist),
				deezerUrl = artist.deezerId?.let { "https://www.deezer.com/artist/$it" },
				onOpen = { linkToOpen = it }
			)
		}
	}

	linkToOpen?.let { LinkConfirmationDialog(linkToOpen = it, onDismissRequest = { linkToOpen = null }) }
}

/** An album on the discography rail: cover with its status badge, title, kind and year. */
@Composable
private fun AlbumCard(album: DiscoverAlbum, addFlow: LidarrAddFlow) = AlbumGestures(album, addFlow) { gestures, _ ->
	val status = rememberDiscoverStatus(album)
	Column(Modifier.width(140.dp).clip(MaterialTheme.shapes.medium).then(gestures)) {
		Box {
			DiscoverArt(deezerImageUrl(album.coverUrl, 500), 140.dp, MaterialTheme.shapes.medium)
			// the badge needs a ground of its own on top of the cover
			Surface(
				Modifier.align(Alignment.BottomStart).padding(8.dp),
				shape = RoundedCornerShape(6.dp),
				color = MaterialTheme.colorScheme.surface
			) { DiscoverBadge(status) }
		}
		Text(
			album.title,
			Modifier.padding(top = 8.dp),
			style = MaterialTheme.typography.bodyLarge,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
		Text(
			dotted(albumKind(album), album.year?.toString()),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1
		)
	}
}

/** A similar artist on its rail: round picture, name and status badge. */
@Composable
private fun SimilarArtist(artist: DiscoverArtist, addFlow: LidarrAddFlow) = ArtistGestures(artist, addFlow) { gestures, _ ->
	Column(
		Modifier.width(96.dp).clip(MaterialTheme.shapes.medium).then(gestures),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp)
	) {
		DiscoverArt(artist.pictureUrl, 88.dp, CircleShape)
		Text(
			artist.name,
			style = MaterialTheme.typography.bodyMedium,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis,
			textAlign = TextAlign.Center
		)
		DiscoverBadge(rememberDiscoverStatus(artist))
	}
}
