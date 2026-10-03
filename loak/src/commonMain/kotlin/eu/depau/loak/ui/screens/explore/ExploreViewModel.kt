package eu.depau.loak.ui.screens.explore

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.data.database.dao.GenreDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainGenre
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.AlchemyIngredient
import eu.depau.loak.domain.repositories.AudioMuseRepository
import eu.depau.loak.domain.repositories.HomeRepository
import eu.depau.loak.domain.repositories.SoundMapPoint
import eu.depau.loak.util.IoDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ExploreUiState(
	val genres: List<DomainGenre> = emptyList(),
	val genreCount: Int = 0,
	val moods: List<AlchemyIngredient> = emptyList(),
	val soundMap: List<SoundMapPoint> = emptyList(),
	val forgotten: List<DomainAlbum> = emptyList(),
	val neverPlayed: List<DomainAlbum> = emptyList(),
	val deepCuts: List<DomainSong> = emptyList()
)

/** Explore's shelves: the library's own, then AudioMuse-AI's when it's connected. */
class ExploreViewModel(
	private val home: HomeRepository,
	private val genreDao: GenreDao,
	private val audioMuseRepository: AudioMuseRepository,
	private val audioMuse: AudioMuseManager
) : ViewModel() {
	val state: StateFlow<ExploreUiState>
		field = MutableStateFlow(ExploreUiState())

	init { refresh() }

	fun refresh() = viewModelScope.launch {
		val library = home.library()
		val all = withContext(IoDispatcher) { genreDao.getGenresWithAlbums().map { it.toDomainModel() } }
		val byName = all.associateBy { it.name }
		// the genres played most, topped up with the biggest ones
		val genres = (home.topGenres(library, 5).mapNotNull { byName[it] } + all.sortedByDescending { it.albumCount })
			.distinctBy { it.name }.take(5)
		state.update {
			it.copy(
				genres = genres,
				genreCount = all.size,
				forgotten = home.forgottenFavourites(library, null),
				neverPlayed = home.neverPlayed(library),
				deepCuts = home.deepCuts(library)
			)
		}
		if (audioMuse.info.value == null) return@launch
		launch { runCatching { audioMuseRepository.moods() }.onSuccess { m -> state.update { it.copy(moods = m) } } }
		launch { runCatching { audioMuseRepository.soundMap() }.onSuccess { m -> state.update { it.copy(soundMap = m) } } }
	}
}
