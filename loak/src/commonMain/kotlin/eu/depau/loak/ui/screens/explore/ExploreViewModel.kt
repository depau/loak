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
import eu.depau.loak.domain.repositories.HomeLibrary
import eu.depau.loak.domain.repositories.HomeRepository
import eu.depau.loak.domain.repositories.SoundMapPoint
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
	val deepCuts: List<DomainSong> = emptyList(),
	val loadingLibrary: Boolean = true,
	val loadingMoods: Boolean = false,
	val loadingSoundMap: Boolean = false,
	/** What failed the last library-shelf load, if anything; shown with a retry action. */
	val error: Exception? = null
)

/** Explore's shelves: the library's own, then AudioMuse-AI's when it's connected. */
class ExploreViewModel(
	private val home: HomeRepository? = null,
	private val genreDao: GenreDao,
	private val audioMuseRepository: AudioMuseRepository? = null,
	private val audioMuse: AudioMuseManager? = null,
	/** Test seam: which dispatcher the DAO read runs on; prod uses the platform IO pool. */
	private val ioDispatcher: CoroutineDispatcher = IoDispatcher,
	/** Test seam: replaces the DAO-backed library read; prod keeps `home.library()`. */
	private val libraryLoader: (suspend () -> HomeLibrary)? = null
) : ViewModel() {
	val state: StateFlow<ExploreUiState>
		field = MutableStateFlow(ExploreUiState())

	init { refresh() }

	fun refresh() = viewModelScope.launch {
		state.update { it.copy(loadingLibrary = true, error = null) }
		try {
			val library = libraryLoader?.invoke() ?: checkNotNull(home).library()
			val all = withContext(ioDispatcher) { genreDao.getGenresWithAlbums().map { it.toDomainModel() } }
			val byName = all.associateBy { it.name }
			// the genres played most, topped up with the biggest ones
			val genres = home?.let {
				(it.topGenres(library, 5).mapNotNull { byName[it] } + all.sortedByDescending { it.albumCount })
					.distinctBy { it.name }.take(5)
			}.orEmpty()
			// the albums a shelf shows need their songs back before a play action can use them
			val forgotten = home?.let { repository ->
				repository.withSongs(repository.forgottenFavourites(library, null))
			}
			val neverPlayed = home?.let { repository ->
				repository.withSongs(repository.neverPlayed(library))
			}
			state.update {
				it.copy(
					genres = genres,
					genreCount = all.size,
					forgotten = forgotten.orEmpty(),
					neverPlayed = neverPlayed.orEmpty(),
					deepCuts = home?.deepCuts(library).orEmpty()
				)
			}
		} catch (e: CancellationException) {
			throw e
		} catch (e: Exception) {
			Logger.e("ExploreViewModel", "Failed to load the library shelves", e)
			state.update { it.copy(error = e) }
		} finally {
			state.update { it.copy(loadingLibrary = false) }
		}
		if (audioMuse?.info?.value == null) return@launch
		state.update { it.copy(loadingMoods = true, loadingSoundMap = true) }
		launch {
			try {
				state.update { it.copy(moods = checkNotNull(audioMuseRepository).moods()) }
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				// The Explore shelf is optional when AudioMuse is unavailable.
			} finally {
				state.update { it.copy(loadingMoods = false) }
			}
		}
		launch {
			try {
				state.update { it.copy(soundMap = checkNotNull(audioMuseRepository).soundMap()) }
			} catch (e: CancellationException) {
				throw e
			} catch (_: Exception) {
				// The Explore shelf is optional when AudioMuse is unavailable.
			} finally {
				state.update { it.copy(loadingSoundMap = false) }
			}
		}
	}
}
