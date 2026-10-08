package eu.depau.loak.ui.screens.queue.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.SongRepository

class QueueViewModel(
	private val songRepository: SongRepository,
	val downloadManager: DownloadManager
) : ViewModel() {
	val allDownloads = downloadManager.allDownloads

	/** The song whose options sheet is open. */
	val selected: StateFlow<Selection?>
		field = MutableStateFlow(null)

	val selectedSongIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	fun select(song: DomainSong, index: Int?) {
		selected.value = Selection(song, index)
		viewModelScope.launch {
			selectedSongIsStarred.value = songRepository.isSongStarred(song)
			selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun clearSelection() {
		selected.value = null
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

/** A [song] at [index] of the queue, or (null) an Autoplay song. */
data class Selection(val song: DomainSong, val index: Int?)
