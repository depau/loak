package eu.depau.loak.ui.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.NavidromeManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.PlaybackSource
import eu.depau.loak.domain.models.formatSampleRate
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.label_cached_copy
import eu.depau.loak.generated.resources.label_direct_play
import eu.depau.loak.generated.resources.label_downloaded_copy
import eu.depau.loak.generated.resources.label_server_transcode
import eu.depau.loak.generated.resources.label_stage_delivery
import eu.depau.loak.generated.resources.label_stage_output
import eu.depau.loak.generated.resources.label_stage_processing
import eu.depau.loak.generated.resources.label_stage_source
import eu.depau.loak.generated.resources.subtitle_audio_quality
import eu.depau.loak.generated.resources.title_audio_quality
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.util.toFileSize

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioQualitySheet(
	song: DomainSong,
	onDismissRequest: () -> Unit
) {
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()
	val details = playerState.playbackDetails?.takeIf { it.songId == song.id }

	val navidrome = koinInject<NavidromeManager>()
	val serverSoftware by produceState<String?>(null) {
		value = runCatching {
			val info = navidrome.serverInfo()
			listOfNotNull(info.type?.replaceFirstChar { it.uppercase() }, info.version)
				.joinToString(" ")
				.ifBlank { null }
		}.getOrNull()
	}

	ModalBottomSheet(
		onDismissRequest = onDismissRequest
	) {
		Column(
			modifier = Modifier
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 20.dp)
				.padding(bottom = 32.dp),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(
					text = stringResource(Res.string.title_audio_quality),
					style = MaterialTheme.typography.headlineSmall,
					fontWeight = FontWeight.Bold
				)
				Text(
					text = stringResource(Res.string.subtitle_audio_quality),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}

			// 1. SOURCE
			StageSection(label = stringResource(Res.string.label_stage_source)) {
				val formatTitle = (song.fileExtension?.uppercase() ?: "AUDIO") + " original"
				val sampleRateStr = song.sampleRate?.let { formatSampleRate(it) }
				val bitDepthStr = song.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" }
				val channelsStr = when (song.audioChannelCount) {
					1 -> "Mono"
					2 -> "Stereo"
					null -> null
					else -> "${song.audioChannelCount} ch"
				}
				val subtitle = listOfNotNull(sampleRateStr, bitDepthStr, channelsStr)
					.joinToString(" · ")
					.ifBlank { null }

				val bitrateStr = song.bitRate?.takeIf { it > 0 }?.let { "$it kbps" }
				val sizeStr = song.fileSize.takeIf { it > 0 }?.toFileSize()
				val meta = listOfNotNull(bitrateStr, sizeStr, serverSoftware)
					.joinToString(" · ")
					.ifBlank { null }

				StageCard(
					title = formatTitle,
					subtitle = subtitle,
					meta = meta
				)
			}

			// 2. DELIVERY
			StageSection(label = stringResource(Res.string.label_stage_delivery)) {
				val deliveryTitle = when (details?.source) {
					PlaybackSource.Download -> stringResource(Res.string.label_downloaded_copy)
					PlaybackSource.Cache -> stringResource(Res.string.label_cached_copy)
					PlaybackSource.Stream, null -> if (details?.isTranscoded == true) {
						stringResource(Res.string.label_server_transcode)
					} else {
						stringResource(Res.string.label_direct_play)
					}
				}

				val codecStr = details?.codec ?: song.fileExtension?.uppercase()
				val rateStr = details?.sampleRateHz?.let { formatSampleRate(it) }
				val chStr = when (details?.channelCount) {
					1 -> "Mono"
					2 -> "Stereo"
					null -> null
					else -> "${details.channelCount} ch"
				}
				val subtitle = listOfNotNull(codecStr, rateStr, chStr)
					.joinToString(" · ")
					.ifBlank { null }

				val bitrateStr = details?.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }
				val sourceDetail = when (details?.source) {
					PlaybackSource.Download -> "Downloaded file"
					PlaybackSource.Cache -> "Audio cache hit"
					PlaybackSource.Stream, null -> if (details?.isTranscoded == true) "Transcoded" else "Direct stream"
				}
				val meta = listOfNotNull(bitrateStr, sourceDetail)
					.joinToString(" · ")
					.ifBlank { null }

				StageCard(
					title = deliveryTitle,
					subtitle = subtitle,
					meta = meta
				)
			}

			// 3. PROCESSING (only if any stage actually ran)
			val hasProcessing = details?.decoder != null ||
				details?.pcmFormat != null ||
				details?.replayGain != null ||
				details?.equalizer != null ||
				details?.isOffloaded == true ||
				playerState.playbackSpeed != 1.0f

			if (hasProcessing) {
				StageSection(label = stringResource(Res.string.label_stage_processing)) {
					val processingTitle = details?.pcmFormat ?: details?.decoder ?: "Active processing"
					val subtitle = if (details?.pcmFormat != null && details.decoder != null) {
						details.decoder
					} else {
						null
					}

					StageCard(
						title = processingTitle,
						subtitle = subtitle
					) {
						FlowRow(
							modifier = Modifier.padding(top = 8.dp),
							horizontalArrangement = Arrangement.spacedBy(8.dp),
							verticalArrangement = Arrangement.spacedBy(8.dp)
						) {
							details?.replayGain?.let { rg ->
								StageChip(label = "ReplayGain: $rg")
							}
							details?.equalizer?.let { eq ->
								StageChip(label = "EQ: $eq")
							}
							if (playerState.playbackSpeed != 1.0f) {
								StageChip(label = "${playerState.playbackSpeed}× speed")
							}
							if (details?.isOffloaded == true) {
								StageChip(label = "Audio offload")
							}
						}
					}
				}
			}

			// 4. OUTPUT (only if route/device or output format known)
			val hasOutput = details?.outputDevice != null || details?.outputFormat != null
			if (hasOutput) {
				StageSection(label = stringResource(Res.string.label_stage_output)) {
					StageCard(
						title = details.outputDevice ?: "Audio output",
						subtitle = details.outputFormat
					)
				}
			}

		}
	}
}

@Composable
private fun StageSection(
	label: String,
	content: @Composable () -> Unit
) {
	Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			modifier = Modifier.fillMaxWidth()
		) {
			Text(
				text = label,
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary,
				letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing
			)
			HorizontalDivider(
				modifier = Modifier
					.weight(1f)
					.padding(start = 10.dp),
				color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
			)
		}
		content()
	}
}

@Composable
private fun StageCard(
	title: String,
	subtitle: String? = null,
	meta: String? = null,
	extraContent: (@Composable () -> Unit)? = null
) {
	Surface(
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
		modifier = Modifier.fillMaxWidth()
	) {
		Column(
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
			verticalArrangement = Arrangement.spacedBy(3.dp)
		) {
			Text(
				text = title,
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.SemiBold
			)
			if (subtitle != null) {
				Text(
					text = subtitle,
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
			if (meta != null) {
				Text(
					text = meta,
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.outline
				)
			}
			extraContent?.invoke()
		}
	}
}

@Composable
private fun StageChip(label: String) {
	Surface(
		shape = MaterialTheme.shapes.small,
		color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f)
	) {
		Text(
			text = label,
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
		)
	}
}
