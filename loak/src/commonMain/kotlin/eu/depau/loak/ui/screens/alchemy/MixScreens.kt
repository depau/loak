package eu.depau.loak.ui.screens.alchemy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import eu.depau.loak.ui.components.sheets.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.core.UiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * Describe a mix: songs that sound like (CLAP) or are about (lyrics) a description, both run
 * by AudioMuse-AI's own models, no AI service. Only the kinds the server has set up show.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DescribeMixScreen(prompt: String) {
	val audioMuse = koinInject<AudioMuseManager>()
	val repository = koinInject<AudioMuseRepository>()
	val info by audioMuse.info.collectAsState()
	val scope = rememberCoroutineScope()
	val query = rememberTextFieldState(prompt)
	var lyrics by rememberSaveable { mutableStateOf(info?.soundSearch == false) }
	var ideas by remember { mutableStateOf(emptyList<String>()) }
	var concepts by remember { mutableStateOf(emptyList<String>()) }
	// term -> true for more, false for less
	val steering = remember { mutableStateMapOf<String, Boolean>() }
	var result by remember { mutableStateOf<UiState<List<DomainSong>>?>(null) }
	val title = stringResource(Res.string.title_describe_mix)

	LaunchedEffect(Unit) {
		if (info?.soundSearch != true) return@LaunchedEffect
		launch { repository.warmUpSoundSearch() }
		ideas = runCatching { repository.soundIdeas() }.getOrDefault(emptyList())
		concepts = runCatching { repository.soundConcepts() }.getOrDefault(emptyList())
	}

	fun find() {
		val text = query.text.toString().trim()
		if (text.isEmpty()) return
		result = UiState.Loading(result?.data)
		scope.launch {
			result = try {
				UiState.Success(
					if (lyrics) repository.lyricsSearch(text)
					else repository.soundSearch(text, steering = steering.toList())
				)
			} catch (e: Exception) {
				UiState.Error(e)
			}
		}
	}

	ResultScaffold(title, result?.data.orEmpty(), defaultName = query.text.toString()) {
		item {
			Text(
				stringResource(Res.string.info_describe_mix),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		if (info?.soundSearch == true && info?.lyricsSearch == true) item {
			SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
				listOf(false to Res.string.option_sound, true to Res.string.option_lyrics).forEachIndexed { i, (l, label) ->
					SegmentedButton(selected = lyrics == l, onClick = { lyrics = l }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
						Text(stringResource(label))
					}
				}
			}
		}
		item {
			TextField(
				state = query,
				modifier = Modifier.fillMaxWidth(),
				placeholder = { Text(stringResource(if (lyrics) Res.string.hint_describe_lyrics else Res.string.hint_describe_sound)) },
				lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 3),
				onKeyboardAction = { find() }
			)
		}
		if (!lyrics && ideas.isNotEmpty() && result == null) item {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(stringResource(Res.string.title_ideas), style = MaterialTheme.typography.labelLarge)
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					ideas.take(8).forEach { idea ->
						AssistChip(onClick = { query.setTextAndPlaceCursorAtEnd(idea) }, label = { Text(idea) })
					}
				}
			}
		}
		if (!lyrics && concepts.isNotEmpty()) item {
			Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
				Text(stringResource(Res.string.title_more_or_less), style = MaterialTheme.typography.labelLarge)
				Text(
					stringResource(Res.string.info_more_or_less),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
				FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					concepts.forEach { term ->
						val more = steering[term]
						FilterChip(
							selected = more != null,
							onClick = {
								// off -> more -> less -> off
								when (more) {
									null -> steering[term] = true
									true -> steering[term] = false
									false -> steering.remove(term)
								}
							},
							label = { Text(when (more) { true -> "+ $term"; false -> "− $term"; null -> term }) }
						)
					}
				}
			}
		}
		item {
			Button(onClick = ::find, enabled = query.text.isNotBlank() && result !is UiState.Loading) {
				Icon(Icons.Outlined.Search, null, Modifier.size(18.dp))
				Spacer(Modifier.size(6.dp))
				Text(stringResource(Res.string.action_find_songs))
			}
		}
		resultItems(result)
	}
}

/** Song path: AudioMuse-AI's smoothest run of songs from one song to another. */
@Composable
fun SongPathScreen() {
	val repository = koinInject<AudioMuseRepository>()
	val scope = rememberCoroutineScope()
	var start by remember { mutableStateOf<DomainSong?>(null) }
	var end by remember { mutableStateOf<DomainSong?>(null) }
	var steps by rememberSaveable { mutableStateOf(15) }
	var exact by rememberSaveable { mutableStateOf(false) }
	var picking by remember { mutableStateOf<((DomainSong) -> Unit)?>(null) }
	var result by remember { mutableStateOf<UiState<List<DomainSong>>?>(null) }
	val title = stringResource(Res.string.title_song_path)

	LaunchedEffect(Unit) {
		snapshotFlow { listOf(start?.id, end?.id, steps, exact) }.collect {
			val a = start ?: return@collect
			val b = end ?: return@collect
			result = UiState.Loading(result?.data)
			result = try {
				UiState.Success(repository.songPath(a.id, b.id, steps, exact))
			} catch (e: Exception) {
				UiState.Error(e)
			}
		}
	}

	ResultScaffold(title, result?.data.orEmpty(), defaultName = listOfNotNull(start?.title, end?.title).joinToString(" → ")) {
		item {
			Text(
				stringResource(Res.string.info_song_path),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant
			)
		}
		item { PathEnd(Res.string.label_from, start) { picking = { start = it } } }
		item { PathEnd(Res.string.label_to, end) { picking = { end = it } } }
		item {
			Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
				Column(Modifier.padding(16.dp)) {
					Row {
						Text(stringResource(Res.string.label_steps), Modifier.weight(1f))
						Text("$steps", color = MaterialTheme.colorScheme.primary)
					}
					Slider(value = steps.toFloat(), onValueChange = { steps = it.roundToInt() }, valueRange = 3f..50f)
					Row(verticalAlignment = Alignment.CenterVertically) {
						Text(stringResource(Res.string.label_exact_length), Modifier.weight(1f))
						Switch(checked = exact, onCheckedChange = { exact = it })
					}
				}
			}
		}
		resultItems(result)
	}

	picking?.let { onPick ->
		SongPicker(onDismissRequest = { picking = null }) {
			onPick(it)
			picking = null
		}
	}
}

@Composable
private fun PathEnd(label: org.jetbrains.compose.resources.StringResource, song: DomainSong?, onClick: () -> Unit) {
	Surface(onClick = onClick, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
		ListItem(
			overlineContent = { Text(stringResource(label)) },
			leadingContent = song?.let { { CoverArt(coverArtId = it.coverArtId, modifier = Modifier.size(48.dp)) } },
			headlineContent = { Text(song?.title ?: stringResource(Res.string.action_pick_song), maxLines = 1) },
			supportingContent = song?.let { { Text(it.artistName.orEmpty(), maxLines = 1) } },
			colors = ListItemDefaults.colors(containerColor = Color.Transparent)
		)
	}
}

@OptIn(FlowPreview::class)
@Composable
private fun SongPicker(onDismissRequest: () -> Unit, onPick: (DomainSong) -> Unit) {
	val repository = koinInject<AudioMuseRepository>()
	val query = rememberTextFieldState()
	var results by remember { mutableStateOf<UiState<List<DomainSong>>>(UiState.Success(emptyList())) }
	LaunchedEffect(Unit) {
		snapshotFlow { query.text.toString().trim() }.debounce(300).collect { q ->
			if (q.isEmpty()) return@collect
			results = UiState.Loading(results.data)
			results = try { UiState.Success(repository.searchSongs(q)) } catch (e: Exception) { UiState.Error(e) }
		}
	}
	ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
		Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			TextField(
				state = query,
				modifier = Modifier.fillMaxWidth(),
				placeholder = { Text(stringResource(Res.string.hint_search_ingredients)) },
				leadingIcon = { Icon(Icons.Outlined.Search, null) },
				lineLimits = TextFieldLineLimits.SingleLine,
				shape = CircleShape
			)
			if (results is UiState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
			LazyColumn(Modifier.heightIn(max = 520.dp)) {
				items(results.data.orEmpty(), key = { it.id }) { song ->
					ListItem(
						modifier = Modifier.clickable { onPick(song) },
						colors = ListItemDefaults.colors(containerColor = Color.Transparent),
						leadingContent = { CoverArt(coverArtId = song.coverArtId, modifier = Modifier.size(48.dp)) },
						headlineContent = { Text(song.title, maxLines = 1) },
						supportingContent = { Text(song.artistName.orEmpty(), maxLines = 1) }
					)
				}
			}
			Text(
				stringResource(Res.string.info_analysed_only),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(bottom = 16.dp)
			)
		}
	}
}

/** A screen of AudioMuse-AI results with Save and Play at the bottom. */
@Composable
internal fun ResultScaffold(
	title: String,
	songs: List<DomainSong>,
	defaultName: String,
	content: LazyListScope.() -> Unit
) {
	val player = koinInject<MediaPlayerViewModel>()
	var saving by rememberSaveable { mutableStateOf(false) }
	Scaffold(
		topBar = { NestedTopBar(title = { Text(title) }) },
		bottomBar = {
			if (songs.isNotEmpty()) Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(16.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
				) {
					OutlinedButton(onClick = { saving = true }) {
						Icon(Icons.Outlined.PlaylistAdd, null, Modifier.size(18.dp))
						Spacer(Modifier.size(6.dp))
						Text(stringResource(Res.string.action_save))
					}
					Button(onClick = { player.playMix(songs, defaultName.ifBlank { title }) }) {
						Icon(Icons.Filled.Play, null, Modifier.size(18.dp))
						Spacer(Modifier.size(6.dp))
						Text(stringResource(Res.string.action_play))
					}
				}
			}
		}
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = 16.dp, end = 16.dp,
				top = innerPadding.calculateTopPadding(),
				bottom = innerPadding.calculateBottomPadding() + 16.dp
			),
			verticalArrangement = Arrangement.spacedBy(12.dp),
			content = content
		)
	}
	if (saving) SaveSongsSheet(defaultName.take(60), songs) { saving = false }
}

internal fun LazyListScope.resultItems(result: UiState<List<DomainSong>>?) {
	when (result) {
		null -> {}
		is UiState.Error -> item { Text("${result.error.message}", color = MaterialTheme.colorScheme.error) }
		else -> {
			if (result is UiState.Loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
			if (result is UiState.Success && result.data.isEmpty()) item {
				Text(stringResource(Res.string.info_no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
			}
			items(result.data.orEmpty(), key = { it.id }) { song ->
				ListItem(
					leadingContent = { CoverArt(coverArtId = song.coverArtId, modifier = Modifier.size(48.dp)) },
					headlineContent = { Text(song.title, maxLines = 1) },
					supportingContent = { Text(song.artistName.orEmpty(), maxLines = 1) }
				)
			}
		}
	}
}

/** Names and saves [songs] as a playlist of the user's, through AudioMuse-AI. */
@Composable
internal fun SaveSongsSheet(defaultName: String, songs: List<DomainSong>, onDismissRequest: () -> Unit) {
	val repository = koinInject<AudioMuseRepository>()
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()
	val name = rememberTextFieldState(defaultName)
	var busy by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }

	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
			Text(stringResource(Res.string.title_save_mix), style = MaterialTheme.typography.titleLarge)
			TextField(state = name, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.label_name)) }, lineLimits = TextFieldLineLimits.SingleLine)
			Text(
				error ?: stringResource(Res.string.info_save_playlist),
				style = MaterialTheme.typography.bodyMedium,
				color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
			)
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
				TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.action_cancel)) }
				Button(
					enabled = !busy && name.text.isNotBlank(),
					onClick = {
						busy = true
						val n = name.text.toString().trim()
						scope.launch {
							runCatching { repository.savePlaylist(n, songs.map { it.id }) }
								.onSuccess {
									snackBarManager.notify(Res.string.notice_mix_saved, n)
									onDismissRequest()
								}
								.onFailure { error = it.message }
							busy = false
						}
					}
				) {
					if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(stringResource(Res.string.action_save))
				}
			}
		}
	}
}
