package eu.depau.loak.ui.screens.song.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongListType
import eu.depau.loak.domain.models.toBitmask
import eu.depau.loak.domain.models.toDomainFilters
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.ui.core.UiState

class SongListViewModel(
	initialListType: DomainSongListType = DomainSongListType.FrequentlyPlayed,
	initialFilters: Set<DomainFilter>? = null,
	private val repository: SongRepository,
	private val downloadManager: DownloadManager,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	connectivityManager: ConnectivityManager,
	syncManager: SyncManager
) : ViewModel() {
	val songsState: StateFlow<UiState<ImmutableList<DomainSong>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainSong>>>(UiState.Loading())

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = persistentListOf()
		)

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val selectedSorting: StateFlow<DomainSongListType>
		field = MutableStateFlow(initialListType)

	val selectedReversed: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedFilters: StateFlow<Set<DomainFilter>>
		field = MutableStateFlow(
			initialFilters ?: preferenceManager.songFilters.toDomainFilters()
		)

	val isOnline = connectivityManager.isOnline

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshSongs(false) }
		}
		// the library sync writes the cache in the background: reload once it's done
		viewModelScope.launch {
			syncManager.syncState.map { it.isSyncing }.distinctUntilChanged().drop(1)
				.collect { syncing -> if (!syncing) refreshSongs(false) }
		}
	}

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			selectedSong.value = song
			starred.value = repository.isSongStarred(song)
			selectedSongRating.value = repository.getSongRating(song)
		}
	}

	fun clearSelection() {
		selectedSong.value = null
	}

	fun refreshSongs(fullRefresh: Boolean) {
		viewModelScope.launch {
			repository.getSongsFlow(
				fullRefresh,
				selectedSorting.value,
				selectedReversed.value,
				selectedFilters.value
			).collect {
				songsState.value = it
			}
		}
	}

	fun starSong(isStarred: Boolean) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				if (isStarred) {
					repository.starSong(selection)
				} else {
					repository.unstarSong(selection)
				}
				starred.value = isStarred
				refreshSongs(false)
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				repository.rateSong(selection, rating)
				selectedSongRating.value = rating
			}
		}
	}

	fun setSorting(sorting: DomainSongListType) {
		selectedSorting.value = sorting
		refreshSongs(false)
	}

	fun setReversed(reversed: Boolean) {
		selectedReversed.value = reversed
		refreshSongs(false)
	}

	fun toggleFilter(filter: DomainFilter) {
		val current = selectedFilters.value
		val newFilters = if (current.contains(filter)) {
			current - filter
		} else {
			current + filter
		}
		selectedFilters.value = newFilters
		preferenceManager.songFilters = newFilters.toBitmask()
		refreshSongs(false)
	}

	fun clearError() {
		songsState.value = UiState.Success(songsState.value.data ?: persistentListOf())
	}

	fun downloadSong(song: DomainSong) {
		downloadManager.downloadSong(song)
	}

	fun cancelDownload(songId: String) {
		downloadManager.cancelDownload(songId)
	}

	fun deleteDownload(songId: String) {
		downloadManager.deleteDownload(songId)
	}
}
