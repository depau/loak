package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.manager.AudioGainManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.ReplayGainMode
import eu.depau.loak.domain.models.settings.StreamingQuality
import eu.depau.loak.domain.models.settings.description
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_uses_replaygain
import eu.depau.loak.generated.resources.option_audio_offload
import eu.depau.loak.generated.resources.option_custom_transcoding
import eu.depau.loak.generated.resources.option_equaliser
import eu.depau.loak.generated.resources.option_even_out_volume
import eu.depau.loak.generated.resources.option_gapless_playback
import eu.depau.loak.generated.resources.option_preamp_tip
import eu.depau.loak.generated.resources.option_preamp_with_rg
import eu.depau.loak.generated.resources.option_preamp_without_rg
import eu.depau.loak.generated.resources.option_streaming_cellular
import eu.depau.loak.generated.resources.option_streaming_wifi
import eu.depau.loak.generated.resources.subtitle_audio_offload
import eu.depau.loak.generated.resources.subtitle_custom_transcoding
import eu.depau.loak.generated.resources.subtitle_equaliser
import eu.depau.loak.generated.resources.subtitle_equaliser_disabled
import eu.depau.loak.generated.resources.subtitle_gapless_playback
import eu.depau.loak.generated.resources.title_advanced
import eu.depau.loak.generated.resources.title_download_quality
import eu.depau.loak.generated.resources.title_downloads_settings
import eu.depau.loak.generated.resources.title_effects
import eu.depau.loak.generated.resources.title_sound_quality
import eu.depau.loak.generated.resources.title_streaming
import eu.depau.loak.generated.resources.title_volume
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsChoiceItem
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.ui.screens.settings.components.SettingsSliderItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.absoluteValue
import kotlin.math.round

/** Streaming and download quality, volume leveling and the equalizer. */
@Composable
fun SettingsSoundScreen() {
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	// ReplayGain, the equalizer, gapless and offload are Android's player only
	val isAndroid = platformContext.platformType == PlatformType.Android
	val preferenceManager = koinInject<PreferenceManager>()
	val backStack = LocalNavStack.current

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_sound_quality)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				}
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				SettingsGroup(title = { Text(stringResource(Res.string.title_streaming)) }) {
					QualityItem(
						title = { Text(stringResource(Res.string.option_streaming_wifi)) },
						quality = preferenceManager.streamingQualityWifi,
						onQualityChange = { preferenceManager.streamingQualityWifi = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					QualityItem(
						title = { Text(stringResource(Res.string.option_streaming_cellular)) },
						quality = preferenceManager.streamingQualityCellular,
						onQualityChange = { preferenceManager.streamingQualityCellular = it },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_downloads_settings)) }) {
					DownloadQualityItem(SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1))
				}

				if (isAndroid) {
					VolumeGroup()

					SettingsGroup(title = { Text(stringResource(Res.string.title_effects)) }) {
						SettingsNavItem(
							onClick = dropUnlessResumed { backStack.add(Screen.Settings.Equaliser) },
							content = { Text(stringResource(Res.string.option_equaliser)) },
							enabled = !preferenceManager.audioOffload,
							supportingContent = {
								Text(stringResource(
									if (!preferenceManager.audioOffload) Res.string.subtitle_equaliser
									else Res.string.subtitle_equaliser_disabled
								))
							},
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
						)
					}
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_advanced)) }) {
					val count = if (isAndroid) 3 else 1
					SettingsNavItem(
						onClick = dropUnlessResumed { backStack.add(Screen.Settings.Transcoding) },
						content = { Text(stringResource(Res.string.option_custom_transcoding)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_custom_transcoding)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
					)
					if (isAndroid) {
						SettingsToggleItem(
							checked = preferenceManager.gaplessPlayback,
							onCheckedChange = { preferenceManager.gaplessPlayback = it },
							content = { Text(stringResource(Res.string.option_gapless_playback)) },
							supportingContent = { Text(stringResource(Res.string.subtitle_gapless_playback)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = count)
						)
						SettingsToggleItem(
							checked = preferenceManager.audioOffload,
							onCheckedChange = { preferenceManager.audioOffload = it },
							content = { Text(stringResource(Res.string.option_audio_offload)) },
							supportingContent = { Text(stringResource(Res.string.subtitle_audio_offload)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = count)
						)
					}
				}
			}
		}
	}
}

/** The download quality; here and in Downloads & storage. */
@Composable
fun DownloadQualityItem(shapes: ListItemShapes) {
	val preferenceManager = koinInject<PreferenceManager>()
	QualityItem(
		title = { Text(stringResource(Res.string.title_download_quality)) },
		quality = preferenceManager.downloadQuality,
		onQualityChange = { preferenceManager.downloadQuality = it },
		shapes = shapes
	)
}

@Composable
private fun QualityItem(
	title: @Composable () -> Unit,
	quality: StreamingQuality,
	onQualityChange: (StreamingQuality) -> Unit,
	shapes: ListItemShapes
) {
	SettingsChoiceItem(
		choices = StreamingQuality.entries.toImmutableList(),
		selectedChoice = quality,
		onChoiceSelected = onQualityChange,
		content = title,
		label = { q ->
			listOfNotNull(stringResource(q.displayName), q.description()).joinToString(" · ")
		},
		shapes = shapes
	)
}

@Composable
private fun VolumeGroup() {
	val preferenceManager = koinInject<PreferenceManager>()
	val audioGainManager = koinInject<AudioGainManager>()
	SettingsGroup(title = { Text(stringResource(Res.string.title_volume)) }) {
		SettingsChoiceItem(
			choices = ReplayGainMode.entries.toImmutableList(),
			selectedChoice = preferenceManager.replayGainMode,
			onChoiceSelected = {
				preferenceManager.replayGainMode = it
				audioGainManager.applyGainMode(it)
			},
			content = { Text(stringResource(Res.string.option_even_out_volume)) },
			label = { stringResource(it.displayName) },
			description = stringResource(Res.string.info_uses_replaygain),
			shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 3)
		)
		SettingsSliderItem(
			content = { Text(stringResource(Res.string.option_preamp_with_rg)) },
			trailingContent = { Text(preferenceManager.rgAmpGain.decibelsToHuman()) },
			value = preferenceManager.rgAmpGain,
			valueRange = -12f..12f,
			onValueChange = {
				preferenceManager.rgAmpGain = it.round(1)
				audioGainManager.setAmplifierValues(
					withReplayGain = it,
					withoutReplayGain = preferenceManager.ampGain
				)
			},
			shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 3)
		)
		SettingsSliderItem(
			content = { Text(stringResource(Res.string.option_preamp_without_rg)) },
			trailingContent = { Text(preferenceManager.ampGain.decibelsToHuman()) },
			value = preferenceManager.ampGain,
			valueRange = -12f..12f,
			onValueChange = {
				preferenceManager.ampGain = it.round(1)
				audioGainManager.setAmplifierValues(
					withReplayGain = preferenceManager.rgAmpGain,
					withoutReplayGain = it
				)
			},
			description = stringResource(Res.string.option_preamp_tip),
			shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 3)
		)
	}
}

private fun Float.round(decimals: Int): Float {
	var multiplier = 1.0
	repeat(decimals) { multiplier *= 10 }
	return (round(this * multiplier) / multiplier).toFloat()
}

private fun Float.decibelsToHuman(): String {
	val decibels = this.round(1)
	return buildString {
		if (decibels < 0) {
			append("-")
		} else if (decibels > 0) {
			append("+")
		} else {
			append(" ")
		}
		append("${decibels.absoluteValue} dB")
	}
}
