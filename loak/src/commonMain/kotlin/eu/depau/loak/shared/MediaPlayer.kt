package eu.depau.loak.shared

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import eu.depau.loak.domain.manager.PlayLogManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.toState
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.domain.repositories.PlayerStateRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.ui.core.InstantMix
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_added_to_queue
import eu.depau.loak.generated.resources.notice_instant_mix
import eu.depau.loak.generated.resources.notice_moved_play_next
import eu.depau.loak.generated.resources.notice_moved_to_end
import eu.depau.loak.generated.resources.notice_no_server_queue
import eu.depau.loak.generated.resources.notice_no_similar_songs
import eu.depau.loak.generated.resources.notice_play_next
import eu.depau.loak.generated.resources.notice_queue_cleared
import eu.depau.loak.generated.resources.notice_sonic_path
import eu.depau.loak.generated.resources.notice_queue_loaded
import eu.depau.loak.generated.resources.notice_queue_sent
import eu.depau.loak.generated.resources.notice_removed_from_queue
import eu.depau.loak.generated.resources.notice_server_unreachable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

abstract class MediaPlayerViewModel(
	private val stateRepository: PlayerStateRepository,
	protected val songRepository: SongRepository,
	protected val connectivityManager: ConnectivityManager,
	protected val downloadManager: DownloadManager,
	protected val preferenceManager: PreferenceManager,
	protected val queueSyncManager: QueueSyncManager
) : ViewModel(), KoinComponent {
	/** Records the playlists and albums started, for Home. */
	protected val playLog: PlayLogManager by inject()


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
			observeAutoFill()
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

	fun playNow(song: DomainSong) = playNow(listOf(song))

	fun playNow(collection: DomainSongCollection, startIndex: Int = 0) {
		playLog.recordCollection(collection)
		playNow(collection.orderedSongs(), startIndex)
	}

	fun playNow(songs: List<DomainSong>, startIndex: Int = 0) = startQueue(songs, startIndex, mix = null)

	private fun startQueue(songs: List<DomainSong>, startIndex: Int, mix: InstantMix?) {
		// before clearQueue: platforms clear by copying the state, which keeps the mix
		_uiState.update { it.copy(instantMix = mix) }
		clearQueue()
		addToQueue(songs, notify = false)
		playAt(startIndex)
	}

	/** The queue's instant mix, while the queue holds only its songs (it may skip explicit ones). */
	val instantMix: StateFlow<InstantMix?> = uiState.map { state ->
		state.instantMix?.takeIf { mix ->
			state.queue.isNotEmpty() && state.queue.all { it.id in mix.songIds }
		}
	}.stateIn(viewModelScope, SharingStarted.Eagerly, null)

	/**
	 * Replaces the queue with songs the server finds similar to [seedId] (a song, album or
	 * artist id), with an undo. A [seed] song plays first.
	 */
	fun playInstantMix(seedId: String, seedName: String, seed: DomainSong? = null) =
		startInstantMix(seedName, seed) { songRepository.getSimilarSongs(seedId) }

	/**
	 * An instant mix seeded by a few of [songs], for lists the server can't take as a seed
	 * (playlists, Quick picks).
	 */
	fun playInstantMix(songs: List<DomainSong>, name: String) = startInstantMix(name, null) {
		// ponytail: 3 random seeds, one call each; more seeds would cost more calls
		val lists = songs.shuffled().take(3).map { songRepository.getSimilarSongs(it.id, count = 20) }
		(0 until (lists.maxOfOrNull { it.size } ?: 0))
			.flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
			.distinctBy { it.id }
	}

	/** Plays [songs] as they are, as a mix called [name] (AudioMuse-AI results), with an undo. */
	fun playMix(songs: List<DomainSong>, name: String) = startInstantMix(name, null) { songs }

	private fun startInstantMix(
		seedName: String,
		seed: DomainSong?,
		fetchSimilar: suspend () -> List<DomainSong>
	) {
		viewModelScope.launch {
			val similar = try {
				fetchSimilar()
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.w("MediaPlayerViewModel", "could not fetch similar songs", e)
				snackBarManager.notify(Res.string.notice_server_unreachable)
				return@launch
			}
			if (similar.isEmpty()) {
				snackBarManager.notify(Res.string.notice_no_similar_songs)
				return@launch
			}
			val songs = listOfNotNull(seed) + similar.filter { it.id != seed?.id }
			val previous = uiState.value
			startQueue(songs, 0, InstantMix(seedName, songs.mapTo(HashSet()) { it.id }))
			snackBarManager.notifyWithUndo(Res.string.notice_instant_mix, seedName) {
				replaceQueue(previous)
			}
		}
	}

	// ponytail: probed once per run; a server switch in Settings keeps the old answer
	private var canMix: Boolean? = null

	/**
	 * Whether the server finds similar songs at all. The API can't tell, so this asks about a
	 * few random songs: servers without similarity answer with nothing or an error.
	 */
	suspend fun canMix(): Boolean {
		canMix?.let { return it }
		if (!connectivityManager.isOnline.value) return false
		// an empty library is still syncing: ask again later
		val seeds = songRepository.getRandomSongs(3).ifEmpty { return false }
		return try {
			seeds.any { songRepository.getSimilarSongs(it.id, count = 1).isNotEmpty() }
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.w("MediaPlayerViewModel", "could not probe for similar songs", e)
			false
		}.also { canMix = it }
	}

	fun togglePlay() {
		if (!uiState.value.isPaused) {
			pause()
		} else {
			resume()
		}
	}

	abstract fun syncPlayerWithState(state: PlayerUiState)

	private var autoFillJob: Job? = null

	/** Whether the server can find paths between songs, for [playSonicPathTo]. */
	suspend fun canFindSonicPaths() = "sonicSimilarity" in queueSyncManager.extensions()

	/**
	 * Replaces what comes after the current song with songs that ease from it into [target],
	 * ending on [target], with an undo.
	 */
	fun playSonicPathTo(target: DomainSong) {
		val current = uiState.value.currentSong ?: return
		viewModelScope.launch {
			val path = try {
				songRepository.getSonicPath(current.id, target.id)
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.w("MediaPlayerViewModel", "could not find a sonic path", e)
				snackBarManager.notify(Res.string.notice_server_unreachable)
				return@launch
			}
			// the server may or may not include the ends
			val songs = path.filter { it.id != current.id && it.id != target.id } + target
			val previous = uiState.value
			val start = previous.currentIndex + 1
			for (index in previous.queue.lastIndex downTo start) removeFromQueue(index)
			insertIntoQueue(start, songs)
			snackBarManager.notifyWithUndo(Res.string.notice_sonic_path, target.title) {
				replaceQueue(previous)
			}
		}
	}

	/** Checks for auto-fill whenever the songs left or the last song change. */
	@OptIn(FlowPreview::class)
	private fun observeAutoFill() {
		// driven by the state, not by callers: platforms update the queue asynchronously, so a
		// check right after a change still sees the old queue; the debounce lets a queue swap
		// settle, since the player briefly reports the old queue's index against the new songs
		viewModelScope.launch {
			uiState
				.map { (it.queue.size - it.currentIndex) to it.queue.lastOrNull()?.id }
				.distinctUntilChanged()
				.debounce(500.milliseconds)
				.collect { checkAndAutoFillQueue() }
		}
	}

	/** When one song is left, queues songs similar to it, or a random one if there are none. */
	private fun checkAndAutoFillQueue() {
		// the state can change while the server answers
		if (!preferenceManager.autoFillQueue || autoFillJob?.isActive == true) return

		val state = uiState.value
		val last = state.queue.lastOrNull() ?: return
		if (state.currentIndex !in state.queue.indices || state.queue.size - state.currentIndex > 1) return

		autoFillJob = viewModelScope.launch {
			val similar = if (!connectivityManager.isOnline.value) emptyList() else try {
				songRepository.getSimilarSongs(last.id, count = 10)
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.w("MediaPlayerViewModel", "could not fetch similar songs to auto-fill", e)
				emptyList()
			}
			val queued = state.queue.mapTo(HashSet()) { it.id }
			val songs = similar.filter { it.id !in queued }
				.ifEmpty { songRepository.getRandomSongs(1) }
			addToQueue(songs, notify = false)
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
		}
	}

	/** Sends this queue to the server, for the user's other devices. */
	fun sendQueueToServer() {
		viewModelScope.launch {
			snackBarManager.notify(
				if (pushQueue()) Res.string.notice_queue_sent else Res.string.notice_server_unreachable
			)
		}
	}

	/** Replaces this queue with the one on the server, with an undo. */
	fun loadQueueFromServer() {
		viewModelScope.launch {
			val remote = try {
				queueSyncManager.fetch()
			} catch (e: Exception) {
				Logger.w("MediaPlayerViewModel", "could not fetch the server queue", e)
				snackBarManager.notify(Res.string.notice_server_unreachable)
				return@launch
			}
			if (remote == null) {
				snackBarManager.notify(Res.string.notice_no_server_queue)
				return@launch
			}
			val previous = uiState.value
			replaceQueue(remote.toState(previous))
			queueSyncManager.markPickedUp(remote)
			snackBarManager.notifyWithUndo(
				Res.string.notice_queue_loaded,
				QueueSyncManager.sourceName(remote.changedBy)
			) { replaceQueue(previous) }
		}
	}

	/** Swaps in [state], paused, the same way the saved queue is restored at startup. */
	private fun replaceQueue(state: PlayerUiState) {
		val paused = state.copy(isPaused = true, isLoading = false)
		clearQueue()
		// a queue that came from the server, or was there before, isn't pushed back
		syncedKey = paused.syncKey()
		_uiState.value = paused
		syncPlayerWithState(paused)
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

		// and every so often while playing, so other devices resume near the same spot:
		// the server only knows the position a client last saved
		viewModelScope.launch {
			uiState
				.map { !it.isPaused && it.currentSong != null }
				.distinctUntilChanged()
				.collectLatest { playing ->
					while (playing) {
						delay(POSITION_SAVE_INTERVAL)
						if (preferenceManager.queueSyncEnabled) pushQueue()
					}
				}
		}

		viewModelScope.launch {
			uiState
				.distinctUntilChanged { old, new ->
					// every queue mutation produces a new list (immutable songs),
					// so identity is enough: deep-comparing queues on each 200 ms
					// progress tick (which shares the list reference) is pure
					// waste on long playlists
					old.currentIndex == new.currentIndex &&
						old.queue === new.queue &&
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

	private companion object {
		// ponytail: fixed; a setting if anyone needs it tighter
		val POSITION_SAVE_INTERVAL = 30.seconds
	}
}

