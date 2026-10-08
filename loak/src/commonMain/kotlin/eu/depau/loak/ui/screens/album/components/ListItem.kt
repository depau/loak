package eu.depau.loak.ui.screens.album.components

import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.unavailable
import eu.depau.loak.ui.util.onSecondaryClick
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_deleted_download
import eu.depau.loak.generated.resources.notice_download_started
import org.koin.compose.koinInject
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.sheets.CollectionSheet
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.playlist.dialogs.PlaylistUpdateDialog
import eu.depau.loak.ui.util.appendBulletPoint
import eu.depau.loak.ui.theme.SmallCoverArtShape

@Composable
fun AlbumListScreenListItem(
	modifier: Modifier = Modifier,
	album: DomainAlbum,
	selected: Boolean,
	starred: Boolean,
	rating: Int,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onSetStarred: (starred: Boolean) -> Unit,
	onSetShareId: (String) -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onSetRating: (Int) -> Unit
) {
	val backStack = LocalNavStack.current
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }

	val downloadManager = koinInject<DownloadManager>()
	val downloadStatus by downloadManager
		.getCollectionDownloadStatus(album.songs.map { it.id })
		.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)

	Box(modifier) {
		ListItem(
			modifier = Modifier
				.unavailable(!LocalAvailability.current.collection(album.id))
				.onSecondaryClick(onSelect),
			leadingContent = {
				CoverArt(
					coverArtId = album.coverArtId,
					modifier = Modifier.size(50.dp),
					shape = SmallCoverArtShape
				)
			},
			content = { MarqueeText(album.name ?: "[unknown album]") },
			supportingContent = {
				MarqueeText(
					buildAnnotatedString {
						append(album.artistName)
						album.year?.let {
							appendBulletPoint()
							append("$it")
						}
					}
				)
			},
			onClick = dropUnlessResumed {
				scope.launch {
					backStack.add(Screen.CollectionDetail(album.id, ""))
				}
			},
			onLongClick = onSelect
		)
		if (selected) {
			CollectionSheet(
				onDismissRequest = onDeselect,
				collection = album,
				onShare = { onSetShareId(album.id) },
				onPlayNext = onPlayNext,
				onAddToQueue = onAddToQueue,
				downloadStatus = downloadStatus,
				onDownloadAll = {
					scope.launch {
						downloadManager.downloadCollection(album)
						snackBarManager.notify(Res.string.notice_download_started)
					}
				},
				onCancelDownloadAll = {
					scope.launch {
						album.songs.forEach { downloadManager.cancelDownload(it.id) }
					}
				},
				onDeleteDownloadAll = {
					scope.launch {
						downloadManager.deleteDownloadedCollection(album)
						snackBarManager.notify(Res.string.notice_deleted_download)
					}
				},
				starred = starred,
				onSetStarred = onSetStarred,
				onAddAllToPlaylist = { playlistDialogShown = true },
				onViewArtist = dropUnlessResumed {
					backStack.add(Screen.ArtistDetail(album.artistId))
				},
				rating = rating,
				onSetRating = onSetRating
			)
		}

		if (playlistDialogShown) {
			PlaylistUpdateDialog(
				songs = album.songs.toPersistentList(),
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}
}
