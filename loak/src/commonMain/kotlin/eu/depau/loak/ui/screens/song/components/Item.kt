package eu.depau.loak.ui.screens.song.components

import eu.depau.loak.ui.util.onSecondaryClick
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_to_queue
import eu.depau.loak.generated.resources.action_play_next
import eu.depau.loak.generated.resources.info_download_failed
import eu.depau.loak.generated.resources.info_downloaded
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.data.database.entities.DownloadEntity
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Star
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.DownloadOff
import eu.depau.loak.icons.outlined.Queue
import eu.depau.loak.icons.outlined.QueuePlayNext
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.ui.components.common.playOrExplain
import eu.depau.loak.ui.components.common.unavailable
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.util.InlineExplicitIcon
import eu.depau.loak.ui.util.buildSongInfoString
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.ui.components.sheets.SongActionsSheet

@Composable
fun SongListScreenItem(
	modifier: Modifier,
	song: DomainSong,
	onPlayNext: () -> Unit,
	onAddToQueue: () -> Unit,
	onClick: () -> Unit,
	download: DownloadEntity?
) {
	val backStack = LocalNavStack.current
	val dismissState = rememberSwipeToDismissBoxState()
	val scope = rememberCoroutineScope()
	var sheetOpen by rememberSaveable { mutableStateOf(false) }
	val onSelect = { sheetOpen = true }
	val songRepository = koinInject<SongRepository>()
	val starred by remember(song.id) { songRepository.observeSongStarred(song.id) }
		.collectAsState(song.starredAt != null)
	val preferenceManager = koinInject<PreferenceManager>()

	SwipeToDismissBox(
		modifier = modifier,
		state = dismissState,
		onDismiss = {
			if (it == SwipeToDismissBoxValue.StartToEnd) onAddToQueue()
			if (it == SwipeToDismissBoxValue.EndToStart) onPlayNext()
			scope.launch { dismissState.reset() }
		},
		backgroundContent = {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.clip(MaterialTheme.shapes.extraSmall)
					.background(MaterialTheme.colorScheme.primaryContainer)
					.padding(horizontal = 20.dp),
				contentAlignment = Alignment.CenterEnd
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
		Box {
			ListItem(
				modifier = Modifier
					.unavailable(!LocalAvailability.current.song(song.id))
					.onSecondaryClick(onSelect),
				onClick = playOrExplain(song.id, onClick),
				onLongClick = onSelect,
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
					Row(verticalAlignment = Alignment.CenterVertically) {
						if (starred) {
							Icon(
								Icons.Filled.Star,
								null,
								modifier = Modifier.size(16.dp)
							)
							Spacer(Modifier.width(6.dp))
						}
						if (download != null) {
							when (download.status) {
								DownloadStatus.DOWNLOADING -> {
									CircularProgressIndicator(
										progress = { download.progress },
										modifier = Modifier.size(16.dp),
										strokeWidth = 2.dp
									)
								}

								DownloadStatus.DOWNLOADED -> {
									Icon(
										Icons.Outlined.Check,
										contentDescription = stringResource(Res.string.info_downloaded),
										modifier = Modifier.size(16.dp),
										tint = MaterialTheme.colorScheme.primary
									)
								}

								DownloadStatus.FAILED -> {
									Icon(
										Icons.Outlined.DownloadOff,
										contentDescription = stringResource(Res.string.info_download_failed),
										modifier = Modifier.size(16.dp),
										tint = MaterialTheme.colorScheme.error
									)
								}

								else -> {}
							}
						}
					}
				}
			)
			SongActionsSheet(song = song, open = sheetOpen, onDismissRequest = { sheetOpen = false })
		}
	}
}
