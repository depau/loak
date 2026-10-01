package eu.depau.loak.ui.screens.nowPlaying.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.shared.MediaPlayerViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class NowPlayingViewModel(
	private val player: MediaPlayerViewModel,
	private val songRepository: SongRepository
) : ViewModel(), KoinComponent {
	val songIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val songRating: StateFlow<Int>
		field = MutableStateFlow(0)

	init {
		// the star and rating only change on user action, and the player pushes
		// progress updates ~5×/s while playing; re-running Room queries on
		// every emission is needless IO churn, so react to the current song only
		val currentSong = player.uiState
			.map { it.currentSong }
			.distinctUntilChangedBy { it?.id }

		// observed, so a star toggled from the media notification shows up here too
		viewModelScope.launch {
			currentSong
				.flatMapLatest { song ->
					song?.let { songRepository.observeSongStarred(it.id) } ?: flowOf(false)
				}
				.collect { songIsStarred.value = it }
		}

		viewModelScope.launch {
			currentSong.collect { song ->
				if (song != null) songRating.value = songRepository.getSongRating(song)
			}
		}
	}

	fun starSong(starred: Boolean) {
		viewModelScope.launch {
			runCatching {
				player.uiState.value.currentSong?.let { song ->
					// optimistic: a song missing from the cache never makes the flow emit
					songIsStarred.value = starred
					songRepository.setSongStarred(song.id, starred)
				}
			}
		}
	}

	fun rateSong(rating: Int) {
		viewModelScope.launch {
			runCatching {
				player.uiState.value.currentSong?.let { song ->
					songRating.value = rating
					songRepository.rateSong(song, rating)
				}
			}
		}
	}
}
