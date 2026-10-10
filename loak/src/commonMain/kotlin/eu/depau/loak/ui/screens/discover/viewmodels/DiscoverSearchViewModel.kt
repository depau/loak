package eu.depau.loak.ui.screens.discover.viewmodels

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.domain.discover.DeezerClient
import eu.depau.loak.domain.discover.DiscoverSearchResults
import eu.depau.loak.domain.discover.MusicBrainzClient
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.screens.search.SearchCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** [recent] with [query] moved (or added) to the top, at most 10 entries. */
fun withRecentSearch(recent: List<String>, query: String): List<String> =
	(listOf(query) + (recent - query)).take(10)

@OptIn(FlowPreview::class)
class DiscoverSearchViewModel(
	private val deezer: DeezerClient,
	private val musicBrainz: MusicBrainzClient,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	val query = TextFieldState()
	val category = MutableStateFlow(SearchCategory.ALL)

	/** Null while the query is blank: the screen shows recent searches instead. */
	val results: StateFlow<UiState<DiscoverSearchResults>?>
		field = MutableStateFlow<UiState<DiscoverSearchResults>?>(null)

	/** Newest first; backed by a preference, so Compose sees changes. */
	val recentSearches: List<String>
		get() = preferenceManager.discoverRecentSearches.lines().filter { it.isNotBlank() }

	init {
		viewModelScope.launch {
			snapshotFlow { query.text.toString().trim() }
				// MusicBrainz's policy is 1 request/s, so a lazy slower debounce when Deezer is off
				.debounce { if (preferenceManager.deezerEnabled) 300.milliseconds else 1_000.milliseconds }
				.collectLatest { search(it) }
		}
	}

	private suspend fun search(query: String) {
		if (query.isEmpty()) {
			results.value = null
			return
		}
		// the last results stay on screen while the new ones load
		results.value = UiState.Loading(results.value?.data)
		results.value = try {
			UiState.Success(
				if (preferenceManager.deezerEnabled) deezer.searchAll(query)
				else musicBrainz.searchMusicBrainz(query).let { (albums, artists) ->
					DiscoverSearchResults(emptyList(), albums, artists)
				}
			)
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			UiState.Error(e)
		}
	}

	/** Runs [recent] again, or remembers the current query (on the keyboard's search key). */
	fun submit(recent: String = query.text.toString()) {
		val q = recent.trim()
		if (q.isEmpty()) return
		if (q != query.text.toString().trim()) query.setTextAndPlaceCursorAtEnd(q)
		preferenceManager.discoverRecentSearches = withRecentSearch(recentSearches, q).joinToString("\n")
	}
}
