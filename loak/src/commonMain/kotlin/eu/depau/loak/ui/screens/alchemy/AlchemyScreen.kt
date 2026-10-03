package eu.depau.loak.ui.screens.alchemy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.repositories.AlchemyIngredient
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.filled.Sparkle
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.Artist
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.icons.outlined.PlaylistPlay
import eu.depau.loak.icons.outlined.Radio
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.core.UiState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt

/**
 * Song alchemy: songs, artists, playlists and moods to mix in or take away; AudioMuse-AI finds
 * the songs in between. Play it, save it once, or keep it as a radio that refills itself.
 */
@Composable
fun AlchemyScreen(seed: AlchemyIngredient?) {
	val viewModel = koinViewModel<AlchemyViewModel>(key = "alchemy-$seed", parameters = { parametersOf(seed) })
	val player = koinInject<MediaPlayerViewModel>()
	val ingredients by viewModel.ingredients.collectAsState()
	val temperature by viewModel.temperature.collectAsState()
	val songCount by viewModel.songCount.collectAsState()
	val result by viewModel.result.collectAsState()
	var picking by rememberSaveable { mutableStateOf(false) }
	var saving by rememberSaveable { mutableStateOf(false) }
	val title = stringResource(Res.string.title_song_alchemy)
	val songs = result?.data?.songs.orEmpty()

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(title) },
				actions = {
					if (ingredients.isNotEmpty()) TextButton(onClick = viewModel::clear) {
						Text(stringResource(Res.string.action_clear))
					}
				}
			)
		},
		bottomBar = {
			Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(16.dp),
					horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
				) {
					OutlinedButton(onClick = { saving = true }, enabled = songs.isNotEmpty()) {
						Icon(Icons.Outlined.PlaylistAdd, null, Modifier.size(18.dp))
						Spacer(Modifier.size(6.dp))
						Text(stringResource(Res.string.action_save))
					}
					Button(onClick = { player.playInstantMix(songs, title) }, enabled = songs.isNotEmpty()) {
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
			verticalArrangement = Arrangement.spacedBy(6.dp)
		) {
			item {
				Text(
					stringResource(Res.string.info_song_alchemy),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(4.dp)
				)
			}
			items(ingredients, key = { "${it.type}-${it.id}" }) { ingredient ->
				IngredientRow(
					ingredient = ingredient,
					onToggle = { viewModel.put(ingredient.copy(add = it)) },
					onRemove = { viewModel.remove(ingredient) }
				)
			}
			item {
				OutlinedButton(onClick = { picking = true }, modifier = Modifier.padding(vertical = 4.dp)) {
					Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
					Spacer(Modifier.size(6.dp))
					Text(stringResource(Res.string.action_add_ingredient))
				}
			}
			item {
				Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
					Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
						Text(stringResource(Res.string.label_variety))
						Slider(value = temperature, onValueChange = viewModel::setTemperature, valueRange = 0f..2f)
						Row {
							Text(stringResource(Res.string.label_focused), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
							Text(stringResource(Res.string.label_adventurous), style = MaterialTheme.typography.bodySmall)
						}
						Spacer(Modifier.height(8.dp))
						Row {
							Text(stringResource(Res.string.label_songs_count), Modifier.weight(1f))
							Text("$songCount", color = MaterialTheme.colorScheme.primary)
						}
						Slider(
							value = songCount.toFloat(),
							onValueChange = { viewModel.setSongCount(it.roundToInt()) },
							valueRange = 10f..viewModel.maxSongs.toFloat()
						)
					}
				}
			}
			item {
				Row(Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
					Text(stringResource(Res.string.title_the_mix), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
					if (songs.isNotEmpty()) Text(
						stringResource(Res.string.info_mix_count, songs.size),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				if (result is UiState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
			}
			when (val r = result) {
				null -> item {
					Text(stringResource(Res.string.info_alchemy_needs_add), Modifier.padding(4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				is UiState.Error -> item { Text("${r.error.message}", Modifier.padding(4.dp), color = MaterialTheme.colorScheme.error) }
				else -> items(songs, key = { it.id }) { song ->
					ListItem(
						leadingContent = { CoverArt(coverArtId = song.coverArtId, modifier = Modifier.size(48.dp)) },
						headlineContent = { Text(song.title, maxLines = 1) },
						supportingContent = { Text(song.artistName.orEmpty(), maxLines = 1) }
					)
				}
			}
		}
	}

	if (picking) IngredientPicker(viewModel, onDismissRequest = { picking = false })
	if (saving) SaveMixSheet(viewModel, onDismissRequest = { saving = false })
}

@Composable
private fun IngredientLead(ingredient: AlchemyIngredient) {
	when (ingredient.type) {
		AlchemyIngredient.Type.Song -> CoverArt(coverArtId = ingredient.coverArtId, modifier = Modifier.size(48.dp))
		else -> Box(
			Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
			contentAlignment = Alignment.Center
		) {
			Icon(
				when (ingredient.type) {
					AlchemyIngredient.Type.Artist -> Icons.Outlined.Artist
					AlchemyIngredient.Type.Playlist -> Icons.Outlined.PlaylistPlay
					AlchemyIngredient.Type.Radio -> Icons.Outlined.Radio
					else -> Icons.Filled.Sparkle
				},
				null
			)
		}
	}
}

/** An ingredient with its add / take-away switch. */
@Composable
private fun IngredientRow(ingredient: AlchemyIngredient, onToggle: (Boolean) -> Unit, onRemove: () -> Unit) {
	Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
		Row(
			modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			IngredientLead(ingredient)
			Column(Modifier.weight(1f)) {
				Text(ingredient.label, maxLines = 1)
				ingredient.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant) }
			}
			SingleChoiceSegmentedButtonRow {
				SegmentedButton(selected = ingredient.add, onClick = { onToggle(true) }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = {}) {
					Text("+")
				}
				SegmentedButton(selected = !ingredient.add, onClick = { onToggle(false) }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = {}) {
					Text("−")
				}
			}
			IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, null) }
		}
	}
}

@Composable
private fun IngredientPicker(viewModel: AlchemyViewModel, onDismissRequest: () -> Unit) {
	val kind by viewModel.pickerKind.collectAsState()
	val results by viewModel.pickerResults.collectAsState()
	val ingredients by viewModel.ingredients.collectAsState()
	val query = rememberTextFieldState(viewModel.pickerQuery.value)
	LaunchedEffect(Unit) { snapshotFlow { query.text.toString() }.collect(viewModel::setQuery) }

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
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				IngredientKind.entries.forEach { k ->
					FilterChip(
						selected = k == kind,
						onClick = { viewModel.setKind(k) },
						label = {
							Text(stringResource(when (k) {
								IngredientKind.Songs -> Res.string.filter_songs
								IngredientKind.Artists -> Res.string.filter_artists
								IngredientKind.Playlists -> Res.string.filter_playlists
								IngredientKind.Moods -> Res.string.filter_moods
							}))
						}
					)
				}
			}
			if (results is UiState.Loading) LinearProgressIndicator(Modifier.fillMaxWidth())
			(results as? UiState.Error)?.let { Text("${it.error.message}", color = MaterialTheme.colorScheme.error) }
			LazyColumn(Modifier.heightIn(max = 520.dp)) {
				items(results.data.orEmpty(), key = { "${it.type}-${it.id}" }) { item ->
					val current = ingredients.find { it.id == item.id && it.type == item.type }
					ListItem(
						leadingContent = { IngredientLead(item) },
						headlineContent = { Text(item.label, maxLines = 1) },
						supportingContent = item.detail?.let { { Text(it, maxLines = 1) } },
						trailingContent = {
							Row {
								FilterChip(
									selected = current?.add == true,
									onClick = { viewModel.put(item.copy(add = true)) },
									label = { Text("+") }
								)
								Spacer(Modifier.size(4.dp))
								FilterChip(
									selected = current?.add == false,
									onClick = { viewModel.put(item.copy(add = false)) },
									label = { Text("−") }
								)
							}
						}
					)
				}
			}
			if (kind == IngredientKind.Songs) Text(
				stringResource(Res.string.info_analysed_only),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(bottom = 16.dp)
			)
		}
	}
}

@Composable
private fun SaveMixSheet(viewModel: AlchemyViewModel, onDismissRequest: () -> Unit) {
	val snackBarManager = koinInject<SnackBarManager>()
	var asRadio by rememberSaveable { mutableStateOf(false) }
	val name = rememberTextFieldState(viewModel.ingredients.value.filter { it.add }.joinToString(" + ") { it.label }.take(60))
	var busy by rememberSaveable { mutableStateOf(false) }
	var error by rememberSaveable { mutableStateOf<String?>(null) }

	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
			Text(stringResource(Res.string.title_save_mix), style = MaterialTheme.typography.titleLarge)
			SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
				listOf(false to Res.string.option_save_playlist, true to Res.string.option_save_radio).forEachIndexed { i, (radio, label) ->
					SegmentedButton(selected = asRadio == radio, onClick = { asRadio = radio }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
						Text(stringResource(label))
					}
				}
			}
			TextField(state = name, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.label_name)) }, lineLimits = TextFieldLineLimits.SingleLine)
			Text(
				error ?: stringResource(if (asRadio) Res.string.info_save_radio else Res.string.info_save_playlist),
				style = MaterialTheme.typography.bodyMedium,
				color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
			)
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
				TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.action_cancel)) }
				Button(
					enabled = !busy && name.text.isNotBlank(),
					onClick = {
						busy = true
						viewModel.launchSave(name.text.toString().trim(), asRadio) { r ->
							busy = false
							r.onSuccess {
								snackBarManager.notify(
									if (asRadio) Res.string.notice_radio_saved else Res.string.notice_mix_saved,
									name.text.toString().trim()
								)
								onDismissRequest()
							}.onFailure { error = it.message }
						}
					}
				) {
					if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(stringResource(Res.string.action_save))
				}
			}
		}
	}
}
