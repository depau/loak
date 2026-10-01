package eu.depau.loak.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.QueueSyncManager
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
import eu.depau.loak.util.Logger
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_added_to_queue
import eu.depau.loak.generated.resources.notice_moved_play_next
import eu.depau.loak.generated.resources.notice_moved_to_end
import eu.depau.loak.generated.resources.notice_play_next
import eu.depau.loak.generated.resources.notice_queue_cleared
import eu.depau.loak.generated.resources.notice_removed_from_queue
import kotlin.time.Duration.Companion.seconds

abstract class MediaPlayerViewModel(
	private val stateRepository: PlayerStateRepository,
	protected val songRepository: SongRepository,
	protected val connectivityManager: ConnectivityManager,
	protected val downloadManager: DownloadManager,
	protected val preferenceManager: PreferenceManager,
	protected val queueSyncManager: QueueSyncManager
) : ViewModel() {

	@Suppress("PropertyName")
	protected val _uiState = MutableStateFlow(PlayerUiState())
	val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

	protected fun isExplicit(song: DomainSong): Boolean {
		return song.explicitStatus == DomainExplicitStatus.Explicit
			&& preferenceManager.explicitContentPlayback != ExplicitContentPlayback.Allowed
	}

	/** The [syncKey] the server last saw from here, or that was loaded from it. */
	// declared before init: restoreState() sets it while the constructor runs
	private var syncedKey: Any? = null

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

	/** 0..1, or null where volume belongs to the device (hardware keys) and the app shows none. */
	open val volume: StateFlow<Float>? = null
	open fun setVolume(value: Float) {}

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

	fun removeFromQueueWithUndo(index: Int) {
		val song = uiState.value.queue.getOrNull(index) ?: return
		removeFromQueue(index)
		snackBarManager.notifyWithUndo(Res.string.notice_removed_from_queue) {
			insertIntoQueue(index, listOf(song))
		}
	}

	fun clearQueueWithUndo() {
		val state = uiState.value
		if (state.queue.isEmpty()) return
		clearQueue()
		snackBarManager.notifyWithUndo(Res.string.notice_queue_cleared) {
			if (uiState.value.queue.isNotEmpty()) return@notifyWithUndo
			// same path as restoring the saved queue at startup: song, position and paused state
			val restored = state.copy(isPaused = true)
			_uiState.value = restored
			syncPlayerWithState(restored)
		}
	}

	protected fun PlayerUiState.withRemoved(index: Int): PlayerUiState {
		if (index !in queue.indices) return this
		val newQueue = queue.toMutableList().apply { removeAt(index) }
		val newIndex = when {
			index < currentIndex -> currentIndex - 1
			index == currentIndex -> if (newQueue.isEmpty()) -1 else currentIndex.coerceAtMost(newQueue.size - 1)
			else -> currentIndex
		}
		return copy(queue = newQueue, currentIndex = newIndex, currentSong = newQueue.getOrNull(newIndex))
	}

	protected fun PlayerUiState.withMoved(fromIndex: Int, toIndex: Int): PlayerUiState {
		if (fromIndex !in queue.indices || toIndex !in queue.indices) return this
		val newQueue = queue.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
		val newIndex = when (currentIndex) {
			fromIndex -> toIndex
			in (fromIndex + 1)..toIndex -> currentIndex - 1
			in toIndex until fromIndex -> currentIndex + 1
			else -> currentIndex
		}
		return copy(queue = newQueue, currentIndex = newIndex, currentSong = newQueue.getOrNull(newIndex))
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
		val savedState = queueSyncManager.startupState(stateRepository.state.value)
			?.copy(isPaused = true, isLoading = false)
		// what was loaded is what the server has, or nothing worth pushing yet
		syncedKey = (savedState ?: _uiState.value).syncKey()
		if (savedState != null) {
			_uiState.value = savedState
			syncPlayerWithState(savedState)
			checkAndAutoFillQueue()
		}
	}

	/** What a push to the server tracks: the playback status and the track. */
	private fun PlayerUiState.syncKey() = Triple(currentSong?.id, currentIndex, isPaused)

	/** Saves the queue to the server; true if it got there. */
	protected suspend fun pushQueue(): Boolean {
		val state = uiState.value
		return try {
			queueSyncManager.save(state)
			syncedKey = state.syncKey()
			true
		} catch (e: Exception) {
			Logger.w("MediaPlayerViewModel", "could not save the queue to the server", e)
			false
		}
	}

	@OptIn(FlowPreview::class)
	private fun observeAndSaveState() {
		// the server gets the queue when playback starts, pauses or stops, or the track
		// changes; the debounce lets the player settle after skips and queue swaps
		viewModelScope.launch {
			uiState
				.map { it.syncKey() }
				.distinctUntilChanged()
				.debounce(1.seconds)
				.collect { key ->
					if (preferenceManager.queueSyncEnabled && key != syncedKey) pushQueue()
				}
		}

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
