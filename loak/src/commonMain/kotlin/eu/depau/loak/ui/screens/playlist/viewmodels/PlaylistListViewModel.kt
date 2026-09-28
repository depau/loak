package eu.depau.loak.ui.screens.playlist.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainPlaylistListType
import eu.depau.loak.domain.models.toBitmask
import eu.depau.loak.domain.models.toDomainFilters
import eu.depau.loak.domain.repositories.PlaylistRepository
import eu.depau.loak.ui.core.UiState

class PlaylistListViewModel(
	private val repository: PlaylistRepository,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	val playlistsState: StateFlow<UiState<ImmutableList<DomainPlaylist>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainPlaylist>>>(UiState.Loading())

	val selectedPlaylist: StateFlow<DomainPlaylist?>
		field = MutableStateFlow(null)

	val selectedSorting: StateFlow<DomainPlaylistListType>
		field = MutableStateFlow(DomainPlaylistListType.DateAdded)

	val selectedReversed: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedFilters: StateFlow<Set<DomainFilter>>
		field = MutableStateFlow(preferenceManager.playlistFilters.toDomainFilters())

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshPlaylists(false) }
		}
		// reload from the cache when something else changes it
		viewModelScope.launch {
			repository.playlistChanges().collect { refreshPlaylists(false) }
		}
	}

	fun selectPlaylist(playlist: DomainPlaylist) {
		selectedPlaylist.value = playlist
	}

	fun clearSelection() {
		selectedPlaylist.value = null
	}

	fun refreshPlaylists(fullRefresh: Boolean) {
		viewModelScope.launch {
			repository.getPlaylistsFlow(
				fullRefresh,
				selectedSorting.value,
				selectedReversed.value,
				selectedFilters.value
			).collect {
				playlistsState.value = it
			}
		}
	}

	fun setSorting(sorting: DomainPlaylistListType) {
		selectedSorting.value = sorting
		refreshPlaylists(false)
	}

	fun setReversed(reversed: Boolean) {
		selectedReversed.value = reversed
		refreshPlaylists(false)
	}

	fun toggleFilter(filter: DomainFilter) {
		val current = selectedFilters.value
		val newFilters = if (current.contains(filter)) {
			current - filter
		} else {
			current + filter
		}
		selectedFilters.value = newFilters
		preferenceManager.playlistFilters = newFilters.toBitmask()
		refreshPlaylists(false)
	}

	fun clearError() {
		playlistsState.value = UiState.Success(playlistsState.value.data ?: persistentListOf())
	}
}
