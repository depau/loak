package eu.depau.loak.ui.components.common

import eu.depau.loak.ui.util.onSecondaryClick
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.generated.resources.Res
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
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.DownloadOff
import eu.depau.loak.icons.outlined.Lock
import eu.depau.loak.icons.outlined.Offline
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.sheets.SongActionsSheet
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.util.InlineExplicitIcon
import eu.depau.loak.ui.util.buildSongInfoString

/** A song in a list: tap plays it ([onClick]), long-press opens its [SongActionsSheet]. */
@Composable
fun SongRow(
	song: DomainSong,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	download: DownloadEntity? = null
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val backStack = LocalNavStack.current
	var sheetOpen by rememberSaveable { mutableStateOf(false) }
	val onLongClick = { sheetOpen = true }
	val songRepository = koinInject<SongRepository>()
	val starredState by remember(song.id) { songRepository.observeSongStarred(song.id) }
		.collectAsState(song.starredAt != null)

	val isCurrentTrack = playerState.currentSong?.id == song.id
	val isExplicit = song.explicitStatus == DomainExplicitStatus.Explicit
		&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	val maybeUnavailable = !LocalAvailability.current.song(song.id)

	ListItem(
		modifier = modifier
			.width(400.dp)
			.alpha(if (isExplicit) .5f else 1f)
			.unavailable(maybeUnavailable)
			.onSecondaryClick(onLongClick)
			.combinedClickable(
				onClick = playOrExplain(song.id, onClick),
				onLongClick = onLongClick,
				enabled = !isExplicit
			),
		headlineContent = {
			Text(
				text = buildAnnotatedString {
					append(song.title)
					if (song.explicitStatus == DomainExplicitStatus.Explicit) {
						append(" ")
						appendInlineContent("InlineExplicitIcon")
					}
				},
				inlineContent = InlineExplicitIcon,
				maxLines = 2
			)
		},
		supportingContent = {
			MarqueeText(
				buildSongInfoString(
					song = song,
					onClickArtist = { backStack.add(Screen.ArtistDetail(it)) }
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
		trailingContent = {
			Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(83.dp)) {
				if (starredState) {
					Icon(
						Icons.Filled.Star,
						null,
						modifier = Modifier.size(16.dp)
					)
					Spacer(Modifier.width(8.dp))
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
			}
		}
	)

	SongActionsSheet(song = song, open = sheetOpen, onDismissRequest = { sheetOpen = false })
}
