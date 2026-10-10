package eu.depau.loak.ui.screens.discover.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.ListenBrainzPlaylist
import eu.depau.loak.domain.discover.deezerImageUrl
import eu.depau.loak.domain.manager.DiscoverBadgeState
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_more
import eu.depau.loak.generated.resources.count_songs
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
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.TopBarButton
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.theme.SmallCoverArtShape
import eu.depau.loak.ui.util.onSecondaryClick
import eu.depau.loak.ui.util.shimmerLoading
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Opens Discover albums and artists on Discover's own pages, even when the library has them: there
 * they show what can be downloaded, the library only badging what it already holds.
 */
@Stable
class DiscoverOpener internal constructor(private val backStack: NavBackStack<NavKey>) {
	/** Opens [album]; [then] runs once the page is on the stack (a sheet closing itself, say). */
	fun album(album: DiscoverAlbum, then: () -> Unit = {}) =
		open(Screen.DiscoverAlbum(album.deezerId, album.mbid, album.title, album.artist), then)

	/** Opens [artist]; [then] runs once the page is on the stack. */
	fun artist(artist: DiscoverArtist, then: () -> Unit = {}) =
		open(Screen.DiscoverArtist(artist.deezerId, artist.mbid, artist.name), then)

	/** A double tap doesn't push the page twice. */
	private fun open(screen: Screen, then: () -> Unit) {
		if (backStack.lastOrNull() != screen) backStack.add(screen)
		then()
	}
}

@Composable
fun rememberDiscoverOpener(): DiscoverOpener {
	val backStack = LocalNavStack.current
	return remember(backStack) { DiscoverOpener(backStack) }
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
 * A Discover song's gestures, handed to [content] as a modifier plus how to open its sheet: tap
 * toggles its preview, long-press or right-click opens [DiscoverSongSheet]. Songs the library has
 * behave the same, only badged.
 */
@Composable
fun SongGestures(
	song: DiscoverSong,
	onPreview: (DiscoverSong) -> Unit,
	addFlow: LidarrAddFlow,
	content: @Composable (gestures: Modifier, openSheet: () -> Unit) -> Unit
) {
	var sheetOpen by rememberSaveable { mutableStateOf(false) }
	if (sheetOpen) DiscoverSongSheet(
		song,
		onDismissRequest = { sheetOpen = false },
		onAddToLidarr = { addFlow.addSong(song) }
	)
	val openSheet = { sheetOpen = true }
	content(Modifier.onSecondaryClick(openSheet).combinedClickable(onLongClick = openSheet) { onPreview(song) }, openSheet)
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

/** A fixed-size Discover song row while its external preview lookup is pending. */
@Composable
fun DiscoverSongPlaceholder() {
	ListItem(
		leadingContent = { Box(Modifier.size(48.dp).clip(SmallCoverArtShape).shimmerLoading()) },
		headlineContent = { Box(Modifier.width(180.dp).height(16.dp).clip(CircleShape).shimmerLoading()) },
		supportingContent = {
			Box(Modifier.width(132.dp).height(14.dp).clip(CircleShape).shimmerLoading())
		},
		trailingContent = {
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				Box(Modifier.size(40.dp).clip(CircleShape).shimmerLoading())
				Box(Modifier.size(40.dp).clip(CircleShape).shimmerLoading())
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
	SongGestures(song, { onPreview() }, addFlow) { gestures, openSheet ->
		ResultItem(
			song.coverUrl, song.title, subtitle, status, gestures,
			previewing = previewing,
			onPreview = onPreview,
			onMore = openSheet
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

/**
 * A "made for you" playlist's stand-in cover: ListenBrainz ships no artwork, so each playlist
 * gets a theme gradient picked by its id — recognisably its own, nothing to fetch.
 */
@Composable
fun playlistGradient(seed: String): Brush {
	val colors = MaterialTheme.colorScheme
	val pairs = listOf(
		colors.primaryContainer to colors.tertiaryContainer,
		colors.tertiaryContainer to colors.secondaryContainer,
		colors.secondaryContainer to colors.primaryContainer
	)
	val (start, end) = pairs[seed.hashCode().mod(pairs.size)]
	return Brush.linearGradient(listOf(start, end))
}


@Composable
fun MadeForYouPlaceholder() {
	Box(Modifier.size(168.dp).clip(MaterialTheme.shapes.large).shimmerLoading())
}
/** One "made for you" playlist on a rail: gradient tile with its title, and its track count. */
@Composable
fun MadeForYouCard(playlist: ListenBrainzPlaylist, onClick: () -> Unit) {
	Column(Modifier.width(168.dp)) {
		Box(
			Modifier.size(168.dp)
				.clip(MaterialTheme.shapes.large)
				.background(playlistGradient(playlist.mbid))
				.clickable(onClick = onClick)
				.padding(14.dp),
			contentAlignment = Alignment.BottomStart
		) {
			Text(
				playlist.title,
				style = MaterialTheme.typography.titleMedium,
				color = MaterialTheme.colorScheme.onPrimaryContainer,
				maxLines = 3,
				overflow = TextOverflow.Ellipsis
			)
		}
		if (playlist.trackCount > 0) Text(
			pluralStringResource(Res.plurals.count_songs, playlist.trackCount, playlist.trackCount),
			Modifier.padding(top = 6.dp),
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	}
}

/** An album on a rail: cover with its status badge, title and [subtitle]. */
@Composable
fun DiscoverAlbumCard(
	album: DiscoverAlbum,
	addFlow: LidarrAddFlow,
	subtitle: String = dotted(albumKind(album), album.year?.toString())
) = AlbumGestures(album, addFlow) { gestures, _ ->
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
			subtitle,
			style = MaterialTheme.typography.bodyMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	}
}

@Composable
fun DiscoverAlbumCardPlaceholder() = Column(Modifier.width(140.dp)) {
	Box(Modifier.size(140.dp).clip(MaterialTheme.shapes.medium).shimmerLoading())
	Box(Modifier.padding(top = 8.dp).width(112.dp).height(16.dp).clip(CircleShape).shimmerLoading())
	Box(Modifier.padding(top = 4.dp).width(88.dp).height(14.dp).clip(CircleShape).shimmerLoading())
}

/** An artist on a rail: round picture, name and status badge. */
@Composable
fun DiscoverArtistCard(artist: DiscoverArtist, addFlow: LidarrAddFlow) = ArtistGestures(artist, addFlow) { gestures, _ ->
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

@Composable
fun DiscoverArtistCardPlaceholder() = Column(
	Modifier.width(96.dp),
	horizontalAlignment = Alignment.CenterHorizontally,
	verticalArrangement = Arrangement.spacedBy(6.dp)
) {
	Box(Modifier.size(88.dp).clip(CircleShape).shimmerLoading())
	Box(Modifier.width(72.dp).height(16.dp).clip(CircleShape).shimmerLoading())
}

/** One chart row: its [rank], art, title, artist with the status badge, and the preview button. */
@Composable
fun ChartSongItem(
	rank: Int,
	song: DiscoverSong,
	previewing: Boolean,
	addFlow: LidarrAddFlow,
	onPreview: () -> Unit
) {
	val status = rememberDiscoverStatus(song)
	SongGestures(song, { onPreview() }, addFlow) { gestures, _ ->
		ListItem(
			modifier = gestures,
			leadingContent = {
				Row(
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(12.dp)
				) {
					Text(
						rank.toString(),
						Modifier.widthIn(min = 24.dp),
						style = MaterialTheme.typography.titleMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						textAlign = TextAlign.End,
						maxLines = 1
					)
					DiscoverArt(song.coverUrl, 48.dp)
				}
			},
			headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
			supportingContent = {
				WithBadge(status) {
					Text(song.artist, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
			},
			trailingContent = { PreviewButton(previewing, onPreview, enabled = song.hasPreview) }
		)
	}
}

@Composable
fun ChartSongPlaceholder() {
	ListItem(
		leadingContent = {
			Row(
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Box(Modifier.width(24.dp).height(16.dp).clip(CircleShape).shimmerLoading())
				Box(Modifier.size(48.dp).clip(SmallCoverArtShape).shimmerLoading())
			}
		},
		headlineContent = { Box(Modifier.width(180.dp).height(16.dp).clip(CircleShape).shimmerLoading()) },
		supportingContent = { Box(Modifier.width(132.dp).height(14.dp).clip(CircleShape).shimmerLoading()) },
		trailingContent = { Box(Modifier.size(40.dp).clip(CircleShape).shimmerLoading()) }
	)
}
