package eu.depau.loak.ui.screens.artist.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainArtistListType
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.toBitmask
import eu.depau.loak.domain.models.toDomainFilters
import eu.depau.loak.domain.repositories.ArtistRepository
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.core.UiState

class ArtistListViewModel(
	initialListType: DomainArtistListType = DomainArtistListType.AlphabeticalByName,
	initialFilters: Set<DomainFilter>? = null,
	private val repository: ArtistRepository,
	private val albumDao: AlbumDao,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	val artistsState: StateFlow<UiState<ImmutableList<DomainArtist>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainArtist>>>(UiState.Loading())

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedArtist: StateFlow<DomainArtist?>
		field = MutableStateFlow(null)

	val selectedArtistAlbums: StateFlow<ImmutableList<DomainAlbum>?>
		field = MutableStateFlow(null)

	val listType: StateFlow<DomainArtistListType>
		field = MutableStateFlow(initialListType)

	val selectedFilters: StateFlow<Set<DomainFilter>>
		field = MutableStateFlow(
			initialFilters ?: preferenceManager.artistFilters.toDomainFilters()
		)

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshArtists(false) }
		}
	}

	fun refreshArtists(fullRefresh: Boolean) {
		viewModelScope.launch {
			repository.getArtistsFlow(fullRefresh, listType.value, selectedFilters.value)
				.collect {
					artistsState.value = it
				}
		}
	}

	fun selectArtist(artist: DomainArtist) {
		viewModelScope.launch {
			selectedArtist.value = artist
			val artistAlbums =
				albumDao.getAlbumsByArtist(artist.id).firstOrNull() ?: emptyList()
			selectedArtistAlbums.value = artistAlbums.map { it.toDomainModel() }.toImmutableList()
			starred.value = repository.isArtistStarred(artist)
		}
	}

	fun clearSelection() {
		selectedArtist.value = null
	}

	fun starArtist(isStarred: Boolean) {
		val artist = selectedArtist.value ?: return
		viewModelScope.launch {
			runCatching {
				if (isStarred) {
					repository.starArtist(artist)
				} else {
					repository.unstarArtist(artist)
				}
				starred.value = isStarred
			}
		}
	}

	fun addArtistAlbumsToQueue(player: MediaPlayerViewModel) {
		val artist = selectedArtist.value ?: return
		viewModelScope.launch {
			val artistAlbums =
				albumDao.getAlbumsByArtist(artist.id).firstOrNull() ?: emptyList()
			artistAlbums.map { it.toDomainModel() }.forEach { album ->
				player.addToQueue(album)
			}
		}
	}

	fun playArtistAlbumsNext(player: MediaPlayerViewModel) {
		val artist = selectedArtist.value ?: return
		viewModelScope.launch {
			val artistAlbums =
				albumDao.getAlbumsByArtist(artist.id).firstOrNull() ?: emptyList()
			artistAlbums.map { it.toDomainModel() }.forEach { album ->
				player.playNext(album)
			}
		}
	}

	fun setListType(newListType: DomainArtistListType) {
		listType.value = newListType
		refreshArtists(false)
	}

	fun toggleFilter(filter: DomainFilter) {
		val current = selectedFilters.value
		val newFilters = if (current.contains(filter)) {
			current - filter
		} else {
			current + filter
		}
		selectedFilters.value = newFilters
		preferenceManager.artistFilters = newFilters.toBitmask()
		refreshArtists(false)
	}

	fun clearError() {
		artistsState.value = UiState.Success(artistsState.value.data ?: persistentListOf())
	}
}
