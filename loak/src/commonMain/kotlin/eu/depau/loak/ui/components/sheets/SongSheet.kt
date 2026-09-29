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
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_to_another_playlist
import eu.depau.loak.generated.resources.action_add_to_playlist
import eu.depau.loak.generated.resources.action_add_to_queue
import eu.depau.loak.generated.resources.action_cancel_download
import eu.depau.loak.generated.resources.action_delete_download
import eu.depau.loak.generated.resources.action_download
import eu.depau.loak.generated.resources.action_play_next
import eu.depau.loak.generated.resources.action_remove_from_playlist
import eu.depau.loak.generated.resources.action_remove_from_queue
import eu.depau.loak.generated.resources.action_remove_star
import eu.depau.loak.generated.resources.action_share
import eu.depau.loak.generated.resources.action_sleep_timer
import eu.depau.loak.generated.resources.action_sleep_timer_enabled
import eu.depau.loak.generated.resources.action_sleep_timer_queue_enabled
import eu.depau.loak.generated.resources.action_sleep_timer_songs_enabled
import eu.depau.loak.generated.resources.action_star
import eu.depau.loak.generated.resources.action_track_info
import eu.depau.loak.generated.resources.action_view_album
import eu.depau.loak.generated.resources.action_view_artist
import eu.depau.loak.generated.resources.info_click_to_retry
import eu.depau.loak.generated.resources.info_download_failed
import eu.depau.loak.generated.resources.option_playback_speed
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SleepTimerManager
import eu.depau.loak.domain.manager.SleepTimerMode
import eu.depau.loak.domain.manager.canUserShare
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.Album
import eu.depau.loak.icons.outlined.Artist
import eu.depau.loak.icons.outlined.Bedtime
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Delete
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.DownloadOff
import eu.depau.loak.icons.outlined.Info
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.icons.outlined.PlaylistRemove
import eu.depau.loak.icons.outlined.Queue
import eu.depau.loak.icons.outlined.QueuePlayNext
import eu.depau.loak.icons.outlined.Share
import eu.depau.loak.icons.outlined.Speed
import eu.depau.loak.icons.outlined.Star
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.common.RatingRow
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.theme.LoakTheme
import eu.depau.loak.ui.theme.positive
import eu.depau.loak.ui.util.InlineExplicitIcon
import eu.depau.loak.ui.util.buildSongInfoString
import eu.depau.loak.ui.util.label
import eu.depau.loak.ui.util.rememberColorSchemeFromCoverArt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongSheet(
	onDismissRequest: () -> Unit,
	song: DomainSong,
	collection: DomainSongCollection? = null,
	starred: Boolean? = null,
	onSetStarred: ((Boolean) -> Unit)? = null,
	onShare: (() -> Unit)? = null,
	onPlayNext: (() -> Unit)? = null,
	onAddToQueue: (() -> Unit)? = null,
	onTrackInfo: (() -> Unit)? = null,
	onViewAlbum: (() -> Unit)? = null,
	onViewArtist: (() -> Unit)? = null,
	onAddToPlaylist: (() -> Unit)? = null,
	onRemoveFromPlaylist: (() -> Unit)? = null,
	onRemoveFromQueue: (() -> Unit)? = null,
	downloadStatus: DownloadStatus? = null,
	onDownload: (() -> Unit)? = null,
	onCancelDownload: (() -> Unit)? = null,
	onDeleteDownload: (() -> Unit)? = null,
	rating: Int? = null,
	onSetRating: ((Int) -> Unit)? = null,
	showSleepTimer: Boolean = false,
	onSleepTimer: (() -> Unit)? = null,
	showPlaybackSpeed: Boolean = false,
	onPlaybackSpeed: (() -> Unit)? = null,
	useSongTheme: Boolean = true
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val sessionManager = koinInject<SessionManager>()

	val sleepTimerManager = koinInject<SleepTimerManager>()
	val sleepTimerMode by sleepTimerManager.mode.collectAsStateWithLifecycle()
	val contentPadding = PaddingValues(horizontal = 16.dp)

	val colorScheme = if (useSongTheme) rememberColorSchemeFromCoverArt(song.coverArtId) else null

	val backStack = LocalNavStack.current

	LoakTheme(colorScheme) {
		val colors = ListItemDefaults.colors(
			containerColor = Color.Transparent,
			trailingIconColor = MaterialTheme.colorScheme.onSurface,
			headlineColor = MaterialTheme.colorScheme.onSurface
		)
		ModalBottomSheet(
			onDismissRequest = onDismissRequest,
			menuOnWideWindows = true,
			containerColor = MaterialTheme.colorScheme.surface,
			sheetState = rememberBottomSheetState(
				initialValue = SheetValue.Hidden,
				enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
			),
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
				content = {
					MarqueeText(
						text = buildAnnotatedString {
							append(song.title)
							if (song.explicitStatus == DomainExplicitStatus.Explicit) {
								append(" ")
								appendInlineContent("InlineExplicitIcon")
							}
						},
						inlineContent = InlineExplicitIcon,
					)
				},
				supportingContent = {
					MarqueeText(
						buildSongInfoString(
							song = song,
							onClickArtist = {
								onDismissRequest()
								backStack.add(Screen.ArtistDetail(it))
							}
						)
					)
				},
				leadingContent = {
					CoverArt(
						coverArtId = song.coverArtId,
						modifier = Modifier.size(50.dp),
						shape = preferenceManager.coverArtShape.decreasedShape
					)
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

				if (onAddToPlaylist != null) {
					ListItem(
						content = {
							Text(
								stringResource(
									if (collection != null && collection !is DomainAlbum)
										Res.string.action_add_to_another_playlist
									else Res.string.action_add_to_playlist
								)
							)
						},
						leadingContent = { Icon(Icons.Outlined.PlaylistAdd, null) },
						onClick = {
							onAddToPlaylist()
							onDismissRequest()
						},
						colors = colors,
						contentPadding = contentPadding
					)
				}

				if (onRemoveFromPlaylist != null && collection != null && collection !is DomainAlbum) {
					ListItem(
						content = { Text(stringResource(Res.string.action_remove_from_playlist)) },
						leadingContent = { Icon(Icons.Outlined.PlaylistRemove, null) },
						onClick = {
							onRemoveFromPlaylist()
							onDismissRequest()
						},
						colors = colors,
						contentPadding = contentPadding
					)
				}

				if (onRemoveFromQueue != null) {
					ListItem(
						content = { Text(stringResource(Res.string.action_remove_from_queue)) },
						leadingContent = { Icon(Icons.Outlined.PlaylistRemove, null) },
						onClick = {
							onRemoveFromQueue()
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
									onCancelDownload?.invoke()
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
									onDeleteDownload?.invoke()
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
									onDownload?.invoke()
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
									onDownload?.invoke()
									onDismissRequest()
								},
								colors = colors,
								contentPadding = contentPadding
							)
						}
					}
				} else if (onDownload != null) {
					ListItem(
						content = { Text(stringResource(Res.string.action_download)) },
						leadingContent = { Icon(Icons.Outlined.Download, null) },
						onClick = {
							onDownload()
							onDismissRequest()
						},
						colors = colors,
						contentPadding = contentPadding
					)
				}

				if (onViewAlbum != null) {
					ListItem(
						content = {
							Text(stringResource(Res.string.action_view_album))
						},
						leadingContent = { Icon(Icons.Outlined.Album, null) },
						onClick = {
							onViewAlbum()
							onDismissRequest()
						},
						colors = colors,
						contentPadding = contentPadding
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

				if (onShare != null && sessionManager.canUserShare()) {
					ListItem(
						content = { Text(stringResource(Res.string.action_share)) },
						leadingContent = { Icon(Icons.Outlined.Share, null) },
						onClick = {
							onShare()
							onDismissRequest()
						},
						colors = colors,
						contentPadding = contentPadding
					)
				}

				if (showSleepTimer) {
					val isEnabled = sleepTimerMode !is SleepTimerMode.Disabled
					ListItem(
						content = {
							Text(
								text = when (val mode = sleepTimerMode) {
									is SleepTimerMode.Time -> stringResource(
										Res.string.action_sleep_timer_enabled,
										sleepTimerManager.timeLeft?.label() ?: ""
									)

									is SleepTimerMode.Songs -> stringResource(
										Res.string.action_sleep_timer_songs_enabled,
										mode.remaining
									)

									is SleepTimerMode.EndOfQueue -> stringResource(
										Res.string.action_sleep_timer_queue_enabled
									)

									else -> stringResource(Res.string.action_sleep_timer)
								},
								color = if (isEnabled) MaterialTheme.colorScheme.positive else Color.Unspecified
							)
						},
						leadingContent = {
							Icon(
								imageVector = Icons.Outlined.Bedtime,
								contentDescription = null,
								tint = if (isEnabled) MaterialTheme.colorScheme.positive else MaterialTheme.colorScheme.onSurfaceVariant
							)
						},
						onClick = {
							onSleepTimer?.invoke()
						},
						colors = colors,
						contentPadding = contentPadding
					)
				}

				if (showPlaybackSpeed) {
					ListItem(
						content = {
							Text(
								stringResource(Res.string.option_playback_speed)
							)
						},
						leadingContent = {
							Icon(
								Icons.Outlined.Speed,
								null
							)
						},
						onClick = dropUnlessResumed {
							onPlaybackSpeed?.invoke()
						},
						colors = colors,
						contentPadding = contentPadding
					)
				}

				if (onTrackInfo != null) {
					ListItem(
						content = { Text(stringResource(Res.string.action_track_info)) },
						leadingContent = { Icon(Icons.Outlined.Info, null) },
						onClick = {
							onDismissRequest()
							onTrackInfo()
						},
						colors = colors,
						contentPadding = contentPadding
					)
				}
			}
		}
	}
}
