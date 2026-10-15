package eu.depau.loak.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.domain.discover.PreviewManager
import eu.depau.loak.domain.discover.deezerImageUrl
import eu.depau.loak.domain.manager.DiscoverBadgeState
import eu.depau.loak.domain.manager.LidarrAlbumSnapshot
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.domain.manager.LinkManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.albumFor
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_album
import eu.depau.loak.generated.resources.action_open_in_lidarr
import eu.depau.loak.generated.resources.action_preview
import eu.depau.loak.generated.resources.action_search_again
import eu.depau.loak.generated.resources.action_view_album
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.info_discover_album_unavailable
import eu.depau.loak.generated.resources.info_lidarr_downloaded
import eu.depau.loak.generated.resources.info_lidarr_searching
import eu.depau.loak.generated.resources.info_own_album_songs
import eu.depau.loak.generated.resources.info_missing_album_songs
import eu.depau.loak.generated.resources.title_missing_songs
import eu.depau.loak.generated.resources.label_size_of_total
import eu.depau.loak.generated.resources.notice_lidarr_search_failed
import eu.depau.loak.generated.resources.title_in_your_library
import eu.depau.loak.generated.resources.title_lidarr_downloaded
import eu.depau.loak.generated.resources.title_lidarr_downloading
import eu.depau.loak.generated.resources.title_lidarr_searching
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.Album
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.Info
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.discover.components.DiscoverAlbumSheet
import eu.depau.loak.ui.screens.discover.components.DiscoverArt
import eu.depau.loak.ui.screens.discover.components.DiscoverPageState
import eu.depau.loak.ui.screens.discover.components.DiscoverPageTopBar
import eu.depau.loak.ui.screens.discover.components.LidarrAddFlow
import eu.depau.loak.ui.screens.discover.components.PreviewButton
import eu.depau.loak.ui.screens.discover.components.SongGestures
import eu.depau.loak.ui.screens.discover.components.WithBadge
import eu.depau.loak.ui.screens.discover.components.albumKind
import eu.depau.loak.ui.screens.discover.components.dotted
import eu.depau.loak.ui.screens.discover.components.hasPreview
import eu.depau.loak.ui.screens.discover.components.rememberDiscoverOpener
import eu.depau.loak.ui.screens.discover.components.rememberLidarrAddFlow
import eu.depau.loak.ui.screens.discover.viewmodels.DiscoverAlbumPage
import eu.depau.loak.ui.screens.discover.viewmodels.DiscoverAlbumViewModel
import eu.depau.loak.util.toFileSize
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** An album the library doesn't have: its tracks to preview, and its way into Lidarr. */
@Composable
fun DiscoverAlbumScreen(key: Screen.DiscoverAlbum) {
	val viewModel = koinViewModel<DiscoverAlbumViewModel>(key = key.toString(), parameters = { parametersOf(key) })
	val state by viewModel.state.collectAsState()
	val page = state.data
	val addFlow = rememberLidarrAddFlow()
	var sheetOpen by rememberSaveable { mutableStateOf(false) }
	if (sheetOpen && page != null) DiscoverAlbumSheet(page.album, onDismissRequest = { sheetOpen = false }, addFlow = addFlow)

	Scaffold(
		topBar = { DiscoverPageTopBar(onMore = page?.let { { sheetOpen = true } }) },
		bottomBar = { RootBottomBar(scrolled = LocalBottomBarScrollManager.current.isTriggered) }
	) { padding ->
		if (page == null) DiscoverPageState(
			state is UiState.Loading, Res.string.info_discover_album_unavailable, Icons.Outlined.Album, padding
		)
		else AlbumPage(page, padding, addFlow)
	}
}

@Composable
private fun AlbumPage(page: DiscoverAlbumPage, padding: PaddingValues, addFlow: LidarrAddFlow) {
	val (album, tracks) = page
	val lidarr = koinInject<LidarrManager>()
	val matcher = koinInject<LibraryMatcher>()
	val previewManager = koinInject<PreviewManager>()
	val catalog by lidarr.catalog.collectAsState()
	val library by matcher.generation.collectAsState()
	// fresh Lidarr state for the status card and badges
	LaunchedEffect(Unit) { lidarr.refreshCatalog() }
	val libraryAlbum by produceState<String?>(null, album, library) { value = matcher.albumInLibrary(album)?.id }
	val librarySongs by produceState(emptyMap<String, DomainSong>(), tracks, library) {
		value = tracks.mapNotNull { track -> matcher.songInLibrary(track)?.let { track.id to it } }.toMap()
	}
	// an unmonitored album (the artist's other releases) still needs adding
	val inLidarr = catalog.albumFor(album)?.takeIf { it.monitored }
	val active = previewManager.activePreview.collectAsState()
	// just the audible song's id: progress ticks don't recompose the list
	val previewing by remember { derivedStateOf { active.value?.takeIf { it.isPlaying }?.song?.id } }

	LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
		item(key = "header") {
			Column(
				Modifier.fillMaxWidth().padding(horizontal = 16.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				val added = libraryAlbum != null || inLidarr != null
				DiscoverArt(deezerImageUrl(album.coverUrl, 500), if (added) 200.dp else 240.dp, MaterialTheme.shapes.medium)
				Spacer(Modifier.height(16.dp))
				Text(album.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
				AlbumSubtitle(album, tracks.size)
				Spacer(Modifier.height(16.dp))
				val inLibrary = libraryAlbum
				// the songs the library doesn't have; its tracklist can hold more than the library's copy
				val missing = tracks.size - librarySongs.size
				when {
					inLibrary != null && missing == 0 -> InLibraryCard(inLibrary)
					// Lidarr is already on it: waiting fills the gaps
					inLibrary != null && inLidarr != null -> LidarrStatus(inLidarr)
					inLibrary != null -> MissingSongsCard(album, inLibrary, missing, addFlow)
					inLidarr != null -> LidarrStatus(inLidarr)
					else -> AddButtons(album, tracks, librarySongs.size, addFlow)
				}
				Spacer(Modifier.height(8.dp))
			}
		}
		itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
			TrackRow(index, track, librarySongs[track.id], track.id == previewing, addFlow) { previewManager.toggle(track) }
		}
	}
}

/** "deadmau5 · Album · 2009 · 10 songs", the artist a link to their page. */
@Composable
private fun AlbumSubtitle(album: DiscoverAlbum, songs: Int) {
	val opener = rememberDiscoverOpener()
	val linkColor = MaterialTheme.colorScheme.primary
	val rest = dotted(albumKind(album), album.year?.toString(), pluralStringResource(Res.plurals.count_songs, songs, songs))
	Text(
		buildAnnotatedString {
			withLink(LinkAnnotation.Clickable("artist", TextLinkStyles(SpanStyle(color = linkColor))) {
				opener.artist(DiscoverArtist("", album.artist, deezerId = album.artistDeezerId))
			}) { append(album.artist) }
			append(" · $rest")
		},
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		textAlign = TextAlign.Center
	)
}

/** Before adding: "Add album" and "Preview", and a word on the songs the library already has. */
@Composable
private fun AddButtons(album: DiscoverAlbum, tracks: List<DiscoverSong>, owned: Int, addFlow: LidarrAddFlow) {
	val previewManager = koinInject<PreviewManager>()
	Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		Button(onClick = { addFlow.addAlbum(album) }) {
			Icon(Icons.Outlined.Add, null, Modifier.size(ButtonDefaults.IconSize))
			Text(stringResource(Res.string.action_add_album), Modifier.padding(start = ButtonDefaults.IconSpacing))
		}
		FilledTonalButton(onClick = { previewManager.playAll(tracks) }, enabled = tracks.any { it.hasPreview }) {
			Icon(Icons.Filled.Play, null, Modifier.size(ButtonDefaults.IconSize))
			Text(stringResource(Res.string.action_preview), Modifier.padding(start = ButtonDefaults.IconSpacing))
		}
	}
	if (owned > 0) Surface(
		modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.surfaceContainerHigh
	) {
		Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
			Icon(Icons.Outlined.Info, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
			Text(
				pluralStringResource(Res.plurals.info_own_album_songs, owned, owned),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
	}
}

/** The library has it now: say so, with the way to its own page. */
@Composable
private fun InLibraryCard(albumId: String) {
	val backStack = LocalNavStack.current
	StatusCard(Icons.Outlined.Check, stringResource(Res.string.title_in_your_library), null, MaterialTheme.colorScheme.primaryContainer)
	OutlinedButton(
		onClick = dropUnlessResumed { backStack.add(Screen.CollectionDetail(albumId, "library")) },
		modifier = Modifier.padding(top = 12.dp)
	) { Text(stringResource(Res.string.action_view_album)) }
}

/** The library has part of it: what's missing, the way to it from Lidarr, and to its own page. */
@Composable
private fun MissingSongsCard(album: DiscoverAlbum, libraryAlbumId: String, missing: Int, addFlow: LidarrAddFlow) {
	val backStack = LocalNavStack.current
	StatusCard(
		Icons.Outlined.Download,
		stringResource(Res.string.title_missing_songs),
		pluralStringResource(Res.plurals.info_missing_album_songs, missing, missing),
		MaterialTheme.colorScheme.secondaryContainer
	)
	Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		Button(onClick = { addFlow.addAlbum(album) }) {
			Icon(Icons.Outlined.Add, null, Modifier.size(ButtonDefaults.IconSize))
			Text(stringResource(Res.string.action_add_album), Modifier.padding(start = ButtonDefaults.IconSpacing))
		}
		OutlinedButton(onClick = dropUnlessResumed { backStack.add(Screen.CollectionDetail(libraryAlbumId, "library")) }) {
			Text(stringResource(Res.string.action_view_album))
		}
	}
}

/**
 * Lidarr has the album: searching for a release, downloading it (polled while on screen), or
 * done and waiting for the library's next scan. "Search again" and "Open in Lidarr" below.
 */
@Composable
private fun LidarrStatus(album: LidarrAlbumSnapshot) {
	val lidarr = koinInject<LidarrManager>()
	val linkManager = koinInject<LinkManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val catalog by lidarr.catalog.collectAsState()
	val queued = catalog.queue.firstOrNull { it.albumId == album.id }
	DisposableEffect(Unit) {
		lidarr.startQueuePolling()
		onDispose { lidarr.stopQueuePolling() }
	}
	// it left the queue: Lidarr imported it (or gave up), which only the album itself says
	var lastQueued by remember { mutableStateOf<Long?>(null) }
	LaunchedEffect(queued?.id) {
		if (queued == null && lastQueued != null) lidarr.refreshCatalog()
		lastQueued = queued?.id
	}

	val colors = MaterialTheme.colorScheme
	when {
		queued != null -> StatusCard(
			Icons.Outlined.Download,
			stringResource(Res.string.title_lidarr_downloading),
			dotted(
				stringResource(
					Res.string.label_size_of_total,
					(queued.size - queued.sizeLeft).toLong().toFileSize(),
					queued.size.toLong().toFileSize()
				),
				queued.quality,
				timeLeftOf(queued)
			),
			colors.tertiaryContainer,
			progress = { queued.progress }
		)
		album.downloaded -> StatusCard(
			Icons.Outlined.Check,
			stringResource(Res.string.title_lidarr_downloaded),
			stringResource(Res.string.info_lidarr_downloaded),
			colors.secondaryContainer
		)
		else -> StatusCard(
			Icons.Outlined.Search,
			stringResource(Res.string.title_lidarr_searching),
			stringResource(Res.string.info_lidarr_searching),
			colors.secondaryContainer,
			indeterminate = true
		)
	}
	Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		// downloaded or not, a new search can find a better release
		OutlinedButton(onClick = {
			snackBarManager.launch {
				try {
					lidarr.searchAlbum(album.id)
				} catch (e: CancellationException) {
					throw e
				} catch (e: Exception) {
					snackBarManager.notify(Res.string.notice_lidarr_search_failed, e.message.orEmpty())
				}
			}
		}) { Text(stringResource(Res.string.action_search_again)) }
		album.musicBrainzId?.let { mbid ->
			OutlinedButton(onClick = { linkManager.openLink(lidarr.albumPageUrl(mbid)) }) {
				Text(stringResource(Res.string.action_open_in_lidarr))
			}
		}
	}
}

@Composable
private fun StatusCard(
	icon: ImageVector,
	title: String,
	text: String?,
	color: Color,
	progress: (() -> Float)? = null,
	indeterminate: Boolean = false
) {
	Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = color) {
		Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Icon(icon, null)
				Column {
					Text(title, style = MaterialTheme.typography.titleMedium)
					text?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
				}
			}
			if (progress != null) LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth())
			else if (indeterminate) LinearProgressIndicator(Modifier.fillMaxWidth())
		}
	}
}

/** One track: its number, title, artist with the "In library" badge, and its preview button. */
@Composable
private fun TrackRow(
	index: Int,
	track: DiscoverSong,
	librarySong: DomainSong?,
	previewing: Boolean,
	addFlow: LidarrAddFlow,
	onPreview: () -> Unit
) {
	val status = librarySong?.let { DiscoverBadgeState.InLibrary(song = it) } ?: DiscoverBadgeState.None
	SongGestures(track, { onPreview() }, addFlow) { gestures, _ ->
		ListItem(
			modifier = gestures,
			leadingContent = {
				Text(
					"${index + 1}",
					Modifier.width(24.dp),
					style = MaterialTheme.typography.bodyLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					textAlign = TextAlign.Center
				)
			},
			headlineContent = { Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
			supportingContent = {
				WithBadge(status) {
					Text(track.artist, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
			},
			trailingContent = { PreviewButton(previewing, onPreview, enabled = track.hasPreview) }
		)
	}
}
