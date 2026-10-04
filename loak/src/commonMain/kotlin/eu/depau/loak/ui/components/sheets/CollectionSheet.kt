package eu.depau.loak.ui.components.sheets

import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.icons.outlined.Flask
import eu.depau.loak.domain.repositories.AlchemyIngredient
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.generated.resources.action_use_in_alchemy
import androidx.compose.runtime.collectAsState
import eu.depau.loak.generated.resources.action_make_a_copy
import eu.depau.loak.icons.outlined.Copy
import eu.depau.loak.ui.screens.playlist.dialogs.CopyPlaylistDialog
import eu.depau.loak.domain.models.PlaylistKind
import eu.depau.loak.ui.components.common.PlaylistBadgedText
import eu.depau.loak.ui.components.common.displayName
import eu.depau.loak.domain.repositories.HomeRepository
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
import eu.depau.loak.generated.resources.action_instant_mix
import eu.depau.loak.generated.resources.action_cancel_download
import eu.depau.loak.generated.resources.action_delete
import eu.depau.loak.generated.resources.action_delete_download
import eu.depau.loak.generated.resources.action_download
import eu.depau.loak.generated.resources.action_play_next
import eu.depau.loak.generated.resources.action_remove_star
import eu.depau.loak.generated.resources.action_share
import eu.depau.loak.generated.resources.action_star
import eu.depau.loak.generated.resources.action_view_artist
import eu.depau.loak.generated.resources.action_view_on_lastfm
import eu.depau.loak.generated.resources.action_view_on_musicbrainz
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.info_click_to_retry
import eu.depau.loak.generated.resources.info_download_failed
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.canUserShare
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainAlbumInfo
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.icons.Icons
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import eu.depau.loak.domain.repositories.DbRepository
import eu.depau.loak.ui.screens.playlist.dialogs.EditPlaylistSheet
import eu.depau.loak.generated.resources.action_edit_playlist
import eu.depau.loak.icons.outlined.Edit
import eu.depau.loak.icons.brand.Lastfm
import eu.depau.loak.icons.brand.Musicbrainz
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.Artist
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Delete
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.InstantMix
import eu.depau.loak.icons.outlined.DownloadOff
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.icons.outlined.PlaylistRemove
import eu.depau.loak.icons.outlined.Queue
import eu.depau.loak.icons.outlined.QueuePlayNext
import eu.depau.loak.icons.outlined.Share
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.common.RatingRow
import eu.depau.loak.ui.components.dialogs.LinkConfirmationDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionSheet(
	onDismissRequest: () -> Unit,
	collection: DomainSongCollection?,
	albumInfo: DomainAlbumInfo? = null,
	onDownloadAll: (() -> Unit)? = null,
	onCancelDownloadAll: (() -> Unit)? = null,
	onDeleteDownloadAll: (() -> Unit)? = null,
	downloadStatus: DownloadStatus? = null,
	onShare: (() -> Unit)? = null,
	onPlayNext: (() -> Unit)? = null,
	onAddToQueue: (() -> Unit)? = null,
	onAddAllToPlaylist: (() -> Unit)? = null,
	onViewArtist: (() -> Unit)? = null,
	starred: Boolean? = null,
	onSetStarred: ((Boolean) -> Unit)? = null,
	onDelete: (() -> Unit)? = null,
	rating: Int? = null,
	onSetRating: ((Int) -> Unit)? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val sessionManager = koinInject<SessionManager>()
	val dbRepository = koinInject<DbRepository>()
	val player = koinInject<MediaPlayerViewModel>()
	val scope = rememberCoroutineScope()

	val contentPadding = PaddingValues(horizontal = 16.dp)
	val colors = ListItemDefaults.colors(
		containerColor = Color.Transparent,
		trailingIconColor = MaterialTheme.colorScheme.onSurface,
		headlineColor = MaterialTheme.colorScheme.onSurface
	)
	var linkToOpen by rememberSaveable { mutableStateOf<String?>(null) }
	var editing by rememberSaveable { mutableStateOf(false) }
	var copying by rememberSaveable { mutableStateOf(false) }
	// only the owner can rename it; playlists shared by others are read-only
	val editablePlaylist = (collection as? DomainPlaylist)
		?.takeIf { it.owner == sessionManager.username }
	val playlistName = (collection as? DomainPlaylist)?.displayName()
	val audioMuseInfo by koinInject<AudioMuseManager>().info.collectAsState()
	val backStack = LocalNavStack.current
	// offline, what needs the server is greyed out (playlist edits too, for now);
	// starring, rating, downloading and deleting are queued
	val online = LocalAvailability.current.online
	val playable = collection != null && LocalAvailability.current.collection(collection.id)

	// the edit sheet replaces this one rather than stacking on top
	if (!editing && !copying) ModalBottomSheet(
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
					coverArtId = collection?.coverArtId,
					modifier = Modifier.size(50.dp),
					shape = preferenceManager.coverArtShape.decreasedShape
				)
			},
			headlineContent = { MarqueeText(playlistName?.display ?: collection?.name.orEmpty()) },
			supportingContent = {
				PlaylistBadgedText(playlistName?.kind ?: PlaylistKind.Regular) {
				MarqueeText(
					listOfNotNull(
						(collection as? DomainAlbum)?.artistName,
						(collection as? DomainPlaylist)?.comment,
						(collection as? DomainAlbum)?.genre,
						(collection as? DomainAlbum)?.year,
						collection?.songCount?.let {
							pluralStringResource(Res.plurals.count_songs, it, it)
						}
					).joinToString(" • ")
				)
				}
			},
			colors = colors
		)
		if (rating != null && onSetRating != null && preferenceManager.enableRatings) {
			RatingRow(
				rating = rating,
				setRating = onSetRating
			)
			Spacer(Modifier.height(14.dp))
		}

		HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 2.dp))

		Column(Modifier.verticalScroll(rememberScrollState())) {
			if (onPlayNext != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_play_next)) },
					leadingContent = { Icon(Icons.Outlined.QueuePlayNext, null) },
					onClick = {
						onPlayNext()
						onDismissRequest()
					},
					colors = colors,
					contentPadding = contentPadding,
					enabled = !collection?.songs.isNullOrEmpty() && playable
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
					contentPadding = contentPadding,
					enabled = !collection?.songs.isNullOrEmpty() && playable
				)
			}

			// getSimilarSongs takes album ids, not playlist ones
			if (collection is DomainAlbum) {
				ListItem(
					content = { Text(stringResource(Res.string.action_instant_mix)) },
					leadingContent = { Icon(Icons.Outlined.InstantMix, null) },
					onClick = {
						player.playInstantMix(collection.id, collection.name ?: "")
						onDismissRequest()
					},
					enabled = online,
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
					contentPadding = contentPadding,
					enabled = !collection?.songs.isNullOrEmpty() && online
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

			collection?.let { HomeRepository.keyOf(it) }?.let { key ->
				SpeedDialPinItem(key, colors, contentPadding, onDismissRequest)
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
					contentPadding = contentPadding,
					enabled = !collection?.songs.isNullOrEmpty()
				)
			}

			if (onViewArtist != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_view_artist)) },
					leadingContent = { Icon(Icons.Outlined.Artist, null) },
					onClick = {
						onViewArtist()
						onDismissRequest()
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}

			if (editablePlaylist != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_edit_playlist)) },
					leadingContent = { Icon(Icons.Outlined.Edit, null) },
					onClick = { editing = true },
					enabled = online,
					colors = colors,
					contentPadding = contentPadding
				)
			}

			if (collection is DomainPlaylist && audioMuseInfo != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_use_in_alchemy)) },
					leadingContent = { Icon(Icons.Outlined.Flask, null) },
					onClick = {
						backStack.add(Screen.Alchemy(AlchemyIngredient(collection.id, AlchemyIngredient.Type.Playlist, playlistName?.display ?: collection.name.orEmpty())))
						onDismissRequest()
					},
					enabled = online,
					colors = colors,
					contentPadding = contentPadding
				)
			}

			if (collection is DomainPlaylist) {
				ListItem(
					content = { Text(stringResource(Res.string.action_make_a_copy)) },
					leadingContent = { Icon(Icons.Outlined.Copy, null) },
					onClick = { copying = true },
					enabled = online,
					colors = colors,
					contentPadding = contentPadding
				)
			}

			if (onShare != null && sessionManager.canUserShare()) {
				ListItem(
					content = { Text(stringResource(Res.string.action_share)) },
					leadingContent = { Icon(Icons.Outlined.Share, null) },
					onClick = {
						onShare()
						onDismissRequest()
					},
					enabled = online,
					colors = colors,
					contentPadding = contentPadding
				)
			}

			// only the owner can delete it; the server refuses anyone else
			if (onDelete != null && (collection !is DomainPlaylist || collection.owner == sessionManager.username)) {
				ListItem(
					content = { Text(stringResource(Res.string.action_delete)) },
					leadingContent = { Icon(Icons.Outlined.PlaylistRemove, null) },
					onClick = {
						onDelete()
						onDismissRequest()
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}

			if (albumInfo?.lastFmUrl != null || albumInfo?.musicBrainzId != null) {
				HorizontalDivider(Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
			}

			if (albumInfo?.lastFmUrl != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_view_on_lastfm)) },
					leadingContent = { Icon(Icons.Brand.Lastfm, null) },
					onClick = {
						linkToOpen = albumInfo.lastFmUrl
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}

			if (albumInfo?.musicBrainzId != null) {
				ListItem(
					content = { Text(stringResource(Res.string.action_view_on_musicbrainz)) },
					leadingContent = { Icon(Icons.Brand.Musicbrainz, null) },
					onClick = {
						linkToOpen = "https://musicbrainz.org/release/${albumInfo.musicBrainzId}"
					},
					colors = colors,
					contentPadding = contentPadding
				)
			}
		}
	}

	if (editing && editablePlaylist != null) {
		EditPlaylistSheet(
			playlist = editablePlaylist,
			onDismissRequest = {
				editing = false
				onDismissRequest()
			},
			// pull the new name into the local cache the lists read from
			onSaved = { scope.launch { dbRepository.syncPlaylists() } }
		)
	}

	if (copying && collection is DomainPlaylist) {
		CopyPlaylistDialog(
			playlist = collection,
			onDismissRequest = {
				copying = false
				onDismissRequest()
			}
		)
	}

	if (linkToOpen != null) {
		LinkConfirmationDialog(
			linkToOpen = linkToOpen!!,
			onDismissRequest = { linkToOpen = null }
		)
	}
}
