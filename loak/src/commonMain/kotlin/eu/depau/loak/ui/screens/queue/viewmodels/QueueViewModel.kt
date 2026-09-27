package eu.depau.loak.ui.screens.queue.viewmodels

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.SongRepository

class QueueViewModel(
	private val songRepository: SongRepository,
	connectivityManager: ConnectivityManager,
	downloadManager: DownloadManager
) : ViewModel() {
	val listState = LazyListState()
	val isOnline = connectivityManager.isOnline
	val downloadedSongs = downloadManager.downloadedSongs

	/** Queue index of the song whose options sheet is open. */
	val selectedIndex: StateFlow<Int?>
		field = MutableStateFlow(null)

	val selectedSongIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	fun select(index: Int, song: DomainSong) {
		selectedIndex.value = index
		viewModelScope.launch {
			selectedSongIsStarred.value = songRepository.isSongStarred(song)
			selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun clearSelection() {
		selectedIndex.value = null
	}

	fun star(song: DomainSong, starred: Boolean) {
		viewModelScope.launch {
			runCatching {
				if (starred) songRepository.starSong(song) else songRepository.unstarSong(song)
				selectedSongIsStarred.value = starred
			}
		}
	}

	fun rate(song: DomainSong, rating: Int) {
		viewModelScope.launch {
			runCatching {
				songRepository.rateSong(song, rating)
				selectedSongRating.value = rating
			}
		}
	}
}
