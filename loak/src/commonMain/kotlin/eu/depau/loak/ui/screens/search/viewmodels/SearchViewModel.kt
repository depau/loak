package eu.depau.loak.ui.screens.search.viewmodels

import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.repositories.AudioMuseRepository
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.SearchRepository
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.screens.search.SearchCategory
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class SearchViewModel(
	private val repository: SearchRepository,
	private val audioMuseRepository: AudioMuseRepository,
	audioMuse: AudioMuseManager,
	private val preferenceManager: PreferenceManager,
	connectivityManager: ConnectivityManager
) : ViewModel() {
	val searchState: StateFlow<UiState<List<Any>>>
		field = MutableStateFlow<UiState<List<Any>>>(UiState.Success(emptyList()))

	val searchHistory: StateFlow<List<String>>
		field = MutableStateFlow<List<String>>(emptyList())

	val searchQuery = TextFieldState()

	/** Kept here so it survives opening a result and coming back. */
	val selectedCategory = MutableStateFlow(SearchCategory.ALL)

	val isOnline = connectivityManager.isOnline

	val gridState = LazyGridState()

	init {
		viewModelScope.launch {
			snapshotFlow { searchQuery.text }
				.debounce(300.milliseconds)
				.collectLatest { queryText -> search(queryText.toString()) }
		}
	}

	val audioMuseInfo = audioMuse.info

	/** "Sounds like" results for [soundQuery]; searched on submit, not as you type. */
	val soundState: StateFlow<UiState<List<DomainSong>>?>
		field = MutableStateFlow<UiState<List<DomainSong>>?>(null)
	private var soundQuery = ""

	fun searchBySound(query: String = searchQuery.text.toString()) {
		val q = query.trim()
		if (q.isBlank() || audioMuseInfo.value?.soundSearch != true || !preferenceManager.audioMuseDescribe || q == soundQuery && soundState.value !is UiState.Error) return
		soundQuery = q
		viewModelScope.launch {
			soundState.value = UiState.Loading()
			soundState.value = try {
				UiState.Success(audioMuseRepository.soundSearch(q))
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				UiState.Error(e)
			}
		}
	}

	/** Runs the current query again, e.g. after something in the results was deleted. */
	fun refresh() {
		viewModelScope.launch { search(searchQuery.text.toString()) }
	}

	private suspend fun search(query: String) {
		// typing something else drops the last "sounds like"; Sound searches again
		if (query.trim() != soundQuery) {
			soundQuery = ""
			soundState.value = null
			if (selectedCategory.value == SearchCategory.SOUND) searchBySound(query)
		}
		if (query.isBlank()) {
			searchState.value = UiState.Success(emptyList())
			return
		}
		searchState.value = UiState.Loading()
		try {
			searchState.value = UiState.Success(repository.search(query))
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			searchState.value = UiState.Error(e)
		}
	}

	fun addToSearchHistory(query: String) {
		if (query.isBlank()) return
		val current = searchHistory.value.toMutableList()
		if (current.contains(query)) {
			current.remove(query)
		}
		current.add(0, query)
		searchHistory.value = current.take(10)
	}

	fun removeFromSearchHistory(query: String) {
		val current = searchHistory.value.toMutableList()
		current.remove(query)
		searchHistory.value = current
	}
}
