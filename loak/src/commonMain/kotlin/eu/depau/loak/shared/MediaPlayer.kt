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
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.domain.repositories.PlayerStateRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_added_to_queue
import eu.depau.loak.generated.resources.notice_moved_play_next
import eu.depau.loak.generated.resources.notice_moved_to_end
import eu.depau.loak.generated.resources.notice_play_next
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

	protected abstract val snackBarManager: SnackBarManager

	/** Inserts [songs] at [index] of the queue, keeping the current song playing. */
	protected abstract fun insertIntoQueue(index: Int, songs: List<DomainSong>)
	abstract fun removeFromQueue(index: Int)
	abstract fun moveQueueItem(fromIndex: Int, toIndex: Int)
	abstract fun clearQueue()
	abstract fun playAt(index: Int)
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

	fun addToQueueSingle(song: DomainSong, notify: Boolean = true) =
		addToQueue(listOf(song), notify)

	fun addToQueue(collection: DomainSongCollection, notify: Boolean = true) =
		addToQueue(collection.orderedSongs(), notify)

	fun addToQueue(songs: List<DomainSong>, notify: Boolean = true) =
		enqueue(songs, next = false, notify = notify)

	fun playNextSingle(song: DomainSong) = enqueue(listOf(song), next = true, notify = true)

	fun playNext(collection: DomainSongCollection) =
		enqueue(collection.orderedSongs(), next = true, notify = true)

	private fun DomainSongCollection.orderedSongs() =
		if (this is DomainAlbum) songs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
		else songs

	/**
	 * Queues [songs] right after the current song ([next]) or at the end of the queue.
	 * User actions ([notify]) move songs that are already queued instead of duplicating
	 * them, and offer an undo.
	 */
	private fun enqueue(songs: List<DomainSong>, next: Boolean, notify: Boolean) {
		if (songs.isEmpty()) return
		val state = uiState.value
		if (!notify) {
			insertIntoQueue(state.queue.size, songs)
			return
		}

		val ids = songs.mapTo(HashSet()) { it.id }
		// the current song stays put: queuing it again adds a copy
		val moved = state.queue.withIndex()
			.filter { (index, song) -> index != state.currentIndex && song.id in ids }
		moved.asReversed().forEach { removeFromQueue(it.index) }

		val current = state.currentIndex - moved.count { it.index < state.currentIndex }
		val at = if (next) current + 1 else state.queue.size - moved.size
		insertIntoQueue(at, songs)

		val message = when {
			moved.size < songs.size -> if (next) Res.string.notice_play_next else Res.string.notice_added_to_queue
			next -> Res.string.notice_moved_play_next
			else -> Res.string.notice_moved_to_end
		}
		snackBarManager.notifyWithUndo(message) { undoEnqueue(at, songs, moved) }
	}

	private fun undoEnqueue(at: Int, songs: List<DomainSong>, moved: List<IndexedValue<DomainSong>>) {
		val queue = uiState.value.queue
		// ponytail: if the queue changed since, the undo is dropped rather than guessed
		if (at + songs.size > queue.size) return
		if (queue.subList(at, at + songs.size).map { it.id } != songs.map { it.id }) return

		for (i in songs.indices.reversed()) removeFromQueue(at + i)
		moved.forEach { (index, song) -> insertIntoQueue(index, listOf(song)) }
	}

	protected fun PlayerUiState.withInserted(index: Int, songs: List<DomainSong>): PlayerUiState {
		val at = index.coerceIn(0, queue.size)
		return copy(
			queue = queue.take(at) + songs + queue.drop(at),
			currentIndex = when {
				currentIndex == -1 -> 0
				at <= currentIndex -> currentIndex + songs.size
				else -> currentIndex
			},
			currentSong = if (currentIndex == -1) songs.firstOrNull() else currentSong
		)
	}

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
