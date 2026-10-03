package eu.depau.loak.ui.screens.collection.components

import eu.depau.loak.ui.components.common.displayName
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toPersistentList
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_more
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainAlbumInfo
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.MoreVert
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.TopBarButton
import eu.depau.loak.ui.components.sheets.CollectionSheet
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.playlist.dialogs.PlaylistUpdateDialog

@Composable
fun CollectionDetailScreenTopBar(
	collection: DomainSongCollection?,
	albumInfoState: UiState<DomainAlbumInfo>,
	titleAlpha: Float,
	onSetShareId: (shareId: String?) -> Unit,
	onDownloadAll: () -> Unit,
	onCancelDownloadAll: () -> Unit,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	downloadStatus: DownloadStatus,
	rating: Int?,
	onSetRating: ((Int) -> Unit)?,
	starred: Boolean?,
	onSetStarred: ((Boolean) -> Unit)? = null,
	refreshCollection: () -> Unit
) {
	var playlistDialogShown by rememberSaveable { mutableStateOf(false) }
	val backStack = LocalNavStack.current

	NestedTopBar(
		title = {
			Text(
				text = (collection as? DomainPlaylist)?.displayName()?.display ?: collection?.name.orEmpty(),
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.alpha(titleAlpha)
			)
		},
		actions = {
			Box {
				var expanded by rememberSaveable { mutableStateOf(false) }
				TopBarButton(onClick = {
					expanded = true
					refreshCollection()
				}) {
					Icon(
						Icons.Outlined.MoreVert,
						stringResource(Res.string.action_more)
					)
				}
				if (expanded) {
					CollectionSheet(
						onDismissRequest = { expanded = false },
						collection = collection,
						albumInfo = (albumInfoState as? UiState.Success)?.data,
						onDownloadAll = onDownloadAll,
						onCancelDownloadAll = onCancelDownloadAll,
						downloadStatus = downloadStatus,
						onShare = { onSetShareId(collection?.id) },
						onPlayNext = onPlayNext,
						onAddToQueue = onAddToQueue,
						onAddAllToPlaylist = { playlistDialogShown = true },
						onViewArtist =
							if (collection is DomainAlbum)
								dropUnlessResumed { backStack.add(Screen.ArtistDetail(collection.artistId)) }
							else null,
						rating = rating,
						onSetRating = onSetRating,
						starred = starred,
						onSetStarred = if (onSetStarred != null && starred != null) {
							{ onSetStarred(!starred) }
						} else null
					)
				}
			}
		}
	)

	if (playlistDialogShown) {
		PlaylistUpdateDialog(
			songs = collection?.songs.orEmpty().toPersistentList(),
			playlistToExclude = (collection as? DomainPlaylist)?.id,
			onDismissRequest = { playlistDialogShown = false }
		)
	}
}
