package eu.depau.loak.ui.screens.playlist.dialogs

import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.parsePlaylistName
import eu.depau.loak.ui.components.common.PlaylistBadgedText
import org.koin.compose.koinInject
import eu.depau.loak.generated.resources.title_audiomuse_section
import eu.depau.loak.generated.resources.info_audiomuse_section
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_create
import eu.depau.loak.generated.resources.action_new_playlist
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.hint_filter_playlists
import eu.depau.loak.generated.resources.info_no_matching_playlists
import eu.depau.loak.generated.resources.info_no_playlists
import eu.depau.loak.generated.resources.option_playlist_name
import eu.depau.loak.generated.resources.title_save_to_playlist
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.screens.playlist.viewmodels.PlaylistUpdateDialogViewModel
import eu.depau.loak.ui.screens.playlist.viewmodels.PlaylistUpdateDialogViewModel.Event

/**
 * "Add to playlist": saves straight to the last-used playlist with a "Change" snackbar,
 * or opens the save-to-playlist sheet. [onDismissRequest] runs once the flow is over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistUpdateDialog(
	songs: ImmutableList<DomainSong>,
	playlistToExclude: String? = null,
	onDismissRequest: () -> Unit
) {
	val viewModel = koinViewModel<PlaylistUpdateDialogViewModel>(
		key = songs.joinToString() + playlistToExclude,
		parameters = { parametersOf(songs, playlistToExclude) }
	)
	var sheetShown by rememberSaveable { mutableStateOf(false) }
	var creatingNew by rememberSaveable { mutableStateOf(false) }

	LaunchedEffect(Unit) {
		viewModel.start()
		viewModel.events.collect { event ->
			when (event) {
				Event.ShowSheet -> sheetShown = true
				Event.HideSheet -> sheetShown = false
				Event.Dismiss -> onDismissRequest()
			}
		}
	}

	if (!sheetShown) return

	ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
		contentWindowInsets = { BottomSheetDefaults.windowInsets }
	) {
		val state by viewModel.playlistsState.collectAsState()
		val creating by viewModel.creating.collectAsState()
		var filter by rememberSaveable { mutableStateOf("") }
		LaunchedEffect(Unit) { snapshotFlow { viewModel.filter.text.toString() }.collect { filter = it } }

		Text(
			stringResource(Res.string.title_save_to_playlist),
			style = MaterialTheme.typography.titleLarge,
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
		)
		TextField(
			state = viewModel.filter,
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
			placeholder = { Text(stringResource(Res.string.hint_filter_playlists)) },
			leadingIcon = { Icon(Icons.Outlined.Search, null) },
			lineLimits = TextFieldLineLimits.SingleLine,
			shape = CircleShape,
			// a search pill, not a form field: no underline
			colors = TextFieldDefaults.colors(
				focusedIndicatorColor = Color.Transparent,
				unfocusedIndicatorColor = Color.Transparent
			)
		)

		if (creatingNew) {
			Row(
				modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				TextField(
					state = viewModel.newPlaylistName,
					modifier = Modifier.weight(1f),
					label = { Text(stringResource(Res.string.option_playlist_name)) },
					lineLimits = TextFieldLineLimits.SingleLine
				)
				Button(
					onClick = viewModel::createAndSave,
					enabled = !creating && viewModel.newPlaylistName.text.isNotBlank()
				) {
					if (creating) CircularProgressIndicator(Modifier.size(18.dp))
					else Text(stringResource(Res.string.action_create))
				}
			}
		} else {
			ListItem(
				onClick = { creatingNew = true },
				content = { Text(stringResource(Res.string.action_new_playlist)) },
				leadingContent = {
					Surface(
						modifier = Modifier.size(48.dp),
						shape = MaterialTheme.shapes.medium,
						color = MaterialTheme.colorScheme.primaryContainer
					) {
						Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null) }
					}
				}
			)
		}

		when (val s = state) {
			is UiState.Loading -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
				CircularProgressIndicator()
			}
			is UiState.Error -> Text("${s.error}", Modifier.padding(16.dp))
			is UiState.Success -> {
				val audioMuse = koinInject<PreferenceManager>().audioMuseIntegration
				val (playlists, rebuilt) = s.data
					.map { it to parsePlaylistName(it.name, it.validUntil != null, audioMuse) }
					.filter { (_, name) -> name.display.contains(filter, ignoreCase = true) }
					.partition { (_, name) -> !name.kind.isRebuilt }
				if (playlists.isEmpty() && rebuilt.isEmpty()) {
					Text(
						stringResource(
							if (s.data.isEmpty()) Res.string.info_no_playlists
							else Res.string.info_no_matching_playlists
						),
						Modifier.padding(16.dp)
					)
				}
				LazyColumn(Modifier.heightIn(max = 480.dp)) {
					items(playlists + rebuilt, key = { it.first.id }) { (playlist, name) ->
						if (rebuilt.firstOrNull()?.first == playlist) {
							ListItem(
								headlineContent = { Text(stringResource(Res.string.title_audiomuse_section)) },
								supportingContent = { Text(stringResource(Res.string.info_audiomuse_section)) }
							)
						}
						ListItem(
							onClick = { viewModel.pick(playlist) },
							content = { Text(name.display) },
							supportingContent = {
								PlaylistBadgedText(name.kind) {
									Text(pluralStringResource(Res.plurals.count_songs, playlist.songCount, playlist.songCount))
								}
							},
							leadingContent = {
								CoverArt(
									coverArtId = playlist.coverArtId,
									modifier = Modifier.size(48.dp),
									shape = MaterialTheme.shapes.small
								)
							}
						)
					}
				}
			}
		}
	}
}
