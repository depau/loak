package eu.depau.loak.ui.screens.alchemy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.AlchemyIngredient
import eu.depau.loak.domain.repositories.AlchemyResult
import eu.depau.loak.domain.repositories.AudioMuseRepository
import eu.depau.loak.ui.core.UiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class IngredientKind { Songs, Artists, Playlists, Moods }

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AlchemyViewModel(
	seed: AlchemyIngredient?,
	private val repository: AudioMuseRepository,
	audioMuse: AudioMuseManager
) : ViewModel() {
	val ingredients: StateFlow<List<AlchemyIngredient>>
		field = MutableStateFlow(listOfNotNull(seed))

	/** AudioMuse-AI's temperature: 0 always the closest songs, higher more surprises. */
	val temperature: StateFlow<Float>
		field = MutableStateFlow(1f)

	val songCount: StateFlow<Int>
		field = MutableStateFlow(audioMuse.info.value?.alchemyDefaultSongs?.coerceAtMost(100) ?: 50)

	val maxSongs = audioMuse.info.value?.alchemyMaxSongs?.coerceAtMost(200) ?: 200

	/** Null until there's something added to mix. */
	val result: StateFlow<UiState<AlchemyResult>?>
		field = MutableStateFlow<UiState<AlchemyResult>?>(null)

	val pickerKind: StateFlow<IngredientKind>
		field = MutableStateFlow(IngredientKind.Songs)

	val pickerQuery: StateFlow<String>
		field = MutableStateFlow("")

	val pickerResults: StateFlow<UiState<List<AlchemyIngredient>>>
		field = MutableStateFlow<UiState<List<AlchemyIngredient>>>(UiState.Success(emptyList()))

	init {
		// the mix follows every change, a moment after the last one
		combine(ingredients, temperature, songCount) { i, t, n -> Triple(i, t, n) }
			.debounce(400)
			.mapLatest { (items, t, n) ->
				if (items.none { it.add }) return@mapLatest null
				result.value = UiState.Loading(result.value?.data)
				try {
					UiState.Success(repository.alchemy(items, n, t))
				} catch (e: Exception) {
					UiState.Error(e)
				}
			}
			.onEach { result.value = it }
			.launchIn(viewModelScope)

		combine(pickerKind, pickerQuery) { k, q -> k to q }
			.debounce(300)
			.mapLatest { (kind, query) ->
				if (query.isBlank() && kind != IngredientKind.Moods) return@mapLatest UiState.Success(emptyList())
				try {
					UiState.Success(when (kind) {
						IngredientKind.Songs -> repository.searchSongs(query).map { it.toIngredient() }
						IngredientKind.Artists -> repository.searchArtists(query)
						IngredientKind.Playlists -> repository.searchPlaylists(query)
						IngredientKind.Moods -> repository.moods().filter { it.label.contains(query, ignoreCase = true) }
					})
				} catch (e: Exception) {
					UiState.Error(e)
				}
			}
			.onEach { pickerResults.value = it }
			.launchIn(viewModelScope)
	}

	fun setKind(kind: IngredientKind) { pickerKind.value = kind }
	fun setQuery(query: String) { pickerQuery.value = query }
	fun setTemperature(t: Float) { temperature.value = t }
	fun setSongCount(n: Int) { songCount.value = n }

	/** Adds [ingredient], or flips it to add/take away if it's already in. */
	fun put(ingredient: AlchemyIngredient) {
		ingredients.value = ingredients.value.filterNot { it.id == ingredient.id && it.type == ingredient.type } + ingredient
	}

	fun remove(ingredient: AlchemyIngredient) {
		ingredients.value = ingredients.value - ingredient
	}

	fun clear() { ingredients.value = emptyList() }

	suspend fun save(name: String, asRadio: Boolean) {
		val mix = (result.value as? UiState.Success)?.data ?: return
		if (asRadio) repository.saveRadio(name, mix, songCount.value, temperature.value)
		else repository.savePlaylist(name, mix.songs.map { it.id })
	}

	fun launchSave(name: String, asRadio: Boolean, onDone: (Result<Unit>) -> Unit) {
		viewModelScope.launch { onDone(runCatching { save(name, asRadio) }) }
	}
}

fun DomainSong.toIngredient(add: Boolean = true) = AlchemyIngredient(
	id = id,
	type = AlchemyIngredient.Type.Song,
	label = title,
	detail = artistName,
	coverArtId = coverArtId,
	add = add
)
