package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.models.settings.QuickPicksDecay
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_history_source_koito
import eu.depau.loak.generated.resources.info_history_source_lastfm
import eu.depau.loak.generated.resources.info_history_source_listenbrainz
import eu.depau.loak.generated.resources.info_history_source_none
import eu.depau.loak.generated.resources.info_koito_api_key
import eu.depau.loak.generated.resources.info_koito_url
import eu.depau.loak.generated.resources.info_listening_history
import eu.depau.loak.generated.resources.info_quick_picks_decay
import eu.depau.loak.generated.resources.label_api_key
import eu.depau.loak.generated.resources.label_koito_url
import eu.depau.loak.generated.resources.label_lastfm_username
import eu.depau.loak.generated.resources.option_history_source_koito
import eu.depau.loak.generated.resources.option_history_source_lastfm
import eu.depau.loak.generated.resources.option_history_source_listenbrainz
import eu.depau.loak.generated.resources.option_history_source_listenbrainz_unset
import eu.depau.loak.generated.resources.option_history_source_none
import eu.depau.loak.generated.resources.title_history_source
import eu.depau.loak.generated.resources.title_listening_history
import eu.depau.loak.generated.resources.title_quick_picks_decay
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsRadioItem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Listening history: which scrobbler, if any, Lo'ak reads plays from beside this device, and
 * how quickly Quick picks forgets a song.
 *
 * The knobs are all Quick picks' — the Recap reads the same history, so a source switched on
 * here changes both.
 */
@Composable
fun ListeningHistorySettingsScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	// Koito and Last.fm are plain HTTP calls the web build can make; the field is hidden there
	// only because the app has nowhere to put a persistent preference on a static host
	val onWeb = LocalPlatformContext.current.platformType == PlatformType.Web
	SettingsScaffold(stringResource(Res.string.title_listening_history)) {
		Text(
			stringResource(Res.string.info_listening_history),
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(horizontal = 12.dp)
		)
		SourceGroup(preferenceManager, onWeb)
		DecayGroup(preferenceManager)
	}
}

/** What [id] is called, wherever a screen has to name the source that is on. */
internal fun historySourceLabel(id: String): StringResource = when (id) {
	"listenbrainz" -> Res.string.option_history_source_listenbrainz
	"koito" -> Res.string.option_history_source_koito
	"lastfm" -> Res.string.option_history_source_lastfm
	else -> Res.string.option_history_source_none
}

/** What reading from [id] means, the line under its row in the picker. */
internal fun historySourceInfo(id: String): StringResource = when (id) {
	"listenbrainz" -> Res.string.info_history_source_listenbrainz
	"koito" -> Res.string.info_history_source_koito
	"lastfm" -> Res.string.info_history_source_lastfm
	else -> Res.string.info_history_source_none
}

@Composable
private fun ColumnScope.SourceGroup(preferenceManager: PreferenceManager, onWeb: Boolean) {
	SettingsGroup(title = { Text(stringResource(Res.string.title_history_source)) }) {
		val listenBrainzUser = preferenceManager.listenBrainzUsername
		val sources = listOf("", "listenbrainz", "koito", "lastfm")
		sources.forEachIndexed { index, id ->
			val selected = preferenceManager.listeningHistorySource == id
			SettingsRadioItem(
				selected = selected,
				onClick = { preferenceManager.listeningHistorySource = id },
				shapes = SegmentedListItemDefaults.segmentedShapes(index, sources.size),
				content = {
					Text(
						stringResource(
							if (id == "listenbrainz" && listenBrainzUser.isBlank())
								Res.string.option_history_source_listenbrainz_unset
							else historySourceLabel(id)
						)
					)
				},
				supportingContent = { Text(stringResource(historySourceInfo(id))) }
			)
		}

		when (preferenceManager.listeningHistorySource) {
			"koito" -> if (!onWeb) KoitoFields(preferenceManager)
			"lastfm" -> if (!onWeb) LastFmField(preferenceManager)
			else -> Unit
		}
	}
}

/** Koito's address and key: the only source that needs somewhere to point at. */
@Composable
private fun KoitoFields(preferenceManager: PreferenceManager) {
	// saved as typed: the source trims them, so a trailing slash or stray space is harmless
	val url = rememberTextFieldState(preferenceManager.koitoUrl)
	val key = rememberTextFieldState(preferenceManager.koitoApiKey)
	LaunchedEffect(url.text) { preferenceManager.koitoUrl = url.text.toString() }
	LaunchedEffect(key.text) { preferenceManager.koitoApiKey = key.text.toString() }

	OutlinedTextField(
		state = url,
		modifier = Modifier.fillMaxWidth(),
		label = { Text(stringResource(Res.string.label_koito_url)) },
		lineLimits = TextFieldLineLimits.SingleLine,
		supportingText = { Text(stringResource(Res.string.info_koito_url)) },
		keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri)
	)
	OutlinedTextField(
		state = key,
		modifier = Modifier.fillMaxWidth(),
		label = { Text(stringResource(Res.string.label_api_key)) },
		lineLimits = TextFieldLineLimits.SingleLine,
		supportingText = { Text(stringResource(Res.string.info_koito_api_key)) },
		keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
	)
}

@Composable
private fun LastFmField(preferenceManager: PreferenceManager) {
	val username = rememberTextFieldState(preferenceManager.lastFmUsername)
	LaunchedEffect(username.text) { preferenceManager.lastFmUsername = username.text.toString() }
	OutlinedTextField(
		state = username,
		modifier = Modifier.fillMaxWidth(),
		label = { Text(stringResource(Res.string.label_lastfm_username)) },
		lineLimits = TextFieldLineLimits.SingleLine,
		keyboardOptions = KeyboardOptions(autoCorrectEnabled = false)
	)
}

@Composable
private fun ColumnScope.DecayGroup(preferenceManager: PreferenceManager) {
	SettingsGroup(title = { Text(stringResource(Res.string.title_quick_picks_decay)) }) {
		Text(
			stringResource(Res.string.info_quick_picks_decay),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
		)
		QuickPicksDecay.entries.forEachIndexed { index, decay ->
			SettingsRadioItem(
				selected = preferenceManager.quickPicksDecay == decay,
				onClick = { preferenceManager.quickPicksDecay = decay },
				shapes = SegmentedListItemDefaults.segmentedShapes(index, QuickPicksDecay.entries.size),
				content = { Text(stringResource(decay.displayName)) },
				supportingContent = { Text(stringResource(decay.description)) }
			)
		}
	}
}
