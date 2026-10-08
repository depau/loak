package eu.depau.loak.ui.components.sheets

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
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_deleted_download
import eu.depau.loak.generated.resources.notice_download_started
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import kotlin.time.Duration

/**
 * The [SongSheet] a song row opens on long-press, with every action a song has, the same in
 * every list, while [open]. It looks up the song's star, rating and download itself, and stays
 * composed while closed for the playlist and share dialogs its actions open.
 *
 * @param collection the album or playlist the song is listed in: no "View album" on its own
 * album, and its playlist isn't offered to add the song to.
 * @param canPlayNext false where it makes no sense (the playing song).
 * @param canAddToQueue false for songs already in the queue.
 * @param inPlayer the sheet is opened from the player: track info opens over it, and going to
 * the album or artist closes it first.
 * @param onStarredChange after starring or unstarring, for lists that filter by it.
 */
@Composable
fun SongActionsSheet(
	song: DomainSong,
	open: Boolean,
	onDismissRequest: () -> Unit,
	collection: DomainSongCollection? = null,
	canPlayNext: Boolean = true,
	canAddToQueue: Boolean = true,
	onRemoveFromPlaylist: (() -> Unit)? = null,
	onRemoveFromQueue: (() -> Unit)? = null,
	inPlayer: Boolean = false,
	onStarredChange: (() -> Unit)? = null
) {
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	var shareId by remember { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	if (open) {
		Sheet(
			song = song,
			onDismissRequest = onDismissRequest,
			collection = collection,
			canPlayNext = canPlayNext,
			canAddToQueue = canAddToQueue,
			onRemoveFromPlaylist = onRemoveFromPlaylist,
			onRemoveFromQueue = onRemoveFromQueue,
			inPlayer = inPlayer,
			onStarredChange = onStarredChange,
			onAddToPlaylist = { playlistDialogShown = true },
			onShare = { shareId = song.id }
		)
	}

	if (playlistDialogShown) {
		PlaylistUpdateDialog(
			songs = persistentListOf(song),
			playlistToExclude = (collection as? DomainPlaylist)?.id,
			onDismissRequest = { playlistDialogShown = false }
		)
	}

	if (shareId != null) {
		ShareDialog(
			id = shareId,
			onIdClear = { shareId = null },
			expiry = shareExpiry,
			onExpiryChange = { shareExpiry = it }
		)
	}
}

@Composable
private fun Sheet(
	song: DomainSong,
	onDismissRequest: () -> Unit,
	collection: DomainSongCollection?,
	canPlayNext: Boolean,
	canAddToQueue: Boolean,
	onRemoveFromPlaylist: (() -> Unit)?,
	onRemoveFromQueue: (() -> Unit)?,
	inPlayer: Boolean,
	onStarredChange: (() -> Unit)?,
	onAddToPlaylist: () -> Unit,
	onShare: () -> Unit
) {
	val backStack = LocalNavStack.current
	val player = koinInject<MediaPlayerViewModel>()
	val songRepository = koinInject<SongRepository>()
	val downloadManager = koinInject<DownloadManager>()
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()

	val starred by remember(song.id) { songRepository.observeSongStarred(song.id) }
		.collectAsState(song.starredAt != null)
	var rating by remember(song.id) { mutableIntStateOf(song.userRating ?: 0) }
	LaunchedEffect(song.id) { rating = songRepository.getSongRating(song) }
	val downloads by downloadManager.allDownloads.collectAsState(emptyList())

	val navigate = { screen: Screen ->
		if (inPlayer) backStack.remove(Screen.NowPlaying)
		backStack.add(screen)
	}

	SongSheet(
		onDismissRequest = onDismissRequest,
		song = song,
		collection = collection,
		starred = starred,
		onSetStarred = {
			scope.launch {
				songRepository.setSongStarred(song.id, it)
				onStarredChange?.invoke()
			}
		},
		rating = rating,
		onSetRating = {
			rating = it
			scope.launch { songRepository.rateSong(song, it) }
		},
		onShare = onShare,
		onPlayNext = if (canPlayNext) ({ player.playNextSingle(song) }) else null,
		onAddToQueue = if (canAddToQueue) ({ player.addToQueueSingle(song) }) else null,
		onRemoveFromQueue = onRemoveFromQueue,
		onRemoveFromPlaylist = onRemoveFromPlaylist,
		onAddToPlaylist = onAddToPlaylist,
		downloadStatus = downloads.find { it.songId == song.id }?.status,
		onDownload = {
			downloadManager.downloadSong(song)
			snackBarManager.notify(Res.string.notice_download_started)
		},
		onCancelDownload = { downloadManager.cancelDownload(song.id) },
		onDeleteDownload = {
			downloadManager.deleteDownload(song.id)
			snackBarManager.notify(Res.string.notice_deleted_download)
		},
		onTrackInfo = dropUnlessResumed {
			backStack.add(
				if (inPlayer) Screen.SongDetailSheet(song.id, song.coverArtId)
				else Screen.SongDetailScreen(song.id, song.coverArtId)
			)
		},
		onViewAlbum = song.albumId
			?.takeIf { collection !is DomainAlbum }
			?.let { dropUnlessResumed { navigate(Screen.CollectionDetail(it, "library")) } }
	)
}
