package eu.depau.loak.ui.screens.nowPlaying.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.shared.MediaPlayerViewModel

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
		// progress updates ~5×/s while playing; re-running two Room queries on
		// every emission is needless IO churn, so react to the current song only
		viewModelScope.launch {
			player.uiState
				.map { it.currentSong?.id }
				.distinctUntilChanged()
				.collect { songId ->
					val song = player.uiState.value.currentSong
					if (song == null) return@collect
					songIsStarred.value = songRepository.isSongStarred(song)
					songRating.value = songRepository.getSongRating(song)
				}
		}
	}

	fun starSong(starred: Boolean) {
		viewModelScope.launch {
			runCatching {
				player.uiState.value.currentSong?.let { song ->
					songIsStarred.value = starred
					if (starred) {
						songRepository.starSong(song)
					} else {
						songRepository.unstarSong(song)
					}
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
