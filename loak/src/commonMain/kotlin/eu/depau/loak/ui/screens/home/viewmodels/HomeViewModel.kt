package eu.depau.loak.ui.screens.home.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.HomeLibrary
import eu.depau.loak.domain.repositories.HomeRepository
import eu.depau.loak.domain.repositories.ServerListener
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.domain.repositories.SpeedDialItem
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_server_unreachable
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
	val loading: Boolean = true,
	/** The first load is done; until then nothing shows, so shelves don't pop in above the fold. */
	val ready: Boolean = false,
	val genres: List<String> = emptyList(),
	val speedDial: List<SpeedDialItem> = emptyList(),
	/** Loading until built: they may wait on the server. */
	val quickPicks: UiState<List<DomainSong>> = UiState.Loading(),
	val mixArtists: List<DomainArtist> = emptyList(),
	val madeForYou: List<DomainPlaylist> = emptyList(),
	val radios: UiState<List<DomainPlaylist>> = UiState.Loading(),
	val sonicJourney: Pair<DomainSong, DomainSong>? = null,
	val similarTo: UiState<Pair<DomainArtist, List<DomainArtist>>?> = UiState.Loading(),
	val recentlyAdded: List<DomainAlbum> = emptyList(),
	val forgotten: List<DomainAlbum> = emptyList(),
	val nowPlaying: List<ServerListener> = emptyList()
)

/**
 * Home's feed. [fixedGenre] makes it a genre's page; otherwise genre chips filter it.
 */
class HomeViewModel(
	val fixedGenre: String?,
	private val repository: HomeRepository,
	private val songRepository: SongRepository,
	private val player: MediaPlayerViewModel,
	private val snackBarManager: SnackBarManager,
	sessionManager: SessionManager,
	syncManager: SyncManager
) : ViewModel() {
	/** During the first sync Home shows its progress, not shelves from a partial library. */
	val sync = syncManager.syncState

	val state: StateFlow<HomeUiState>
		field = MutableStateFlow(HomeUiState())

	/** The genre chip that's on, on Home. */
	val selectedGenre: StateFlow<String?>
		field = MutableStateFlow(null)

	private val genre get() = fixedGenre ?: selectedGenre.value

	private var library: HomeLibrary? = null
	private var loadJob: Job? = null

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect { if (it) refresh(reloadLibrary = true) }
		}
		// the library sync writes the cache in the background: reload once it's done
		viewModelScope.launch {
			var initial = sync.value.initial
			sync.distinctUntilChangedBy { it.isSyncing }.drop(1).collect {
				if (it.isSyncing) initial = it.initial
				// Quick picks built from the partial library would otherwise stick around
				else refresh(reloadLibrary = true, rebuildPicks = initial)
			}
		}
		viewModelScope.launch {
			repository.pins.drop(1).collect {
				val library = library ?: return@collect
				if (genre == null) state.update { it.copy(speedDial = repository.speedDial(library)) }
			}
		}
	}

	/** Pull to refresh: reloads the library and builds new Quick picks. */
	fun refresh() = refresh(reloadLibrary = true, rebuildPicks = true)

	/** Home came back on screen: Quick picks get rebuilt if they're old. */
	fun onShown() {
		if (library != null && loadJob?.isActive != true) refresh(reloadLibrary = false)
	}

	fun selectGenre(genre: String?) {
		if (fixedGenre != null) return
		selectedGenre.value = genre
		refresh(reloadLibrary = false)
	}

	private fun refresh(reloadLibrary: Boolean, rebuildPicks: Boolean = false) {
		loadJob?.cancel()
		loadJob = viewModelScope.launch {
			state.update { it.copy(loading = true) }
			val library = (if (reloadLibrary) null else library) ?: repository.library()
			this@HomeViewModel.library = library
			val genre = genre
			val topArtists = repository.topArtists(library, genre)

			state.update {
				it.copy(
					genres = if (fixedGenre == null) repository.topGenres(library) else emptyList(),
					speedDial = if (genre == null) repository.speedDial(library) else emptyList(),
					mixArtists = topArtists,
					madeForYou = if (genre == null) repository.madeForYou() else emptyList(),
					recentlyAdded = repository.recentlyAdded(library, genre),
					forgotten = repository.forgottenFavourites(library, genre),
					// Quick picks may wait on the server: they come in below the top
					ready = true
				)
			}
			state.update {
				it.copy(quickPicks = UiState.Success(repository.quickPicks(library, genre, rebuildPicks)), loading = false)
			}

			// the server's answers come last, each on its own; a failure shows nothing, not a skeleton
			launchRemote(onError = { state.update { it.copy(radios = UiState.Success(emptyList())) } }) {
				val radios = if (genre == null) repository.radios() else emptyList()
				state.update { it.copy(radios = UiState.Success(radios)) }
			}
			launchRemote(onError = { state.update { it.copy(similarTo = UiState.Success(null)) } }) {
				val similarTo = if (genre == null) repository.similarTo(topArtists) else null
				state.update { it.copy(similarTo = UiState.Success(similarTo)) }
			}
			launchRemote {
				state.update { it.copy(nowPlaying = if (genre == null) repository.nowPlaying(library) else emptyList()) }
			}
			launchRemote {
				val journey = if (genre == null) repository.sonicJourney(library, player.uiState.value.currentSong) else null
				state.update { it.copy(sonicJourney = journey) }
			}
		}
	}

	// children of the refresh, so a newer refresh cancels them
	private fun CoroutineScope.launchRemote(onError: () -> Unit = {}, block: suspend () -> Unit) = launch {
		try {
			block()
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.w(TAG, "a Home shelf could not load", e)
			onError()
		}
	}

	/** Plays Quick picks exactly as shown, from [index]. */
	fun playQuickPicks(index: Int = 0) = player.playNow(state.value.quickPicks.data.orEmpty(), index)

	/** The dice: a random song you've played, and its radio. */
	fun feelingLucky() {
		val songs = library?.songs ?: return
		val song = songs.filter { it.playCount > 0 }.ifEmpty { songs }.randomOrNull() ?: return
		playRadio(song)
	}

	fun playRadio(song: DomainSong) = player.playInstantMix(song.id, song.title, song)

	fun playMix(artist: DomainArtist) = player.playInstantMix(artist.id, artist.name)

	fun playSonicJourney() {
		val (from, to) = state.value.sonicJourney ?: return
		viewModelScope.launch {
			val path = try {
				songRepository.getSonicPath(from.id, to.id)
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.w(TAG, "could not find a sonic path", e)
				snackBarManager.notify(Res.string.notice_server_unreachable)
				return@launch
			}
			// the server may or may not include the ends
			player.playNow(listOf(from) + path.filter { it.id != from.id && it.id != to.id } + to)
		}
	}

	// region song options, for Quick picks and Playing on your server

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val selectedSongStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			selectedSong.value = song
			selectedSongStarred.value = songRepository.isSongStarred(song)
			selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun clearSongSelection() {
		selectedSong.value = null
	}

	fun starSelectedSong(starred: Boolean) {
		val song = selectedSong.value ?: return
		viewModelScope.launch {
			songRepository.setSongStarred(song.id, starred)
			selectedSongStarred.value = starred
		}
	}

	fun rateSelectedSong(rating: Int) {
		val song = selectedSong.value ?: return
		viewModelScope.launch {
			songRepository.rateSong(song, rating)
			selectedSongRating.value = rating
		}
	}

	// endregion

	companion object {
		private const val TAG = "HomeViewModel"
	}
}
