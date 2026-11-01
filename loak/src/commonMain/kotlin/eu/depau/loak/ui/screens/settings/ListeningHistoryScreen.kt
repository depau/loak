package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.discover.ListenBrainzClient
import eu.depau.loak.domain.history.HistoryCheck
import eu.depau.loak.domain.history.KoitoHistorySource
import eu.depau.loak.domain.history.LastFmHistorySource
import eu.depau.loak.domain.models.settings.QuickPicksDecay
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_connect
import eu.depau.loak.generated.resources.action_done
import eu.depau.loak.generated.resources.action_edit
import eu.depau.loak.generated.resources.count_listens
import eu.depau.loak.generated.resources.error_history_unreachable
import eu.depau.loak.generated.resources.error_koito_rejected
import eu.depau.loak.generated.resources.error_koito_unexpected
import eu.depau.loak.generated.resources.error_koito_unreachable
import eu.depau.loak.generated.resources.error_lastfm_unknown_user
import eu.depau.loak.generated.resources.error_no_listenbrainz_user
import eu.depau.loak.generated.resources.info_history_source_koito
import eu.depau.loak.generated.resources.info_history_source_lastfm
import eu.depau.loak.generated.resources.info_history_source_listenbrainz
import eu.depau.loak.generated.resources.info_history_source_none
import eu.depau.loak.generated.resources.info_koito_api_key
import eu.depau.loak.generated.resources.info_koito_url
import eu.depau.loak.generated.resources.info_listenbrainz_shared
import eu.depau.loak.generated.resources.info_listening_history
import eu.depau.loak.generated.resources.info_quick_picks_decay
import eu.depau.loak.generated.resources.info_replaces_history_source
import eu.depau.loak.generated.resources.label_api_key
import eu.depau.loak.generated.resources.label_koito_url
import eu.depau.loak.generated.resources.label_lastfm_username
import eu.depau.loak.generated.resources.label_listenbrainz_username
import eu.depau.loak.generated.resources.option_history_source_koito
import eu.depau.loak.generated.resources.option_history_source_lastfm
import eu.depau.loak.generated.resources.option_history_source_listenbrainz
import eu.depau.loak.generated.resources.option_history_source_none
import eu.depau.loak.generated.resources.title_connect_koito
import eu.depau.loak.generated.resources.title_connect_lastfm
import eu.depau.loak.generated.resources.title_connect_listenbrainz
import eu.depau.loak.generated.resources.title_connected
import eu.depau.loak.generated.resources.title_history_source
import eu.depau.loak.generated.resources.title_listening_history
import eu.depau.loak.generated.resources.title_quick_picks_decay
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.CheckCircle
import eu.depau.loak.icons.outlined.Edit
import eu.depau.loak.icons.outlined.History
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsRadioItem
import eu.depau.loak.util.instanceHost
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
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
	val intro = stringResource(Res.string.info_listening_history)
	SettingsScaffold(
		stringResource(Res.string.title_listening_history),
		icon = Icons.Outlined.History,
		description = intro
	) {
		if (!LocalInWizard.current) {
			Text(intro, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp))
		}
		SourceGroup(preferenceManager)
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

/** Who [id] reads from once it's set up, or null while it has nothing to point at. */
private fun historySourceAccount(preferenceManager: PreferenceManager, id: String): String? = when (id) {
	"koito" -> preferenceManager.koitoUrl.takeIf { it.isNotBlank() && preferenceManager.koitoApiKey.isNotBlank() }
		?.let(::instanceHost)
	"lastfm" -> preferenceManager.lastFmUsername.ifBlank { null }
	"listenbrainz" -> preferenceManager.listenBrainzUsername.ifBlank { null }
	else -> null
}

@Composable
private fun ColumnScope.SourceGroup(preferenceManager: PreferenceManager) {
	val backStack = LocalNavStack.current
	// Koito and Last.fm are plain HTTP calls the web build can make; they're left out there
	// only because the app has nowhere to put a persistent preference on a static host
	val onWeb = LocalPlatformContext.current.platformType == PlatformType.Web
	SettingsGroup(title = { Text(stringResource(Res.string.title_history_source)) }) {
		val sources = listOf("", "listenbrainz", "koito", "lastfm")
			.filter { !onWeb || it !in setOf("koito", "lastfm") }
		sources.forEachIndexed { index, id ->
			val account = historySourceAccount(preferenceManager, id)
			val connect = dropUnlessResumed { backStack.add(Screen.Settings.HistorySourceConnect(id)) }
			SettingsRadioItem(
				selected = preferenceManager.listeningHistorySource == id,
				// one that has nothing to read from yet is connected (and checked) first
				onClick = {
					if (id.isEmpty() || account != null) preferenceManager.listeningHistorySource = id
					else connect()
				},
				shapes = SegmentedListItemDefaults.segmentedShapes(index, sources.size),
				content = { Text(stringResource(historySourceLabel(id))) },
				supportingContent = { Text(account ?: stringResource(historySourceInfo(id))) },
				trailingContent = account?.let {
					{
						IconButton(onClick = connect) {
							Icon(Icons.Outlined.Edit, stringResource(Res.string.action_edit))
						}
					}
				}
			)
		}
	}
}

/**
 * Connecting a history source: its details, checked before anything is saved, then what the
 * check found. Connecting switches the history source to it.
 */
@Composable
fun HistorySourceConnectScreen(source: String) {
	val preferenceManager = koinInject<PreferenceManager>()
	val koito = koinInject<KoitoHistorySource>()
	val lastFm = koinInject<LastFmHistorySource>()
	val listenBrainz = koinInject<ListenBrainzClient>()
	val backStack = LocalNavStack.current
	val scope = rememberCoroutineScope()
	val previous = preferenceManager.listeningHistorySource

	// Koito's address, or the username for the others
	val first = rememberTextFieldState(
		when (source) {
			"koito" -> preferenceManager.koitoUrl
			"lastfm" -> preferenceManager.lastFmUsername
			else -> preferenceManager.listenBrainzUsername
		}
	)
	val key = rememberTextFieldState(preferenceManager.koitoApiKey)
	var busy by remember { mutableStateOf(false) }
	var result by remember { mutableStateOf<HistoryCheck?>(null) }
	val connected = result as? HistoryCheck.Connected

	val intro = stringResource(historySourceInfo(source))
	SettingsScaffold(
		stringResource(
			when (source) {
				"koito" -> Res.string.title_connect_koito
				"lastfm" -> Res.string.title_connect_lastfm
				else -> Res.string.title_connect_listenbrainz
			}
		),
		description = intro
	) {
		if (!LocalInWizard.current) {
			Text(intro, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 4.dp))
		}
		val error = result?.let { historyCheckError(source, it) }
		// only a key Koito turned down is the key's fault; the rest is the address's
		val keyError = error.takeIf { source == "koito" && result == HistoryCheck.Rejected }
		val firstError = error.takeIf { keyError == null }
		Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
			OutlinedTextField(
				state = first,
				modifier = Modifier.fillMaxWidth(),
				label = {
					Text(stringResource(when (source) {
						"koito" -> Res.string.label_koito_url
						"lastfm" -> Res.string.label_lastfm_username
						else -> Res.string.label_listenbrainz_username
					}))
				},
				lineLimits = TextFieldLineLimits.SingleLine,
				enabled = !busy && connected == null,
				isError = firstError != null,
				supportingText = {
					when {
						firstError != null -> Text(stringResource(firstError))
						source == "koito" -> Text(stringResource(Res.string.info_koito_url))
						source == "listenbrainz" -> Text(stringResource(Res.string.info_listenbrainz_shared))
					}
				},
				keyboardOptions = KeyboardOptions(
					autoCorrectEnabled = false,
					keyboardType = if (source == "koito") KeyboardType.Uri else KeyboardType.Text
				)
			)
			if (source == "koito") OutlinedSecureTextField(
				state = key,
				modifier = Modifier.fillMaxWidth(),
				label = { Text(stringResource(Res.string.label_api_key)) },
				enabled = !busy && connected == null,
				isError = keyError != null,
				supportingText = { Text(stringResource(keyError ?: Res.string.info_koito_api_key)) }
			)
		}
		if (connected != null) {
			Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
				Row(
					Modifier.fillMaxWidth().padding(16.dp),
					horizontalArrangement = Arrangement.spacedBy(12.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
					Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
					Column {
						Text(stringResource(Res.string.title_connected), style = MaterialTheme.typography.titleMedium)
						connected.listens?.let {
							Text(
								pluralStringResource(Res.plurals.count_listens, it.toInt().coerceAtLeast(0), it),
								style = MaterialTheme.typography.bodyMedium
							)
						}
					}
				}
			}
		} else if (previous.isNotEmpty() && previous != source) {
			Text(
				stringResource(Res.string.info_replaces_history_source, stringResource(historySourceLabel(previous))),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(horizontal = 4.dp)
			)
		}
		Button(
			modifier = Modifier.align(Alignment.End),
			enabled = !busy && first.text.isNotBlank() && (source != "koito" || key.text.isNotBlank()),
			onClick = {
				if (connected != null) {
					backStack.removeLastOrNull()
					return@Button
				}
				busy = true
				scope.launch {
					val value = first.text.toString().trim()
					val check = when (source) {
						"koito" -> koito.check(value, key.text.toString())
						"lastfm" -> lastFm.check(value)
						else -> if (listenBrainz.checkUser(value)) HistoryCheck.Connected(null) else HistoryCheck.Rejected
					}
					if (check is HistoryCheck.Connected) {
						when (source) {
							"koito" -> {
								preferenceManager.koitoUrl = value
								preferenceManager.koitoApiKey = key.text.toString().trim()
							}
							"lastfm" -> preferenceManager.lastFmUsername = value
							else -> preferenceManager.listenBrainzUsername = value
						}
						preferenceManager.listeningHistorySource = source
					}
					result = check
					busy = false
				}
			}
		) {
			when {
				busy -> CircularProgressIndicator(Modifier.size(18.dp))
				connected != null -> Text(stringResource(Res.string.action_done))
				else -> Text(stringResource(Res.string.action_connect))
			}
		}
	}
}

/** Why [check] didn't connect [source], or null when it did. */
private fun historyCheckError(source: String, check: HistoryCheck): StringResource? = when (check) {
	is HistoryCheck.Connected -> null
	HistoryCheck.Unreachable -> if (source == "koito") Res.string.error_koito_unreachable else Res.string.error_history_unreachable
	HistoryCheck.Rejected -> when (source) {
		"koito" -> Res.string.error_koito_rejected
		"lastfm" -> Res.string.error_lastfm_unknown_user
		else -> Res.string.error_no_listenbrainz_user
	}
	HistoryCheck.Unexpected -> if (source == "koito") Res.string.error_koito_unexpected else Res.string.error_history_unreachable
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
