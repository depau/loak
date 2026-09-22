package eu.depau.loak.ui.screens.radio.viewmodels

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.domain.repositories.RadioRepository
import eu.depau.loak.ui.core.UiState

class RadioListViewModel(
	private val repository: RadioRepository,
	private val sessionManager: SessionManager
) : ViewModel() {
	val radiosState: StateFlow<UiState<ImmutableList<DomainRadio>>>
		field = MutableStateFlow<UiState<ImmutableList<DomainRadio>>>(UiState.Loading())

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refreshRadios(false) }
		}
	}

	fun refreshRadios(fullRefresh: Boolean) {
		viewModelScope.launch {
			repository.getRadiosFlow(fullRefresh).collect {
				radiosState.value = it
			}
		}
	}

	fun clearError() {
		radiosState.value = UiState.Success(radiosState.value.data ?: persistentListOf())
	}
}
