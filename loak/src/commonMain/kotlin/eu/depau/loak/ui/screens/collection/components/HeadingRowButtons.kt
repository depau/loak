package eu.depau.loak.ui.screens.collection.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.depau.loak.ui.theme.ContinuousCapsule
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_delete_download
import eu.depau.loak.generated.resources.action_instant_mix
import eu.depau.loak.generated.resources.action_play
import eu.depau.loak.generated.resources.action_shuffle
import eu.depau.loak.generated.resources.info_download_failed
import eu.depau.loak.generated.resources.notice_deleted_download
import eu.depau.loak.generated.resources.notice_download_started
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Delete
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.DownloadOff
import eu.depau.loak.icons.outlined.InstantMix
import eu.depau.loak.icons.outlined.Shuffle
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.theme.defaultFont

@Composable
fun CollectionDetailScreenHeadingRowButtons(
	collection: DomainSongCollection
) {
	val player = koinInject<MediaPlayerViewModel>()
	val snackBarManager = koinInject<SnackBarManager>()
	val downloadManager = koinInject<DownloadManager>()
	val scope = rememberCoroutineScope()
	val canMix by produceState(false) { value = player.canMix() }

	val downloadStatus by downloadManager
		.getCollectionDownloadStatus(collection.songs.map { it.id })
		.collectAsState(initial = DownloadStatus.NOT_DOWNLOADED)

	Row(
		// on wide windows the play button keeps a phone-like width
		modifier = Modifier.widthIn(max = 560.dp).padding(horizontal = 31.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(
			10.dp,
			alignment = Alignment.CenterHorizontally
		)
	) {
		val buttonShape = ContinuousCapsule
		val buttonHeight = 44.dp
		OutlinedButton(
			modifier = Modifier.size(width = 52.dp, height = buttonHeight),
			onClick = {
				player.shufflePlay(collection)
			},
			shape = buttonShape,
			contentPadding = PaddingValues(0.dp),
			enabled = collection.songs.isNotEmpty()
		) {
			Icon(
				Icons.Outlined.Shuffle,
				contentDescription = stringResource(Res.string.action_shuffle),
				modifier = Modifier.size(24.dp)
			)
		}
		if (canMix) {
			OutlinedButton(
				modifier = Modifier.size(width = 52.dp, height = buttonHeight),
				onClick = {
					// getSimilarSongs takes album ids, not playlist ones
					if (collection is DomainAlbum) {
						player.playInstantMix(collection.id, collection.name.orEmpty())
					} else {
						player.playInstantMix(collection.songs, collection.name.orEmpty())
					}
				},
				shape = buttonShape,
				contentPadding = PaddingValues(0.dp),
				enabled = collection.songs.isNotEmpty()
			) {
				Icon(
					Icons.Outlined.InstantMix,
					contentDescription = stringResource(Res.string.action_instant_mix),
					modifier = Modifier.size(24.dp)
				)
			}
		}
		Button(
			modifier = Modifier.weight(1f).height(buttonHeight),
			onClick = {
				player.playNow(collection)
			},
			shape = buttonShape,
			enabled = collection.songs.isNotEmpty()
		) {
			Icon(
				Icons.Filled.Play,
				null,
				modifier = Modifier.size(25.dp).padding(end = 3.dp)
			)
			Text(
				stringResource(Res.string.action_play),
				maxLines = 1,
				autoSize = TextAutoSize.StepBased(
					minFontSize = 1.sp,
					maxFontSize = 15.sp
				),
				fontWeight = FontWeight.SemiBold,
				fontFamily = defaultFont(round = 100f)
			)
		}
		OutlinedButton(
			modifier = Modifier.size(width = 52.dp, height = buttonHeight),
			onClick = {
				scope.launch {
					when (downloadStatus) {
						DownloadStatus.NOT_DOWNLOADED, DownloadStatus.FAILED -> {
							downloadManager.downloadCollection(collection)
							snackBarManager.notify(Res.string.notice_download_started)
						}

						DownloadStatus.DOWNLOADING -> {
							downloadManager.cancelCollectionDownload(collection)
						}

						DownloadStatus.DOWNLOADED -> {
							downloadManager.deleteDownloadedCollection(collection)
							snackBarManager.notify(Res.string.notice_deleted_download)
						}
					}
				}
			},
			shape = buttonShape,
			enabled = collection.songs.isNotEmpty() ||
				(downloadStatus == DownloadStatus.DOWNLOADED || downloadStatus == DownloadStatus.DOWNLOADING),
			contentPadding = PaddingValues(0.dp)
		) {
			when (downloadStatus) {
				DownloadStatus.DOWNLOADING -> {
					Box(contentAlignment = Alignment.Center) {
						CircularProgressIndicator(
							modifier = Modifier.size(24.dp),
							strokeWidth = 2.dp,
							color = MaterialTheme.colorScheme.primary
						)
						Icon(
							imageVector = Icons.Outlined.Close,
							contentDescription = "Cancel",
							modifier = Modifier.size(12.dp),
							tint = MaterialTheme.colorScheme.primary
						)
					}
				}

				DownloadStatus.DOWNLOADED -> {
					Icon(
						imageVector = Icons.Outlined.Delete,
						contentDescription = stringResource(Res.string.action_delete_download),
						modifier = Modifier.size(24.dp),
						tint = MaterialTheme.colorScheme.primary
					)
				}

				DownloadStatus.FAILED -> {
					Icon(
						imageVector = Icons.Outlined.DownloadOff,
						contentDescription = stringResource(Res.string.info_download_failed),
						modifier = Modifier.size(24.dp),
						tint = MaterialTheme.colorScheme.error
					)
				}

				else -> {
					Icon(
						imageVector = Icons.Outlined.Download,
						contentDescription = null,
						modifier = Modifier.size(24.dp)
					)
				}
			}
		}
	}
}
