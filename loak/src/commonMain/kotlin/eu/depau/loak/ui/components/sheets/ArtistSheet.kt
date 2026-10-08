package eu.depau.loak.ui.components.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_to_playlist
import eu.depau.loak.generated.resources.action_add_to_queue
import eu.depau.loak.generated.resources.action_instant_mix
import eu.depau.loak.generated.resources.action_play_next
import eu.depau.loak.generated.resources.action_remove_star
import eu.depau.loak.generated.resources.action_star
import eu.depau.loak.generated.resources.count_albums
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.InstantMix
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.icons.outlined.Queue
import eu.depau.loak.icons.outlined.QueuePlayNext
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.dialogs.LinkConfirmationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistSheet(
	onDismissRequest: () -> Unit,
	artist: DomainArtist,
	onPlayNext: (() -> Unit)? = null,
	onAddToQueue: (() -> Unit)? = null,
	onAddAllToPlaylist: (() -> Unit)? = null,
	starred: Boolean? = null,
	onSetStarred: ((Boolean) -> Unit)? = null,
	downloadStatus: DownloadStatus? = null,
	onDownloadAll: (() -> Unit)? = null,
	onCancelDownloadAll: (() -> Unit)? = null,
	onDeleteDownloadAll: (() -> Unit)? = null,
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val contentPadding = PaddingValues(horizontal = 16.dp)
	val colors = ListItemDefaults.colors(
		containerColor = Color.Transparent,
		trailingIconColor = MaterialTheme.colorScheme.onSurface,
		headlineColor = MaterialTheme.colorScheme.onSurface
	)
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }
	// offline, what needs the server is greyed out; adding to playlists is queued
	val online = LocalAvailability.current.online
	val playable = LocalAvailability.current.collection(artist.id)

	ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		menuOnWideWindows = true,
		contentWindowInsets = {
			BottomSheetDefaults.modalWindowInsets.add(
				WindowInsets(
					left = 8.dp,
					right = 8.dp
				)
			)
		}
	) {
		Spacer(Modifier.height(16.dp))

		ListItem(
			leadingContent = {
				CoverArt(
					coverArtId = artist.coverArtId,
					modifier = Modifier.size(50.dp),
					shape = preferenceManager.coverArtShape.decreasedShape
				)
			},
			headlineContent = { MarqueeText(artist.name) },
			supportingContent = {
				Text(
					text = artist.albumCount.let {
						pluralStringResource(Res.plurals.count_albums, it, it)
					}
				)
			},
			colors = colors
		)

		SheetActionBar(
			listOfNotNull(
				onPlayNext?.let { playNext ->
					SheetAction(
						stringResource(Res.string.action_play_next),
						Icons.Outlined.QueuePlayNext,
						{ playNext(); onDismissRequest() },
						enabled = playable
					)
				},
				onAddToQueue?.let { addToQueue ->
					SheetAction(
						stringResource(Res.string.action_add_to_queue),
						Icons.Outlined.Queue,
						{ addToQueue(); onDismissRequest() },
						enabled = playable
					)
				},
				if (starred != null && onSetStarred != null) SheetAction(
					stringResource(if (starred) Res.string.action_remove_star else Res.string.action_star),
					if (starred) Icons.Filled.Star else Icons.Outlined.Star,
					{ onSetStarred(!starred); onDismissRequest() },
					checked = starred
				) else null,
				downloadAction(
					downloadStatus,
					onDownload = onDownloadAll,
					onCancel = onCancelDownloadAll,
					onDelete = onDeleteDownloadAll,
					onDismissRequest = onDismissRequest
				)
			)
		)

		Column(Modifier.verticalScroll(rememberScrollState())) {
			if (onAddAllToPlaylist != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_add_to_playlist)) },
					leadingContent = { Icon(Icons.Outlined.PlaylistAdd, null) },
					onClick = {
						onAddAllToPlaylist()
						onDismissRequest()
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}

			ListItem(
				content = { Text(stringResource(Res.string.action_instant_mix)) },
				leadingContent = { Icon(Icons.Outlined.InstantMix, null) },
				onClick = {
					player.playInstantMix(artist.id, artist.name)
					onDismissRequest()
				},
				enabled = online,
				colors = colors,
				contentPadding = contentPadding
			)

			SheetLinks(
				lastFmUrl = artist.lastFmUrl,
				musicBrainzUrl = artist.musicBrainzId?.let { "https://musicbrainz.org/artist/$it" },
				onOpen = { linkToOpen = it }
			)
		}
	}

	if (linkToOpen != null) {
		LinkConfirmationDialog(
			linkToOpen = linkToOpen!!,
			onDismissRequest = { linkToOpen = null }
		)
	}
}
