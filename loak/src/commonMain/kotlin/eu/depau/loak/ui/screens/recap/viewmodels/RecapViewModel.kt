package eu.depau.loak.ui.screens.recap.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.domain.history.RecapData
import eu.depau.loak.domain.history.RecapPeriod
import eu.depau.loak.domain.repositories.HomeRepository
import eu.depau.loak.ui.core.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Recap screen's state: one period at a time, recomputed when the period changes.
 *
 * The source is read fresh on every load, so switching it in settings and coming back shows the
 * new one rather than a Recap built from the old.
 */
class RecapViewModel(private val homeRepository: HomeRepository) : ViewModel() {
	private val _state = MutableStateFlow<UiState<RecapData>>(UiState.Loading())
	val state: StateFlow<UiState<RecapData>> = _state.asStateFlow()

	private val _period = MutableStateFlow(RecapPeriod.Month)
	val period: StateFlow<RecapPeriod> = _period.asStateFlow()

	private var loadJob: Job? = null

	init {
		load()
	}

	fun select(period: RecapPeriod) {
		if (_period.value == period) return
		_period.value = period
		load()
	}

	/** Rebuilds what is on screen; the button on a failure and a source change both come here. */
	fun load() {
		loadJob?.cancel()
		_state.value = UiState.Loading()
		loadJob = viewModelScope.launch {
			_state.value = try {
				UiState.Success(homeRepository.recap(_period.value))
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				UiState.Error(e)
			}
		}
	}
}
