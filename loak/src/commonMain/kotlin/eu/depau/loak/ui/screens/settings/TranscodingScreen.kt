package eu.depau.loak.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.title_downloads_settings
import eu.depau.loak.generated.resources.title_streaming
import eu.depau.loak.generated.resources.option_custom_transcoding
import eu.depau.loak.generated.resources.info_streaming_quality
import eu.depau.loak.generated.resources.info_transcoding_defaults
import eu.depau.loak.generated.resources.option_max_bitrate_cellular
import eu.depau.loak.generated.resources.option_max_bitrate
import eu.depau.loak.generated.resources.option_max_bitrate_wifi
import eu.depau.loak.generated.resources.option_download_format
import eu.depau.loak.generated.resources.option_stream_custom_quality
import eu.depau.loak.generated.resources.option_download_custom_quality
import eu.depau.loak.generated.resources.option_stream_format_cellular
import eu.depau.loak.generated.resources.option_stream_format_wifi
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Info
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem

/** Custom formats and bitrates, in place of the presets, for servers with their own transcoding. */
@Composable
fun SettingsTranscodingScreen() {
	val preferenceManager = koinInject<PreferenceManager>()

	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(Res.string.option_custom_transcoding)) }) }
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
					SettingsToggleItem(
						checked = preferenceManager.isAdvancedTranscodingActive,
						onCheckedChange = { preferenceManager.isAdvancedTranscodingActive = it },
						content = { Text(stringResource(Res.string.option_stream_custom_quality)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
					)
				}
				AnimatedVisibility(visible = preferenceManager.isAdvancedTranscodingActive) {
					CustomStreamingOptions()
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_downloads_settings)) }) {
					SettingsToggleItem(
						checked = preferenceManager.isAdvancedDownloadTranscodingActive,
						onCheckedChange = { preferenceManager.isAdvancedDownloadTranscodingActive = it },
						content = { Text(stringResource(Res.string.option_download_custom_quality)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
					)
				}
				AnimatedVisibility(visible = preferenceManager.isAdvancedDownloadTranscodingActive) {
					CustomDownloadOptions()
				}

				Row(
					modifier = Modifier.padding(horizontal = 8.dp),
					horizontalArrangement = Arrangement.spacedBy(16.dp)
				) {
					Icon(
						Icons.Outlined.Info,
						contentDescription = null,
						tint = MaterialTheme.colorScheme.onSurfaceVariant
					)
					Text(
						stringResource(Res.string.info_streaming_quality),
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						style = MaterialTheme.typography.bodyMedium
					)
				}
			}
		}
	}
}

@Composable
private fun CustomDownloadOptions() {
	val preferenceManager = koinInject<PreferenceManager>()
	var bitrateInput by remember {
		val current = preferenceManager.customDownloadMaxBitrate
		mutableStateOf(if (current > 0) current.toString() else "")
	}

	Column(
		modifier = Modifier.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		OutlinedTextField(
			value = bitrateInput,
			onValueChange = { newValue ->
				if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
					bitrateInput = newValue
					preferenceManager.customDownloadMaxBitrate = newValue.toIntOrNull() ?: 0
				}
			},
			label = { Text(stringResource(Res.string.option_max_bitrate)) },
			placeholder = { Text("0") },
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
			modifier = Modifier.fillMaxWidth(),
			singleLine = true
		)

		OutlinedTextField(
			value = preferenceManager.customDownloadFormat,
			onValueChange = { preferenceManager.customDownloadFormat = it },
			label = { Text(stringResource(Res.string.option_download_format)) },
			supportingText = { Text(stringResource(Res.string.info_transcoding_defaults)) },
			modifier = Modifier.fillMaxWidth(),
			singleLine = true
		)
	}
}

@Composable
private fun CustomStreamingOptions() {
	val preferenceManager = koinInject<PreferenceManager>()
	var wifiInput by remember {
		val current = preferenceManager.customMaxBitrateWifi
		mutableStateOf(if (current > 0) current.toString() else "")
	}
	var cellularInput by remember {
		val current = preferenceManager.customMaxBitrateCellular
		mutableStateOf(if (current > 0) current.toString() else "")
	}

	Column(
		modifier = Modifier.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		OutlinedTextField(
			value = wifiInput,
			onValueChange = { newValue ->
				if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
					wifiInput = newValue
					preferenceManager.customMaxBitrateWifi = newValue.toIntOrNull() ?: 0
				}
			},
			label = { Text(stringResource(Res.string.option_max_bitrate_wifi)) },
			placeholder = { Text("0") },
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
			modifier = Modifier.fillMaxWidth(),
			singleLine = true
		)

		OutlinedTextField(
			value = cellularInput,
			onValueChange = { newValue ->
				if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
					cellularInput = newValue
					preferenceManager.customMaxBitrateCellular = newValue.toIntOrNull() ?: 0
				}
			},
			label = { Text(stringResource(Res.string.option_max_bitrate_cellular)) },
			placeholder = { Text("0") },
			keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
			modifier = Modifier.fillMaxWidth(),
			singleLine = true
		)

		OutlinedTextField(
			value = preferenceManager.customFormatWifi,
			onValueChange = { preferenceManager.customFormatWifi = it },
			label = { Text(stringResource(Res.string.option_stream_format_wifi)) },
			modifier = Modifier.fillMaxWidth(),
			singleLine = true
		)

		OutlinedTextField(
			value = preferenceManager.customFormatCellular,
			onValueChange = { preferenceManager.customFormatCellular = it },
			label = { Text(stringResource(Res.string.option_stream_format_cellular)) },
			supportingText = { Text(stringResource(Res.string.info_transcoding_defaults)) },
			modifier = Modifier.fillMaxWidth(),
			singleLine = true
		)
	}
}
