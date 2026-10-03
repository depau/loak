package eu.depau.loak.ui.screens.alchemy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.AudioMuseRepository
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.filled.Sparkle
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.theme.warning
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private sealed interface AskMessage {
	data class Mine(val text: String) : AskMessage
	data class Progress(val lines: List<String>, val done: Boolean, val error: String? = null) : AskMessage
}

/**
 * Ask AI for a playlist: a chat with the AI service set up in AudioMuse-AI, which picks songs
 * from the library. It costs whoever runs that service, so nothing is sent until Send; the box
 * at the top says so. [prompt] fills the field (from search), it isn't sent by itself.
 */
@Composable
fun AskAiScreen(prompt: String) {
	val audioMuse = koinInject<AudioMuseManager>()
	val repository = koinInject<AudioMuseRepository>()
	val player = koinInject<MediaPlayerViewModel>()
	val snackBarManager = koinInject<SnackBarManager>()
	val info by audioMuse.info.collectAsState()
	val scope = rememberCoroutineScope()
	val input = rememberTextFieldState(prompt)
	val messages = remember { mutableStateListOf<AskMessage>() }
	var songs by remember { mutableStateOf<List<DomainSong>>(emptyList()) }
	var busy by remember { mutableStateOf(false) }
	var request by remember { mutableStateOf("") }
	val title = stringResource(Res.string.action_ask_ai_playlist)
	val ideas = listOf(
		stringResource(Res.string.idea_ask_1),
		stringResource(Res.string.idea_ask_2),
		stringResource(Res.string.idea_ask_3)
	)

	fun send() {
		val text = input.text.toString().trim()
		if (text.isEmpty() || busy) return
		input.clearText()
		// AudioMuse-AI's chat keeps nothing between requests: a follow-up carries the first one
		request = if (request.isEmpty()) text else "$request. Changes: $text"
		messages += AskMessage.Mine(text)
		messages += AskMessage.Progress(emptyList(), done = false)
		busy = true
		scope.launch {
			val log = mutableListOf<String>()
			fun update(done: Boolean, error: String? = null) {
				messages[messages.lastIndex] = AskMessage.Progress(log.toList(), done, error)
			}
			try {
				audioMuse.stream("chat/api/chatPlaylistStream", buildJsonObject {
					put("userInput", JsonPrimitive(request))
				}) { event ->
					when (event["type"]?.jsonPrimitive?.contentOrNull) {
						"log" -> {
							event["line"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let { log += it }
							update(false)
						}
						"done" -> {
							val ids = (event["response"] as? JsonObject)
								?.get("query_results") as? JsonArray
							songs = repository.songs(ids.orEmpty().mapNotNull {
								(it as? JsonObject)?.get("item_id")?.jsonPrimitive?.contentOrNull
							})
							update(true)
						}
						"error" -> update(true, event["message"]?.jsonPrimitive?.contentOrNull ?: event.toString())
					}
				}
				if (messages.last() is AskMessage.Progress && !(messages.last() as AskMessage.Progress).done) update(true)
			} catch (e: Exception) {
				update(true, e.message ?: e.toString())
			}
			busy = false
		}
	}

	Scaffold(
		topBar = { NestedTopBar(title = { Text(title) }) },
		bottomBar = {
			Surface { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
				if (songs.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
					OutlinedButton(onClick = {
						scope.launch {
							runCatching { repository.savePlaylist(request.take(60), songs.map { it.id }) }
								.onSuccess { snackBarManager.notify(Res.string.notice_mix_saved, request.take(60)) }
						}
					}) {
						Icon(Icons.Outlined.PlaylistAdd, null, Modifier.size(18.dp))
						Spacer(Modifier.size(6.dp))
						Text(stringResource(Res.string.action_save))
					}
					Button(onClick = { player.playMix(songs, title) }) {
						Icon(Icons.Filled.Play, null, Modifier.size(18.dp))
						Spacer(Modifier.size(6.dp))
						Text(stringResource(Res.string.action_play))
					}
				}
				TextField(
					state = input,
					modifier = Modifier.fillMaxWidth(),
					placeholder = {
						Text(stringResource(if (messages.isEmpty()) Res.string.hint_ask_ai else Res.string.hint_ask_ai_changes))
					},
					lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 4),
					shape = RoundedCornerShape(28.dp),
					colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
					trailingIcon = {
						IconButton(onClick = ::send, enabled = !busy && input.text.isNotBlank()) {
							if (busy) CircularProgressIndicator(Modifier.size(20.dp))
							else Icon(Icons.Outlined.ChevronForward, stringResource(Res.string.action_send))
						}
					}
				)
			} }
		}
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = 16.dp, end = 16.dp,
				top = innerPadding.calculateTopPadding(),
				bottom = innerPadding.calculateBottomPadding() + 16.dp
			),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			item {
				Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.warning.copy(alpha = 0.16f)) {
					Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
						Icon(Icons.Filled.Sparkle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.warning)
						Text(
							stringResource(Res.string.info_ask_ai_cost, info?.aiProviderName ?: ""),
							style = MaterialTheme.typography.bodySmall
						)
					}
				}
			}
			if (messages.isEmpty()) item {
				Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
					Text(stringResource(Res.string.title_ideas), style = MaterialTheme.typography.labelLarge)
					ideas.forEach { idea ->
						AssistChip(onClick = {
							input.setTextAndPlaceCursorAtEnd(idea)
						}, label = { Text(idea) })
					}
				}
			}
			items(messages.size) { i ->
				when (val m = messages[i]) {
					is AskMessage.Mine -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
						Surface(
							shape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
							color = MaterialTheme.colorScheme.primary,
							contentColor = MaterialTheme.colorScheme.onPrimary,
							modifier = Modifier.widthIn(max = 320.dp)
						) { Text(m.text, Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
					}
					is AskMessage.Progress -> Surface(
						shape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
						color = MaterialTheme.colorScheme.surfaceContainerHigh,
						modifier = Modifier.widthIn(max = 360.dp)
					) {
						Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
							m.lines.takeLast(6).forEach { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
							if (!m.done) CircularProgressIndicator(Modifier.size(18.dp))
							m.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
						}
					}
				}
			}
			items(songs, key = { it.id }) { song ->
				ListItem(
					leadingContent = { CoverArt(coverArtId = song.coverArtId, modifier = Modifier.size(48.dp)) },
					headlineContent = { Text(song.title, maxLines = 1) },
					supportingContent = { Text(song.artistName.orEmpty(), maxLines = 1) }
				)
			}
		}
	}
}
