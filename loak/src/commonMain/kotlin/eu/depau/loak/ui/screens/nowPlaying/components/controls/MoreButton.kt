package eu.depau.loak.ui.screens.nowPlaying.components.controls

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_more
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.MoreHoriz
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.sheets.SongSheet
import eu.depau.loak.ui.components.sheets.SleepTimerSheet
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import eu.depau.loak.ui.theme.LoakTheme
import eu.depau.loak.ui.util.rememberColorSchemeFromCoverArt
import kotlin.time.Duration

@Composable
fun NowPlayingMoreButton() {
	val backStack = LocalNavStack.current
	val player = koinInject<MediaPlayerViewModel>()
	val songRepository = koinInject<SongRepository>()
	val scope = rememberCoroutineScope()
	val playerState by player.uiState.collectAsState()
	val currentSong = playerState.currentSong
	// the song the menu was opened for: the actions must not follow the player to the next track
	var song by remember { mutableStateOf<DomainSong?>(null) }
	var expanded by remember { mutableStateOf(false) }
	var sleepTimerSheetShown by rememberSaveable { mutableStateOf(false) }
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }
	val colorScheme = rememberColorSchemeFromCoverArt(
		(if (expanded) song else currentSong)?.coverArtId
	)

	IconButton(
		onClick = {
			song = currentSong
			expanded = true
		},
		colors = IconButtonDefaults.filledTonalIconButtonColors(),
		modifier = Modifier.size(32.dp),
		enabled = currentSong != null
	) {
		Icon(
			imageVector = Icons.Outlined.MoreHoriz,
			contentDescription = stringResource(Res.string.action_more)
		)
	}

	val menuSong = song
	var rating by remember(menuSong?.id) { mutableIntStateOf(0) }
	LaunchedEffect(menuSong?.id) {
		if (menuSong != null) rating = songRepository.getSongRating(menuSong)
	}
	if (expanded && menuSong != null) {
		LoakTheme(colorScheme) {
			SongSheet(
				onDismissRequest = { expanded = false },
				song = menuSong,
				collection = playerState.currentCollection,
				onViewAlbum = dropUnlessResumed {
					menuSong.albumId?.let { albumId ->
						backStack.remove(Screen.NowPlaying)
						backStack.add(Screen.CollectionDetail(albumId, ""))
					}
				},
				onViewArtist = dropUnlessResumed {
					backStack.remove(Screen.NowPlaying)
					backStack.add(Screen.ArtistDetail(menuSong.artistId))
				},
				onShare = {
					shareId = menuSong.id
				},
				onAddToPlaylist = {
					playlistDialogShown = true
				},
				onTrackInfo = dropUnlessResumed {
					expanded = false
					backStack.add(Screen.SongDetailSheet(songId = menuSong.id, coverArtId = menuSong.coverArtId))
				},
				rating = rating,
				onSetRating = {
					rating = it
					scope.launch { runCatching { songRepository.rateSong(menuSong, it) } }
				},
				showSleepTimer = true,
				onSleepTimer = {
					expanded = false
					sleepTimerSheetShown = true
				},
				showPlaybackSpeed = true,
				onPlaybackSpeed = {
					expanded = false
					backStack.add(Screen.PlaybackSpeed)
				}
			)
		}
	}

	if (sleepTimerSheetShown) {
		LoakTheme(colorScheme) {
			SleepTimerSheet(
				onDismissRequest = { sleepTimerSheetShown = false }
			)
		}
	}

	if (playlistDialogShown && menuSong != null) {
		LoakTheme(colorScheme) {
			PlaylistUpdateDialog(
				songs = persistentListOf(menuSong),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}

	LoakTheme(colorScheme) {
		ShareDialog(
			id = shareId,
			onIdClear = { shareId = null },
			expiry = shareExpiry,
			onExpiryChange = { shareExpiry = it }
		)
	}
}
