package eu.depau.loak.ui.screens.discover.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.PreviewManager
import eu.depau.loak.domain.manager.ShareManager
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.domain.manager.albumFor
import eu.depau.loak.domain.manager.artistFor
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_to_lidarr_more
import eu.depau.loak.generated.resources.action_pause_preview
import eu.depau.loak.generated.resources.action_preview
import eu.depau.loak.generated.resources.action_share
import eu.depau.loak.generated.resources.action_view_album
import eu.depau.loak.generated.resources.action_view_artist
import eu.depau.loak.generated.resources.label_kind_artist
import eu.depau.loak.generated.resources.subtitle_add_song_to_lidarr
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Pause
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.Album
import eu.depau.loak.icons.outlined.Artist
import eu.depau.loak.icons.outlined.Share
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.dialogs.LinkConfirmationDialog
import eu.depau.loak.ui.components.sheets.ModalBottomSheet
import eu.depau.loak.ui.components.sheets.SheetLinks
import eu.depau.loak.ui.components.sheets.SheetRow
import eu.depau.loak.ui.components.sheets.SheetRows
import eu.depau.loak.ui.theme.SmallCoverArtShape
import io.ktor.http.encodeURLParameter
import io.ktor.http.encodeURLPathPart
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Cover art or an artist picture from Deezer or the Cover Art Archive: a plain URL. */
@Composable
fun DiscoverArt(url: String?, size: Dp, shape: Shape = SmallCoverArtShape) {
	AsyncImage(
		model = url,
		imageLoader = koinInject<ImageLoader>(),
		contentDescription = null,
		contentScale = ContentScale.Crop,
		modifier = Modifier
			.size(size)
			.clip(shape)
			.background(MaterialTheme.colorScheme.surfaceContainer)
	)
}

/** The non-blank [parts], joined by middle dots ("Album · 2009 · 10 songs"). */
fun dotted(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

/** Where MusicBrainz shows [song]: its ISRC page, else a recording search. */
fun musicBrainzUrlOf(song: DiscoverSong) = song.isrc?.takeIf { it.isNotBlank() }
	?.let { "https://musicbrainz.org/isrc/$it" }
	?: musicBrainzSearchUrl("recording", "${song.title} ${song.artist}")

/** Where MusicBrainz shows [album]: its release group, else a release group search. */
fun musicBrainzUrlOf(album: DiscoverAlbum) = album.mbid?.let { "https://musicbrainz.org/release-group/$it" }
	?: musicBrainzSearchUrl("release_group", "${album.title} ${album.artist}")

/** Where MusicBrainz shows [artist]: its page, else an artist search. */
fun musicBrainzUrlOf(artist: DiscoverArtist) = artist.mbid?.let { "https://musicbrainz.org/artist/$it" }
	?: musicBrainzSearchUrl("artist", artist.name)

/** [artist]'s last.fm page, or one of its albums' when [album] is given. */
fun lastFmUrlOf(artist: String, album: String? = null) =
	"https://www.last.fm/music/" + listOfNotNull(artist, album).joinToString("/") { it.encodeURLPathPart() }

/**
 * A Discover item's options sheet: its art, [title] and [subtitle] on top, then [rows] and its
 * pages on MusicBrainz, Deezer and last.fm.
 */
@Composable
private fun DiscoverSheet(
	art: String?,
	title: String,
	subtitle: String,
	rows: List<SheetRow>,
	musicBrainzUrl: String,
	deezerUrl: String?,
	lastFmUrl: String?,
	lidarrUrl: String?,
	onDismissRequest: () -> Unit,
	artShape: Shape = SmallCoverArtShape
) {
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }
	ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		menuOnWideWindows = true,
		contentWindowInsets = {
			BottomSheetDefaults.modalWindowInsets.add(WindowInsets(left = 8.dp, right = 8.dp))
		}
	) {
		Spacer(Modifier.height(16.dp))
		ListItem(
			leadingContent = { DiscoverArt(art, 50.dp, artShape) },
			headlineContent = { MarqueeText(title) },
			supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
			colors = ListItemDefaults.colors(containerColor = Color.Transparent)
		)
		Column(Modifier.verticalScroll(rememberScrollState())) {
			SheetRows(rows)
			SheetLinks(
				lastFmUrl = lastFmUrl,
				musicBrainzUrl = musicBrainzUrl,
				deezerUrl = deezerUrl,
				lidarrUrl = lidarrUrl,
				onOpen = { linkToOpen = it }
			)
		}
	}
	linkToOpen?.let { LinkConfirmationDialog(linkToOpen = it, onDismissRequest = { linkToOpen = null }) }
}

/** "Share", sending [parts] dotted together. */
@Composable
private fun shareRow(vararg parts: String?): SheetRow {
	val shareManager = koinInject<ShareManager>()
	val scope = rememberCoroutineScope()
	val text = dotted(*parts)
	return SheetRow(stringResource(Res.string.action_share), Icons.Outlined.Share, {
		scope.launch { shareManager.shareString(text) }
	})
}

/**
 * The options sheet of a Discover song the library doesn't have: preview, add to Lidarr, its
 * album and artist, share, and its pages on MusicBrainz and Deezer.
 */
@Composable
fun DiscoverSongSheet(song: DiscoverSong, onDismissRequest: () -> Unit, onAddToLidarr: () -> Unit) {
	val previewManager = koinInject<PreviewManager>()
	val opener = rememberDiscoverOpener()
	val active by previewManager.activePreview.collectAsState()
	val previewing = active?.takeIf { it.isPlaying }?.song?.id == song.id
	val deezerUrl = song.deezerId?.let { "https://www.deezer.com/track/$it" }
	val lidarr = koinInject<LidarrManager>()
	val catalog by lidarr.catalog.collectAsState()
	// Lidarr has no page per track: its album's
	val lidarrUrl = catalog.albumFor(null, song.album, song.artist)?.musicBrainzId?.let(lidarr::albumPageUrl)

	DiscoverSheet(
		art = song.coverUrl,
		title = song.title,
		subtitle = dotted(song.album, song.artist),
		rows = listOfNotNull(
			SheetRow(
				stringResource(if (previewing) Res.string.action_pause_preview else Res.string.action_preview),
				if (previewing) Icons.Filled.Pause else Icons.Filled.Play,
				{ previewManager.toggle(song); onDismissRequest() },
				enabled = song.hasPreview
			),
			SheetRow(
				stringResource(Res.string.action_add_to_lidarr_more),
				Icons.Outlined.Add,
				{ onDismissRequest(); onAddToLidarr() },
				supporting = stringResource(Res.string.subtitle_add_song_to_lidarr)
			),
			song.album?.let { title ->
				SheetRow(stringResource(Res.string.action_view_album), Icons.Outlined.Album, {
					opener.album(DiscoverAlbum("", title, song.artist, deezerId = song.albumDeezerId), onDismissRequest)
				})
			},
			SheetRow(stringResource(Res.string.action_view_artist), Icons.Outlined.Artist, {
				opener.artist(DiscoverArtist("", song.artist, deezerId = song.artistDeezerId), onDismissRequest)
			}),
			shareRow(song.title, song.artist, deezerUrl)
		),
		musicBrainzUrl = musicBrainzUrlOf(song),
		deezerUrl = deezerUrl,
		lastFmUrl = null,
		lidarrUrl = lidarrUrl,
		onDismissRequest = onDismissRequest
	)
}

/** A Discover album's options sheet: add to Lidarr, its artist, share, and its pages elsewhere. */
@Composable
fun DiscoverAlbumSheet(album: DiscoverAlbum, onDismissRequest: () -> Unit, addFlow: LidarrAddFlow) {
	val opener = rememberDiscoverOpener()
	val deezerUrl = album.deezerId?.let { "https://www.deezer.com/album/$it" }
	val lidarr = koinInject<LidarrManager>()
	val catalog by lidarr.catalog.collectAsState()
	val lidarrUrl = catalog.albumFor(album)?.musicBrainzId?.let(lidarr::albumPageUrl)
	DiscoverSheet(
		art = album.coverUrl,
		title = album.title,
		subtitle = dotted(albumKind(album), album.artist, album.year?.toString()),
		rows = listOf(
			SheetRow(stringResource(Res.string.action_add_to_lidarr_more), Icons.Outlined.Add, {
				onDismissRequest()
				addFlow.addAlbum(album)
			}),
			SheetRow(stringResource(Res.string.action_view_artist), Icons.Outlined.Artist, {
				opener.artist(DiscoverArtist("", album.artist, deezerId = album.artistDeezerId), onDismissRequest)
			}),
			shareRow(album.title, album.artist, deezerUrl)
		),
		musicBrainzUrl = musicBrainzUrlOf(album),
		deezerUrl = deezerUrl,
		lastFmUrl = lastFmUrlOf(album.artist, album.title),
		lidarrUrl = lidarrUrl,
		onDismissRequest = onDismissRequest
	)
}

/** A Discover artist's options sheet: add to Lidarr, share, and its pages elsewhere. */
@Composable
fun DiscoverArtistSheet(artist: DiscoverArtist, onDismissRequest: () -> Unit, addFlow: LidarrAddFlow) {
	val deezerUrl = artist.deezerId?.let { "https://www.deezer.com/artist/$it" }
	val lidarr = koinInject<LidarrManager>()
	val catalog by lidarr.catalog.collectAsState()
	val lidarrUrl = catalog.artistFor(artist)?.musicBrainzId?.let(lidarr::artistPageUrl)
	DiscoverSheet(
		art = artist.pictureUrl,
		title = artist.name,
		subtitle = stringResource(Res.string.label_kind_artist),
		rows = listOf(
			SheetRow(stringResource(Res.string.action_add_to_lidarr_more), Icons.Outlined.Add, {
				onDismissRequest()
				addFlow.addArtist(artist)
			}),
			shareRow(artist.name, deezerUrl)
		),
		musicBrainzUrl = musicBrainzUrlOf(artist),
		deezerUrl = deezerUrl,
		lastFmUrl = lastFmUrlOf(artist.name),
		lidarrUrl = lidarrUrl,
		onDismissRequest = onDismissRequest,
		artShape = CircleShape
	)
}
