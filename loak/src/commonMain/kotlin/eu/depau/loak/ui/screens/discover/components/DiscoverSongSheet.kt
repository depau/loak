package eu.depau.loak.ui.screens.discover.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.produceState
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
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.ImageLoader
import coil3.compose.AsyncImage
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.domain.discover.PreviewManager
import eu.depau.loak.domain.manager.ShareManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_to_lidarr_more
import eu.depau.loak.generated.resources.action_pause_preview
import eu.depau.loak.generated.resources.action_preview
import eu.depau.loak.generated.resources.action_share
import eu.depau.loak.generated.resources.action_view_album
import eu.depau.loak.generated.resources.action_view_artist
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
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.theme.SmallCoverArtShape
import io.ktor.http.encodeURLParameter
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
	?: "https://musicbrainz.org/search?type=recording&query=" +
		"${song.title} ${song.artist}".encodeURLParameter()

/**
 * The options sheet of a Discover song the library doesn't have: preview, add to Lidarr, its
 * album and artist when the library has them (Discover's own pages come with M7), share, and
 * its pages on MusicBrainz and Deezer.
 */
// ponytail: "Go to album/artist" only shows for library matches until the Discover pages (M7)
@Composable
fun DiscoverSongSheet(song: DiscoverSong, onDismissRequest: () -> Unit, onAddToLidarr: () -> Unit) {
	val backStack = LocalNavStack.current
	val previewManager = koinInject<PreviewManager>()
	val matcher = koinInject<LibraryMatcher>()
	val shareManager = koinInject<ShareManager>()
	val scope = rememberCoroutineScope()
	val active by previewManager.activePreview.collectAsState()
	val previewing = active?.takeIf { it.isPlaying }?.song?.id == song.id
	val album by produceState<String?>(null, song) {
		value = song.album?.let { matcher.albumInLibrary(DiscoverAlbum("", it, song.artist))?.id }
	}
	val artist by produceState<String?>(null, song) {
		value = matcher.artistInLibrary(DiscoverArtist("", song.artist))?.id
	}
	val deezerUrl = song.deezerId?.let { "https://www.deezer.com/track/$it" }
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
			leadingContent = { DiscoverArt(song.coverUrl, 50.dp) },
			headlineContent = { MarqueeText(song.title) },
			supportingContent = {
				Text(dotted(song.album, song.artist), maxLines = 1, overflow = TextOverflow.Ellipsis)
			},
			colors = ListItemDefaults.colors(containerColor = Color.Transparent)
		)
		Column(Modifier.verticalScroll(rememberScrollState())) {
			SheetRows(listOfNotNull(
				SheetRow(
					stringResource(if (previewing) Res.string.action_pause_preview else Res.string.action_preview),
					if (previewing) Icons.Filled.Pause else Icons.Filled.Play,
					{ previewManager.toggle(song); onDismissRequest() },
					enabled = song.previewUrl != null || song.deezerId != null
				),
				SheetRow(
					stringResource(Res.string.action_add_to_lidarr_more),
					Icons.Outlined.Add,
					{ onDismissRequest(); onAddToLidarr() },
					supporting = stringResource(Res.string.subtitle_add_song_to_lidarr)
				),
				album?.let {
					SheetRow(stringResource(Res.string.action_view_album), Icons.Outlined.Album, dropUnlessResumed {
						onDismissRequest()
						backStack.add(Screen.CollectionDetail(it, "library"))
					})
				},
				artist?.let {
					SheetRow(stringResource(Res.string.action_view_artist), Icons.Outlined.Artist, dropUnlessResumed {
						onDismissRequest()
						backStack.add(Screen.ArtistDetail(it))
					})
				},
				SheetRow(stringResource(Res.string.action_share), Icons.Outlined.Share, {
					scope.launch { shareManager.shareString(dotted(song.title, song.artist, deezerUrl)) }
				})
			))
			SheetLinks(
				lastFmUrl = null,
				musicBrainzUrl = musicBrainzUrlOf(song),
				deezerUrl = deezerUrl,
				onOpen = { linkToOpen = it }
			)
		}
	}

	linkToOpen?.let { LinkConfirmationDialog(linkToOpen = it, onDismissRequest = { linkToOpen = null }) }
}
