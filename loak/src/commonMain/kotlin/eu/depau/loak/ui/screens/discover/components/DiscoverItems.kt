package eu.depau.loak.ui.screens.discover.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.domain.manager.DiscoverBadgeState
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_more
import eu.depau.loak.generated.resources.action_pause_preview
import eu.depau.loak.generated.resources.action_preview
import eu.depau.loak.generated.resources.label_kind_album
import eu.depau.loak.generated.resources.label_kind_artist
import eu.depau.loak.generated.resources.label_kind_compilation
import eu.depau.loak.generated.resources.label_kind_ep
import eu.depau.loak.generated.resources.label_kind_single
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Pause
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.MoreVert
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.sheets.SongActionsSheet
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.TopBarButton
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.theme.SmallCoverArtShape
import eu.depau.loak.ui.util.onSecondaryClick
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Opens Discover albums and artists where they live: Lo'ak's own page when the library has
 * them, Discover's page otherwise.
 */
@Stable
class DiscoverOpener internal constructor(
	private val scope: CoroutineScope,
	private val matcher: LibraryMatcher,
	private val backStack: NavBackStack<NavKey>
) {
	private var opening: Job? = null

	/** Opens [album]; [then] runs once the page is on the stack (a sheet closing itself, say). */
	fun album(album: DiscoverAlbum, then: () -> Unit = {}) = open(then) {
		matcher.albumInLibrary(album)?.let { Screen.CollectionDetail(it.id, "library") }
			?: Screen.DiscoverAlbum(album.deezerId, album.mbid, album.title, album.artist)
	}

	/** Opens [artist]; [then] runs once the page is on the stack. */
	fun artist(artist: DiscoverArtist, then: () -> Unit = {}) = open(then) {
		matcher.artistInLibrary(artist)?.let { Screen.ArtistDetail(it.id) }
			?: Screen.DiscoverArtist(artist.deezerId, artist.mbid, artist.name)
	}

	/** One at a time, so a double tap doesn't push the page twice. */
	private fun open(then: () -> Unit, screen: suspend () -> Screen) {
		if (opening?.isActive == true) return
		opening = scope.launch {
			backStack.add(screen())
			then()
		}
	}
}

@Composable
fun rememberDiscoverOpener(): DiscoverOpener {
	val scope = rememberCoroutineScope()
	val matcher = koinInject<LibraryMatcher>()
	val backStack = LocalNavStack.current
	return remember { DiscoverOpener(scope, matcher, backStack) }
}

@Composable
fun albumKind(album: DiscoverAlbum) = stringResource(
	when (album.recordType) {
		"single" -> Res.string.label_kind_single
		"ep" -> Res.string.label_kind_ep
		"compile" -> Res.string.label_kind_compilation
		else -> Res.string.label_kind_album
	}
)

@Composable
fun SectionTitle(title: StringResource, modifier: Modifier = Modifier) {
	Text(
		stringResource(title),
		style = MaterialTheme.typography.titleLarge,
		modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 4.dp)
	)
}

/** [text] (one weighted Text) followed by the item's status badge, when it has one. */
@Composable
fun WithBadge(status: DiscoverBadgeState, text: @Composable RowScope.() -> Unit) {
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
		text()
		DiscoverBadge(status)
	}
}

/** A Discover song's round play/pause preview button; disabled for songs without a preview. */
@Composable
fun PreviewButton(previewing: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
	FilledTonalIconButton(onClick = onClick, enabled = enabled) {
		Icon(
			if (previewing) Icons.Filled.Pause else Icons.Filled.Play,
			stringResource(if (previewing) Res.string.action_pause_preview else Res.string.action_preview),
			Modifier.size(18.dp)
		)
	}
}

/** Whether [song] can play a preview: it has a URL, or a Deezer id to fetch one. */
val DiscoverSong.hasPreview get() = previewUrl != null || deezerId != null

/**
 * A Discover song's gestures, handed to [content] as a modifier plus how to open its sheet: a
 * song already in the library plays with the normal player on tap and has the normal song sheet;
 * any other toggles its preview and has [DiscoverSongSheet].
 */
@Composable
fun SongGestures(
	song: DiscoverSong,
	status: DiscoverBadgeState,
	onPreview: (DiscoverSong) -> Unit,
	addFlow: LidarrAddFlow,
	content: @Composable (gestures: Modifier, openSheet: () -> Unit) -> Unit
) {
	val librarySong = (status as? DiscoverBadgeState.InLibrary)?.song
	val player = koinInject<MediaPlayerViewModel>()
	var sheetOpen by rememberSaveable { mutableStateOf(false) }
	if (librarySong != null) SongActionsSheet(librarySong, sheetOpen, onDismissRequest = { sheetOpen = false })
	else if (sheetOpen) DiscoverSongSheet(
		song,
		onDismissRequest = { sheetOpen = false },
		onAddToLidarr = { addFlow.addSong(song) }
	)
	val openSheet = { sheetOpen = true }
	content(
		Modifier.onSecondaryClick(openSheet).combinedClickable(onLongClick = openSheet) {
			if (librarySong != null) player.playNow(librarySong) else onPreview(song)
		},
		openSheet
	)
}

/** A Discover album's gestures: opens it on tap, its options sheet on long-press or right-click. */
@Composable
fun AlbumGestures(
	album: DiscoverAlbum,
	addFlow: LidarrAddFlow,
	content: @Composable (gestures: Modifier, openSheet: () -> Unit) -> Unit
) {
	val opener = rememberDiscoverOpener()
	var sheetOpen by rememberSaveable { mutableStateOf(false) }
	if (sheetOpen) DiscoverAlbumSheet(album, onDismissRequest = { sheetOpen = false }, addFlow = addFlow)
	val openSheet = { sheetOpen = true }
	content(Modifier.onSecondaryClick(openSheet).combinedClickable(onLongClick = openSheet) { opener.album(album) }, openSheet)
}

/** A Discover artist's gestures: opens it on tap, its options sheet on long-press or right-click. */
@Composable
fun ArtistGestures(
	artist: DiscoverArtist,
	addFlow: LidarrAddFlow,
	content: @Composable (gestures: Modifier, openSheet: () -> Unit) -> Unit
) {
	val opener = rememberDiscoverOpener()
	var sheetOpen by rememberSaveable { mutableStateOf(false) }
	if (sheetOpen) DiscoverArtistSheet(artist, onDismissRequest = { sheetOpen = false }, addFlow = addFlow)
	val openSheet = { sheetOpen = true }
	content(Modifier.onSecondaryClick(openSheet).combinedClickable(onLongClick = openSheet) { opener.artist(artist) }, openSheet)
}

/** One Discover list row: art, title, subtitle with the status badge, preview and overflow buttons. */
@Composable
fun ResultItem(
	art: String?,
	title: String,
	subtitle: String,
	status: DiscoverBadgeState,
	modifier: Modifier = Modifier,
	artShape: Shape = SmallCoverArtShape,
	previewing: Boolean = false,
	onPreview: (() -> Unit)? = null,
	onMore: () -> Unit
) {
	ListItem(
		modifier = modifier,
		leadingContent = { DiscoverArt(art, 48.dp, artShape) },
		headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
		supportingContent = {
			WithBadge(status) {
				Text(subtitle, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		},
		trailingContent = {
			Row(verticalAlignment = Alignment.CenterVertically) {
				if (onPreview != null) PreviewButton(previewing, onPreview)
				IconButton(onClick = onMore) {
					Icon(Icons.Outlined.MoreVert, stringResource(Res.string.action_more))
				}
			}
		}
	)
}

@Composable
fun SongItem(
	song: DiscoverSong,
	previewing: Boolean,
	addFlow: LidarrAddFlow,
	subtitle: String = dotted(song.artist, song.album),
	onPreview: () -> Unit
) {
	val status = rememberDiscoverStatus(song)
	SongGestures(song, status, { onPreview() }, addFlow) { gestures, openSheet ->
		ResultItem(
			song.coverUrl, song.title, subtitle, status, gestures,
			previewing = previewing, onPreview = onPreview, onMore = openSheet
		)
	}
}

@Composable
fun AlbumItem(album: DiscoverAlbum, addFlow: LidarrAddFlow) = AlbumGestures(album, addFlow) { gestures, openSheet ->
	ResultItem(
		album.coverUrl, album.title, dotted(albumKind(album), album.artist, album.year?.toString()),
		rememberDiscoverStatus(album), gestures, onMore = openSheet
	)
}

@Composable
fun ArtistItem(artist: DiscoverArtist, addFlow: LidarrAddFlow) = ArtistGestures(artist, addFlow) { gestures, openSheet ->
	ResultItem(
		artist.pictureUrl, artist.name, stringResource(Res.string.label_kind_artist),
		rememberDiscoverStatus(artist), gestures, artShape = CircleShape, onMore = openSheet
	)
}

/** A Discover page's top bar: back, and the page's options once it has loaded. */
@Composable
fun DiscoverPageTopBar(onMore: (() -> Unit)?) = NestedTopBar(title = {}, actions = {
	if (onMore != null) TopBarButton(onClick = onMore) {
		Icon(Icons.Outlined.MoreVert, stringResource(Res.string.action_more))
	}
})

/** A Discover page while it loads, or what it says when it couldn't. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoverPageState(loading: Boolean, unavailable: StringResource, icon: ImageVector, padding: PaddingValues) {
	if (loading) Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
		ContainedLoadingIndicator(Modifier.size(80.dp))
	}
	else ContentUnavailable(Modifier.fillMaxSize().padding(padding), icon = icon, label = stringResource(unavailable))
}
