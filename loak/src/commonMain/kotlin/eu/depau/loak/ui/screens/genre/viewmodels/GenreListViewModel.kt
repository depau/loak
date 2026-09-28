package eu.depau.loak.ui.screens.genre.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainGenre
import eu.depau.loak.domain.repositories.GenreRepository
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.core.inBackground

class GenreListViewModel(
	private val repository: GenreRepository,
	private val sessionManager: SessionManager,
	connectivityManager: ConnectivityManager
) : ViewModel() {
	private val isOnline = connectivityManager.isOnline

	val genresState: StateFlow<UiState<ImmutableList<DomainGenre>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainGenre>>>(UiState.Loading())

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshGenres(false) }
		}
	}

	/** Shows the cache, then updates it from the server when online (no spinner). */
	fun revalidate() {
		if (isOnline.value) refreshGenres(fullRefresh = true, background = true)
	}

	fun refreshGenres(fullRefresh: Boolean, background: Boolean = false) {
		viewModelScope.launch {
			repository.getGenresFlow(fullRefresh)
				.let { if (background) it.inBackground() else it }
				.collect { genresState.value = it }
		}
	}

	fun clearError() {
		genresState.value = UiState.Success(genresState.value.data ?: persistentListOf())
	}
}
