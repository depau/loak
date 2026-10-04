package eu.depau.loak.ui.screens.collection.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_to_queue
import eu.depau.loak.generated.resources.action_play_next
import eu.depau.loak.generated.resources.info_download_failed
import eu.depau.loak.generated.resources.info_downloaded
import eu.depau.loak.generated.resources.info_explicit
import eu.depau.loak.generated.resources.info_not_available_offline
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.data.database.entities.DownloadEntity
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.DownloadOff
import eu.depau.loak.icons.outlined.Lock
import eu.depau.loak.icons.outlined.Offline
import eu.depau.loak.icons.outlined.Queue
import eu.depau.loak.icons.outlined.QueuePlayNext
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.playOrExplain
import eu.depau.loak.ui.components.common.unavailable
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.common.Waveform
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.util.InlineExplicitIcon
import eu.depau.loak.ui.util.buildSongInfoString
import eu.depau.loak.util.toHoursMinutesSeconds

@Composable
fun CollectionDetailScreenSongRow(
	song: DomainSong,
	index: Int,
	count: Int,
	isPlaylist: Boolean = false,
	onClick: (() -> Unit),
	onLongClick: (() -> Unit),
	onPlayNext: (() -> Unit),
	onAddToQueue: (() -> Unit),
	isStarred: Boolean,
	download: DownloadEntity? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()

	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val isCurrentTrack = playerState.currentSong?.id == song.id
	val isExplicit = song.explicitStatus == DomainExplicitStatus.Explicit
		&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	val maybeUnavailable = !LocalAvailability.current.song(song.id)

	val dismissState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()

	val backStack = LocalNavStack.current

	SwipeToDismissBox(
		modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.5.dp),
		state = dismissState,
		gesturesEnabled = !isExplicit,
		onDismiss = {
			if (it == SwipeToDismissBoxValue.StartToEnd) {
				onAddToQueue()
			}
			if (it == SwipeToDismissBoxValue.EndToStart) {
				onPlayNext()
			}
			scope.launch { dismissState.reset() }
		},
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(MaterialTheme.shapes.largeIncreased)
					.background(MaterialTheme.colorScheme.primaryContainer)
					.padding(horizontal = 20.dp)
			) {
				when (dismissState.dismissDirection) {
					SwipeToDismissBoxValue.StartToEnd -> {
						Icon(
							imageVector = Icons.Outlined.Queue,
							contentDescription = stringResource(Res.string.action_add_to_queue),
							tint = MaterialTheme.colorScheme.onPrimaryContainer,
							modifier = Modifier.align(Alignment.CenterStart)
						)
					}

					SwipeToDismissBoxValue.EndToStart -> {
						Icon(
							imageVector = Icons.Outlined.QueuePlayNext,
							contentDescription = stringResource(Res.string.action_play_next),
							tint = MaterialTheme.colorScheme.onPrimaryContainer,
							modifier = Modifier.align(Alignment.CenterEnd)
						)
					}

					else -> {}
				}
			}
		}
	) {
		SegmentedListItem(
			modifier = Modifier.unavailable(maybeUnavailable),
			enabled = !isExplicit,
			contentPadding = PaddingValues(14.dp),
			onClick = playOrExplain(song.id, onClick),
			onLongClick = onLongClick,
			shapes = SegmentedListItemDefaults.segmentedShapes(
				index = index,
				count = count,
				dismissDirection = dismissState.dismissDirection
			),
			leadingContent = {
				if (isPlaylist)
					CoverArt(
						modifier = Modifier.size(48.dp),
						coverArtId = song.coverArtId,
						shape = MaterialTheme.shapes.small
					)
				else
					Text(
						text = "${index + 1}",
						modifier = Modifier.width(25.dp),
						style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
						fontWeight = FontWeight(400),
						maxLines = 1,
						textAlign = TextAlign.Center,
						autoSize = TextAutoSize.StepBased(6.sp, 13.sp)
					)
			},
			content = {
				Column {
					MarqueeText(
						text = buildAnnotatedString {
							append(song.title)
							if (song.explicitStatus == DomainExplicitStatus.Explicit) {
								append(" ")
								appendInlineContent("InlineExplicitIcon")
							}
						},
						inlineContent = InlineExplicitIcon
					)
					MarqueeText(
						text = buildSongInfoString(
							song = song,
							onClickArtist = { backStack.add(Screen.ArtistDetail(it)) },
							showYear = false,
							showAlbum = false
						),
						style = MaterialTheme.typography.bodySmall
					)
				}
			},
			trailingContent = {
				Row(verticalAlignment = Alignment.CenterVertically) {
					if (isStarred) {
						Icon(
							Icons.Filled.Star,
							null,
							modifier = Modifier.size(16.dp)
						)
						Spacer(Modifier.width(6.dp))
					}
					if (isExplicit) {
						Icon(
							Icons.Outlined.Lock,
							stringResource(Res.string.info_explicit),
							modifier = Modifier.size(20.dp)
						)
						Spacer(Modifier.width(6.dp))
					}
					if (maybeUnavailable) {
						Icon(
							Icons.Outlined.Offline,
							stringResource(Res.string.info_not_available_offline),
							modifier = Modifier.size(20.dp)
						)
						Spacer(Modifier.width(6.dp))
					}
					if (download != null && !isCurrentTrack) {
						when (download.status) {
							DownloadStatus.DOWNLOADING -> {
								CircularProgressIndicator(
									progress = { download.progress },
									modifier = Modifier.size(16.dp),
									strokeWidth = 2.dp
								)
								Spacer(Modifier.width(8.dp))
							}

							DownloadStatus.DOWNLOADED -> {
								Icon(
									Icons.Outlined.Check,
									contentDescription = stringResource(Res.string.info_downloaded),
									modifier = Modifier.size(16.dp),
									tint = MaterialTheme.colorScheme.primary
								)
								Spacer(Modifier.width(8.dp))
							}

							DownloadStatus.FAILED -> {
								Icon(
									Icons.Outlined.DownloadOff,
									contentDescription = stringResource(Res.string.info_download_failed),
									modifier = Modifier.size(16.dp),
									tint = MaterialTheme.colorScheme.error
								)
								Spacer(Modifier.width(8.dp))
							}

							else -> {}
						}
					}
					if (isCurrentTrack) {
						Waveform(
							modifier = Modifier.padding(end = 12.dp),
							isPlaying = !playerState.isPaused
						)
					}
					song.duration.toHoursMinutesSeconds().let {
						Text(
							text = it,
							style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
							fontWeight = FontWeight(400),
							fontSize = 13.sp,
							color = MaterialTheme.colorScheme.onSurfaceVariant,
							maxLines = 1
						)
					}
				}
			}
		)
	}
}
