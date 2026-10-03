package eu.depau.loak.ui.screens.playlist.components

import eu.depau.loak.ui.components.common.SpecialPlaylistCover
import eu.depau.loak.domain.models.PlaylistKind
import eu.depau.loak.ui.components.common.PlaylistBadge
import eu.depau.loak.ui.components.common.displayName
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.notice_deleted_download
import eu.depau.loak.generated.resources.notice_download_started
import org.jetbrains.compose.resources.pluralStringResource
import org.koin.compose.koinInject
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.ui.components.layouts.ArtGridItem
import eu.depau.loak.ui.components.sheets.CollectionSheet
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.playlist.dialogs.PlaylistUpdateDialog

@Composable
fun PlaylistListScreenGridItem(
	modifier: Modifier = Modifier,
	tab: String,
	playlist: DomainPlaylist,
	selected: Boolean,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onSelect: () -> Unit,
	onDeselect: () -> Unit,
	onSetShareId: (String) -> Unit,
	onSetDeletionId: (String) -> Unit
) {
	val backStack = LocalNavStack.current
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()

	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	val downloadManager = koinInject<DownloadManager>()
	val downloadStatus by downloadManager
		.getCollectionDownloadStatus(playlist.songs.map { it.id })
		.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)

	val name = playlist.displayName()

	Box(modifier) {
		ArtGridItem(
			onClick = dropUnlessResumed {
				scope.launch {
					backStack.add(Screen.CollectionDetail(playlist.id, tab))
				}
			},
			onLongClick = onSelect,
			coverArtId = playlist.coverArtId,
			title = name.display.ifEmpty { "[unknown playlist]" },
			subtitle = buildString {
				append(
					pluralStringResource(
						Res.plurals.count_songs,
						playlist.songCount,
						playlist.songCount
					)
				)
				playlist.comment?.let {
					append("\n${playlist.comment}\n")
				}
			},
			coverOverlay = { SpecialPlaylistCover(name, Modifier.matchParentSize()) },
			badge = if (name.kind == PlaylistKind.Regular) null else ({ PlaylistBadge(name.kind) }),
			id = playlist.id,
			tab = tab
		)
		if (selected) {
			CollectionSheet(
				onDismissRequest = onDeselect,
				collection = playlist,
				onShare = { onSetShareId(playlist.id) },
				onDelete = { onSetDeletionId(playlist.id) },
				onPlayNext = onPlayNext,
				onAddToQueue = onAddToQueue,
				onAddAllToPlaylist = { playlistDialogShown = true },
				downloadStatus = downloadStatus,
				onDownloadAll = {
					scope.launch {
						downloadManager.downloadCollection(playlist)
						snackBarManager.notify(Res.string.notice_download_started)
					}
				},
				onCancelDownloadAll = {
					scope.launch {
						playlist.songs.forEach { downloadManager.cancelDownload(it.id) }
					}
				},
				onDeleteDownloadAll = {
					scope.launch {
						downloadManager.deleteDownloadedCollection(playlist)
						snackBarManager.notify(Res.string.notice_deleted_download)
					}
				}
			)
		}

		if (playlistDialogShown) {
			PlaylistUpdateDialog(
				songs = playlist.songs.toPersistentList(),
				playlistToExclude = playlist.id,
				onDismissRequest = { playlistDialogShown = false }
			)
		}
	}
}
