package eu.depau.loak.ui.screens.album.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.toBitmask
import eu.depau.loak.domain.models.toDomainFilters
import eu.depau.loak.domain.repositories.AlbumRepository
import eu.depau.loak.ui.core.UiState

class AlbumListViewModel(
	initialListType: DomainAlbumListType = DomainAlbumListType.AlphabeticalByArtist,
	initialFilters: Set<DomainFilter>? = null,
	private val repository: AlbumRepository,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	syncManager: SyncManager
) : ViewModel() {
	val albumsState: StateFlow<UiState<ImmutableList<DomainAlbum>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainAlbum>>>(UiState.Loading())

	val selectedAlbum: StateFlow<DomainAlbum?>
		field = MutableStateFlow(null)

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val rating: StateFlow<Int>
		field = MutableStateFlow(0)

	val listType: StateFlow<DomainAlbumListType>
		field = MutableStateFlow(initialListType)

	val selectedReversed: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedFilters: StateFlow<Set<DomainFilter>>
		field = MutableStateFlow(
			initialFilters ?: preferenceManager.albumFilters.toDomainFilters()
		)

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshAlbums(false) }
		}
		// the library sync writes the cache in the background: reload once it's done
		viewModelScope.launch {
			syncManager.syncState.map { it.isSyncing }.distinctUntilChanged().drop(1)
				.collect { syncing -> if (!syncing) refreshAlbums(false) }
		}
	}

	fun refreshAlbums(fullRefresh: Boolean) {
		viewModelScope.launch {
			repository.getAlbumsFlow(
				fullRefresh,
				listType.value,
				selectedReversed.value,
				selectedFilters.value
			).collect {
				albumsState.value = it
			}
		}
	}

	fun selectAlbum(album: DomainAlbum) {
		viewModelScope.launch {
			selectedAlbum.value = album
			starred.value = repository.isAlbumStarred(album)
			rating.value = repository.getAlbumRating(album)
		}
	}

	fun clearSelection() {
		selectedAlbum.value = null
	}

	fun starAlbum(isStarred: Boolean) {
		viewModelScope.launch {
			val selection = selectedAlbum.value ?: return@launch
			runCatching {
				if (isStarred) {
					repository.starAlbum(selection)
				} else {
					repository.unstarAlbum(selection)
				}
				starred.value = isStarred
			}
		}
	}

	fun setRating(newRating: Int) {
		viewModelScope.launch {
			val selection = selectedAlbum.value ?: return@launch
			runCatching {
				rating.value = newRating
				repository.rateAlbum(selection, newRating)
			}
		}
	}

	fun setListType(newListType: DomainAlbumListType) {
		listType.value = newListType
		refreshAlbums(false)
	}

	fun setReversed(reversed: Boolean) {
		selectedReversed.value = reversed
		refreshAlbums(false)
	}

	fun toggleFilter(filter: DomainFilter) {
		val current = selectedFilters.value
		val newFilters = if (current.contains(filter)) {
			current - filter
		} else {
			current + filter
		}
		selectedFilters.value = newFilters
		preferenceManager.albumFilters = newFilters.toBitmask()
		refreshAlbums(false)
	}

	fun clearError() {
		albumsState.value = UiState.Success(albumsState.value.data ?: persistentListOf())
	}
}
