package eu.depau.loak.ui.screens.collection.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.persistentListOf
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.ui.components.sheets.SongSheet
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.playlist.dialogs.PlaylistUpdateDialog

@Composable
fun CollectionDetailScreenSongRowDropdown(
	expanded: Boolean,
	onDismissRequest: () -> Unit,
	onRemoveStar: () -> Unit,
	onAddStar: () -> Unit,
	onShare: () -> Unit,
	collection: DomainSongCollection,
	song: DomainSong,
	onRemoveFromPlaylist: () -> Unit,
	starred: Boolean,
	downloadStatus: DownloadStatus?,
	onDownload: () -> Unit,
	onCancelDownload: () -> Unit,
	onDeleteDownload: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	rating: Int,
	onSetRating: (Int) -> Unit
) {
	val backStack = LocalNavStack.current
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }

	if (expanded) {
		SongSheet(
			onDismissRequest = onDismissRequest,
			song = song,
			collection = collection,
			starred = starred,
			onSetStarred = { starred ->
				if (starred) onAddStar() else onRemoveStar()
			},
			onShare = onShare,
			onPlayNext = onPlayNext,
			onAddToQueue = onAddToQueue,
			onTrackInfo = dropUnlessResumed {
				backStack.add(Screen.SongDetailScreen(song.id, song.coverArtId))
			},
			onViewAlbum = if (collection !is DomainAlbum && song.albumId != null) {
				dropUnlessResumed {
					backStack.add(
						Screen.CollectionDetail(
							collectionId = song.albumId,
							tab = "library"
						)
					)
				}
			} else null,
			onViewArtist = dropUnlessResumed {
				backStack.add(Screen.ArtistDetail(song.artistId))
			},
			onAddToPlaylist = {
				playlistDialogShown = true
			},
			onRemoveFromPlaylist = onRemoveFromPlaylist,
			downloadStatus = downloadStatus,
			onDownload = onDownload,
			onCancelDownload = onCancelDownload,
			onDeleteDownload = onDeleteDownload,
			rating = rating,
			onSetRating = onSetRating
		)
	}

	if (playlistDialogShown) {
		PlaylistUpdateDialog(
			songs = persistentListOf(song),
			playlistToExclude = if (collection is DomainPlaylist)
				collection.id
			else null,
			onDismissRequest = { playlistDialogShown = false }
		)
	}
}
