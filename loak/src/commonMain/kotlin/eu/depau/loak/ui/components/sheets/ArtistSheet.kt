package eu.depau.loak.ui.components.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import eu.depau.loak.generated.resources.action_cancel_download
import eu.depau.loak.generated.resources.action_delete_download
import eu.depau.loak.generated.resources.action_download
import eu.depau.loak.generated.resources.action_play_next
import eu.depau.loak.generated.resources.action_remove_star
import eu.depau.loak.generated.resources.action_star
import eu.depau.loak.generated.resources.action_view_on_lastfm
import eu.depau.loak.generated.resources.action_view_on_musicbrainz
import eu.depau.loak.generated.resources.count_albums
import eu.depau.loak.generated.resources.info_click_to_retry
import eu.depau.loak.generated.resources.info_download_failed
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.brand.Lastfm
import eu.depau.loak.icons.brand.Musicbrainz
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Delete
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.DownloadOff
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.icons.outlined.Queue
import eu.depau.loak.icons.outlined.QueuePlayNext
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.ui.components.common.CoverArt
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
	val contentPadding = PaddingValues(horizontal = 16.dp)
	val colors = ListItemDefaults.colors(
		containerColor = Color.Transparent,
		trailingIconColor = MaterialTheme.colorScheme.onSurface,
		headlineColor = MaterialTheme.colorScheme.onSurface
	)
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }

	ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		dragHandle = null,
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
			content = { MarqueeText(artist.name) },
			supportingContent = {
				Text(
					text = artist.albumCount.let {
						pluralStringResource(Res.plurals.count_albums, it, it)
					}
				)
			},
			colors = colors
		)

		HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 2.dp))

		Column(Modifier.verticalScroll(rememberScrollState())) {
			if (artist.lastFmUrl != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_view_on_lastfm)) },
					leadingContent = { Icon(Icons.Brand.Lastfm, null) },
					onClick = {
						linkToOpen = artist.lastFmUrl
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}

			if (artist.musicBrainzId != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_view_on_musicbrainz)) },
					leadingContent = { Icon(Icons.Brand.Musicbrainz, null) },
					onClick = {
						linkToOpen = "https://musicbrainz.org/artist/${artist.musicBrainzId}"
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}

			if (onPlayNext != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_play_next)) },
					leadingContent = { Icon(Icons.Outlined.QueuePlayNext, null) },
					onClick = {
						onPlayNext()
						onDismissRequest()
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}

			if (onAddToQueue != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_add_to_queue)) },
					leadingContent = { Icon(Icons.Outlined.Queue, null) },
					onClick = {
						onAddToQueue()
						onDismissRequest()
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}

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

			if (starred != null && onSetStarred != null) {
				ListItem(
					content = {
						Text(stringResource(if (starred) Res.string.action_remove_star else Res.string.action_star))
					},
					leadingContent = {
						Icon(if (starred) Icons.Filled.Star else Icons.Outlined.Star, null)
					},
					onClick = {
						onSetStarred(!starred)
						onDismissRequest()
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}

			if (downloadStatus != null) {
				when (downloadStatus) {
					DownloadStatus.DOWNLOADING -> {
						ListItem(
							content = { Text(stringResource(Res.string.action_cancel_download)) },
							leadingContent = { Icon(Icons.Outlined.Close, null) },
							onClick = {
								onCancelDownloadAll?.invoke()
								onDismissRequest()
							},
							colors = colors,
							contentPadding = contentPadding
						)
					}

					DownloadStatus.DOWNLOADED -> {
						ListItem(
							content = { Text(stringResource(Res.string.action_delete_download)) },
							leadingContent = { Icon(Icons.Outlined.Delete, null) },
							onClick = {
								onDeleteDownloadAll?.invoke()
								onDismissRequest()
							},
							colors = colors,
							contentPadding = contentPadding
						)
					}

					DownloadStatus.FAILED -> {
						ListItem(
							content = {
								Text(
									text = stringResource(Res.string.info_download_failed),
									color = MaterialTheme.colorScheme.error
								)
							},
							supportingContent = {
								Text(
									text = stringResource(Res.string.info_click_to_retry),
									color = MaterialTheme.colorScheme.error,
									style = MaterialTheme.typography.labelSmall
								)
							},
							leadingContent = {
								Icon(
									Icons.Outlined.DownloadOff,
									null,
									tint = MaterialTheme.colorScheme.error
								)
							},
							onClick = {
								onDownloadAll?.invoke()
								onDismissRequest()
							},
							colors = colors,
							contentPadding = contentPadding
						)
					}

					else -> {
						ListItem(
							content = { Text(stringResource(Res.string.action_download)) },
							leadingContent = { Icon(Icons.Outlined.Download, null) },
							onClick = {
								onDownloadAll?.invoke()
								onDismissRequest()
							},
							colors = colors,
							contentPadding = contentPadding
						)
					}
				}
			} else if (onDownloadAll != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_download)) },
					leadingContent = { Icon(Icons.Outlined.Download, null) },
					onClick = {
						onDownloadAll()
						onDismissRequest()
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}
		}
	}

	if (linkToOpen != null) {
		LinkConfirmationDialog(
			linkToOpen = linkToOpen!!,
			onDismissRequest = { linkToOpen = null }
		)
	}
}
