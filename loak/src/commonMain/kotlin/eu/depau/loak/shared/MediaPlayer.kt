package eu.depau.loak.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.domain.repositories.PlayerStateRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.ui.core.PlayerUiState
import kotlin.time.Duration.Companion.seconds

abstract class MediaPlayerViewModel(
	private val stateRepository: PlayerStateRepository,
	protected val songRepository: SongRepository,
	protected val connectivityManager: ConnectivityManager,
	protected val downloadManager: DownloadManager,
	protected val preferenceManager: PreferenceManager
) : ViewModel() {

	@Suppress("PropertyName")
	protected val _uiState = MutableStateFlow(PlayerUiState())
	val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

	protected fun isExplicit(song: DomainSong): Boolean {
		return song.explicitStatus == DomainExplicitStatus.Explicit
			&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	}

	init {
		viewModelScope.launch {
			restoreState()
			observeAndSaveState()
		}
	}

	abstract fun addToQueueSingle(song: DomainSong, notify: Boolean = true)
	abstract fun addToQueue(collection: DomainSongCollection, notify: Boolean = true)
	abstract fun addToQueue(songs: List<DomainSong>, notify: Boolean = true)
	abstract fun removeFromQueue(index: Int)
	abstract fun moveQueueItem(fromIndex: Int, toIndex: Int)
	abstract fun clearQueue()
	abstract fun playAt(index: Int)
	abstract fun playNextSingle(song: DomainSong)
	abstract fun playNext(collection: DomainSongCollection)
	abstract fun playRadio(radio: DomainRadio)
	abstract fun pause()
	abstract fun resume()
	abstract fun seek(normalized: Float)
	abstract fun next()
	abstract fun previous()
	abstract fun toggleShuffle()
	abstract fun toggleRepeat()
	abstract fun shufflePlay(collection: DomainSongCollection)
	abstract fun setPlaybackSpeed(value: Float)

	fun playNow(song: DomainSong) {
		clearQueue()
		addToQueueSingle(song, notify = false)
		playAt(0)
		checkAndAutoFillQueue()
	}

	fun playNow(collection: DomainSongCollection, startIndex: Int = 0) {
		clearQueue()
		addToQueue(collection, notify = false)
		playAt(startIndex)
		checkAndAutoFillQueue()
	}

	fun playNow(songs: List<DomainSong>, startIndex: Int = 0) {
		clearQueue()
		addToQueue(songs, notify = false)
		playAt(startIndex)
		checkAndAutoFillQueue()
	}

	fun togglePlay() {
		if (!uiState.value.isPaused) {
			pause()
		} else {
			resume()
		}
	}

	abstract fun syncPlayerWithState(state: PlayerUiState)

	protected fun checkAndAutoFillQueue() {
		if (!preferenceManager.autoFillQueue) return

		val state = uiState.value
		if (state.queue.isEmpty()) return

		val remainingCount = state.queue.size - state.currentIndex

		if (remainingCount <= 1) {
			viewModelScope.launch {
				val randomSongs = songRepository.getRandomSongs(1)
				addToQueue(randomSongs, notify = false)
			}
		}
	}

	private suspend fun restoreState() {
		val savedState = stateRepository.state
			.filterNotNull()
			.firstOrNull()
			?.copy(isPaused = true, isLoading = false)
		if (savedState != null) {
			_uiState.value = savedState
			syncPlayerWithState(savedState)
			checkAndAutoFillQueue()
		}
	}

	@OptIn(FlowPreview::class)
	private fun observeAndSaveState() {
		viewModelScope.launch {
			uiState
				.distinctUntilChanged { old, new ->
					old.currentIndex == new.currentIndex &&
						old.queue == new.queue &&
						old.isPaused == new.isPaused &&
						old.repeatMode == new.repeatMode &&
						old.isShuffleEnabled == new.isShuffleEnabled
				}
				.collect { state ->
					stateRepository.setState(state)
				}
		}

		viewModelScope.launch {
			uiState
				.debounce(2.seconds)
				.collect { state ->
					stateRepository.setState(state)
				}
		}
	}
}
