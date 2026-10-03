package eu.depau.loak.ui.screens.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.AlchemyIngredient
import eu.depau.loak.domain.repositories.AudioMuseRepository
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.PlaylistAdd
import eu.depau.loak.icons.outlined.Shuffle
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.screens.alchemy.SaveSongsSheet
import eu.depau.loak.ui.screens.alchemy.resultItems
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** A mood's page: the library songs AudioMuse-AI finds closest to it. */
@Composable
fun MoodScreen(mood: AlchemyIngredient) {
	val repository = koinInject<AudioMuseRepository>()
	val player = koinInject<MediaPlayerViewModel>()
	val info by koinInject<AudioMuseManager>().info.collectAsState()
	val result by produceState<UiState<List<DomainSong>>>(UiState.Loading(), mood) {
		value = try {
			UiState.Success(repository.alchemy(listOf(mood), info?.alchemyDefaultSongs ?: 50, 1f).songs)
		} catch (e: Exception) {
			UiState.Error(e)
		}
	}
	val songs = result.data.orEmpty()
	var saving by remember { mutableStateOf(false) }

	Scaffold(topBar = { NestedTopBar(title = { Text(mood.label) }) }) { innerPadding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(16.dp, innerPadding.calculateTopPadding(), 16.dp, innerPadding.calculateBottomPadding() + 16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			item {
				Surface(Modifier.fillMaxWidth().height(120.dp), shape = MaterialTheme.shapes.large, color = moodColor(mood.label), contentColor = Color.White) {
					Box(Modifier.padding(16.dp)) {
						Text(stringResource(Res.string.overline_mood), style = MaterialTheme.typography.labelSmall)
						Text(mood.label, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.BottomStart))
					}
				}
			}
			item {
				Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
					FilledTonalIconButton(onClick = { player.playMix(songs.shuffled(), mood.label) }, enabled = songs.isNotEmpty()) {
						Icon(Icons.Outlined.Shuffle, stringResource(Res.string.action_shuffle))
					}
					Button(onClick = { player.playMix(songs, mood.label) }, enabled = songs.isNotEmpty()) {
						Icon(Icons.Filled.Play, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(Res.string.action_play))
					}
					FilledTonalButton(onClick = { saving = true }, enabled = songs.isNotEmpty()) {
						Icon(Icons.Outlined.PlaylistAdd, null, Modifier.size(18.dp)); Spacer(Modifier.size(6.dp)); Text(stringResource(Res.string.action_save))
					}
				}
			}
			resultItems(result)
		}
	}
	if (saving) SaveSongsSheet(mood.label, songs) { saving = false }
}
