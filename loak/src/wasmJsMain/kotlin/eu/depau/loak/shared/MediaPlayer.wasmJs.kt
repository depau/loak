package eu.depau.loak.shared

import androidx.lifecycle.viewModelScope
import kotlinx.browser.document
import kotlinx.coroutines.flow.update
import org.w3c.dom.HTMLAudioElement
import org.w3c.dom.events.Event
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.ScrobbleManager
import eu.depau.loak.domain.manager.ScrobblePlayerSource
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.repositories.PlayerStateRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger
import eu.depau.loak.web.BrowserMediaSession

/**
 * Web (Kotlin/Wasm) implementation of [MediaPlayerViewModel], backed by a
 * single `HTMLAudioElement`. Ported from the iOS AVPlayer implementation.
 */
class WebMediaPlayerViewModel(
	stateRepository: PlayerStateRepository,
	songRepository: SongRepository,
	connectivityManager: ConnectivityManager,
	downloadManager: DownloadManager,
	preferenceManager: PreferenceManager,
	syncManager: SyncManager,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager
) : MediaPlayerViewModel(
	stateRepository = stateRepository,
	songRepository = songRepository,
	connectivityManager = connectivityManager,
	downloadManager = downloadManager,
	preferenceManager = preferenceManager
) {
	private val audio: HTMLAudioElement =
		document.createElement("audio") as HTMLAudioElement

	private var isTransitioningBetweenTracks = false

	private val scrobbleManager = ScrobbleManager(
		playerSource = object : ScrobblePlayerSource {
			override val currentPosition: Long
				get() = (audio.currentTime * 1000).toLong()
			override val duration: Long
				get() = (audio.duration * 1000).toLong()
			override val isPlaying: Boolean
				get() = !audio.paused
		},
		connectivityManager = connectivityManager,
		syncManager = syncManager,
		sessionManager = sessionManager,
		scope = viewModelScope,
		preferenceManager = preferenceManager
	)

	init {
		setupAudioListeners()
		startProgressObserver()
	}

	private fun onEvent(listener: (Event) -> Unit): (Event) -> Unit = listener

	private fun setupAudioListeners() {
		audio.pause()
		audio.addEventListener("ended", onEvent {
			when (_uiState.value.repeatMode) {
				1 -> {
					seek(0f)
					resume()
				}

				else -> next()
			}
		})
		audio.addEventListener("play", onEvent {
			_uiState.update { it.copy(isPaused = false) }
		})
		audio.addEventListener("pause", onEvent {
			_uiState.update { it.copy(isPaused = true) }
		})
		audio.addEventListener("error", onEvent {
			Logger.e("WebMediaPlayer", "Audio element error: ${audio.currentSrc}")
			_uiState.update { it.copy(isLoading = false) }
		})
	}

	private fun startProgressObserver() {
		audio.addEventListener("timeupdate", onEvent {
			val duration = audio.duration
			val current = audio.currentTime
			if (duration.isFinite() && duration > 0) {
				_uiState.update {
					it.copy(progress = (current / duration).toFloat().coerceIn(0f, 1f))
				}
			}
		})
	}

	private fun getStreamUrl(id: String): String {
		val isCellular = connectivityManager.isCellular.value
		val isTranscoding = preferenceManager.isAdvancedTranscodingActive
		val bitrate = if (isTranscoding) {
			if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.bitrateIos else preferenceManager.streamingQualityWifi.bitrateIos
		}
		val container = if (isTranscoding) {
			if (isCellular) preferenceManager.customFormatCellular else preferenceManager.customFormatWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.containerIos else preferenceManager.streamingQualityWifi.containerIos
		}
		return sessionManager.api.getStreamUrl(
			id = id,
			maxBitRate = bitrate,
			format = container?.takeIf { it.isNotBlank() }
		) + "&estimateContentLength=true"
	}

	private fun getSongUrl(song: DomainSong): String? {
		return when {
			song.id.startsWith("radio_") && !song.filePath.isNullOrEmpty() ->
				song.filePath

			else -> getStreamUrl(song.id)
		}
	}

	private fun updateNowPlayingInfo(song: DomainSong) {
		if (BrowserMediaSession.isSupported()) {
			val artUrl = song.coverArtId?.let { sessionManager.getCoverArtUrl(it) }
			BrowserMediaSession.setMetadata(
				title = song.title.orEmpty(),
				artist = song.artistName.orEmpty(),
				album = song.albumTitle.orEmpty(),
				artworkUrl = artUrl
			)
		}
	}

	override fun playAt(index: Int) {
		if (isTransitioningBetweenTracks) return

		val songToPlay = _uiState.value.queue.getOrNull(index) ?: return
		val url = getSongUrl(songToPlay) ?: return

		isTransitioningBetweenTracks = true
		try {
			audio.pause()
			stopAudio()
			audio.src = url
			audio.load()
			audio.play()

			_uiState.update {
				it.copy(
					currentIndex = index,
					currentSong = songToPlay,
					isPaused = false,
					isLoading = false
				)
			}

			scrobbleManager.onMediaChanged(songToPlay.id)
			scrobbleManager.onPlayStateChanged(true)
			updateNowPlayingInfo(songToPlay)
		} finally {
			isTransitioningBetweenTracks = false
		}
	}

	override fun playRadio(radio: DomainRadio) {
		val radioId = "radio_${radio.name.hashCode()}"

		val dummyRadioSong = DomainSong(
			id = radioId,
			title = radio.name,
			artistName = "Live Radio",
			albumId = "radio_album",
			albumTitle = "Live Stream",
			duration = kotlin.time.Duration.ZERO,
			trackNumber = 1,
			coverArtId = null,
			artistId = "",
			parentId = "",
			comment = null,
			discNumber = null,
			isrc = emptyList(),
			year = null,
			genre = null,
			genres = emptyList(),
			moods = emptyList(),
			bpm = null,
			contributors = emptyList(),
			playCount = 0,
			userRating = 0,
			averageRating = null,
			bitRate = null,
			bitDepth = null,
			sampleRate = null,
			audioChannelCount = null,
			replayGain = null,
			fileSize = 0,
			fileExtension = "",
			mimeType = "",
			filePath = radio.streamUrl,
			starredAt = null,
			musicBrainzId = null,
			explicitStatus = DomainExplicitStatus.Unknown,
			artists = emptyList(),
			albumArtists = emptyList(),
			isExternal = false
		)

		if (!radio.streamUrl.isNullOrBlank()) {
			audio.pause()
			stopAudio()
			audio.src = radio.streamUrl
			audio.load()
			audio.play()
		}

		_uiState.update { state ->
			state.copy(
				queue = listOf(dummyRadioSong),
				currentIndex = 0,
				currentSong = dummyRadioSong,
				isLoading = false
			)
		}

		scrobbleManager.onMediaChanged(radioId)
		scrobbleManager.onPlayStateChanged(true)
		updateNowPlayingInfo(dummyRadioSong)
	}

	override fun playNextSingle(song: DomainSong) {
		_uiState.update { state ->
			val newQueue =
				if (state.queue.isEmpty())
					state.queue + song
				else
					state.queue.slice(0..state.currentIndex) + song + state.queue.slice(state.currentIndex + 1..<state.queue.size)
			state.copy(
				queue = newQueue,
				currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
				currentSong = if (state.currentIndex == -1) song else state.currentSong
			)
		}
		snackBarManager.notifyPlayNext()
	}

	override fun playNext(collection: DomainSongCollection) {
		val newCollection = if (collection is DomainAlbum) collection.songs.sortedWith(
			compareBy(
				{ it.discNumber },
				{ it.trackNumber }
			)
		) else collection.songs
		_uiState.update { state ->
			val newQueue =
				if (state.queue.isEmpty())
					state.queue + newCollection
				else
					state.queue.slice(0..state.currentIndex) + newCollection + state.queue.slice(
						state.currentIndex + 1..<state.queue.size
					)
			state.copy(
				queue = newQueue,
				currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
				currentSong = if (state.currentIndex == -1) newCollection.firstOrNull() else state.currentSong
			)
		}
		snackBarManager.notifyPlayNext()
	}

	override fun addToQueueSingle(song: DomainSong, notify: Boolean) {
		_uiState.update { state ->
			val newQueue = state.queue + song
			state.copy(
				queue = newQueue,
				currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
				currentSong = if (state.currentIndex == -1) song else state.currentSong
			)
		}
		if (notify) snackBarManager.notifyAddedToQueue()
	}

	override fun addToQueue(collection: DomainSongCollection, notify: Boolean) {
		addToQueue(
			if (collection is DomainAlbum) collection.songs.sortedWith(
				compareBy(
					{ it.discNumber },
					{ it.trackNumber }
				)
			) else collection.songs,
			notify
		)
	}

	override fun addToQueue(songs: List<DomainSong>, notify: Boolean) {
		_uiState.update { state ->
			val newQueue = state.queue + songs
			state.copy(
				queue = newQueue,
				currentIndex = if (state.currentIndex == -1) 0 else state.currentIndex,
				currentSong = if (state.currentIndex == -1) songs.firstOrNull() else state.currentSong
			)
		}
		if (notify) snackBarManager.notifyAddedToQueue()
	}

	override fun removeFromQueue(index: Int) {
		_uiState.update { state ->
			val newQueue = state.queue.toMutableList().apply {
				if (index in indices) removeAt(index)
			}
			val newIndex = when {
				index < state.currentIndex -> state.currentIndex - 1
				index == state.currentIndex -> if (newQueue.isEmpty()) -1 else state.currentIndex.coerceAtMost(
					newQueue.size - 1
				)

				else -> state.currentIndex
			}
			state.copy(
				queue = newQueue,
				currentIndex = newIndex,
				currentSong = if (newIndex == -1) null else newQueue[newIndex]
			)
		}
	}

	override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
		_uiState.update { state ->
			val newQueue = state.queue.toMutableList().apply {
				if (fromIndex in indices && toIndex in 0..size) {
					val item = removeAt(fromIndex)
					add(toIndex, item)
				}
			}
			val newIndex = when (state.currentIndex) {
				fromIndex -> toIndex
				in (fromIndex + 1)..toIndex -> state.currentIndex - 1
				in toIndex until fromIndex -> state.currentIndex + 1
				else -> state.currentIndex
			}
			state.copy(
				queue = newQueue,
				currentIndex = newIndex,
				currentSong = if (newIndex == -1) null else newQueue[newIndex]
			)
		}
	}

	override fun clearQueue() {
		_uiState.update { state ->
			state.copy(
				queue = emptyList(),
				currentIndex = -1,
				currentSong = null,
				currentCollection = null
			)
		}
		audio.pause()
		stopAudio()
	}

	override fun resume() {
		audio.play()
		_uiState.update { it.copy(isPaused = false) }
		scrobbleManager.onPlayStateChanged(true)
	}

	override fun pause() {
		audio.pause()
		_uiState.update { it.copy(isPaused = true) }
		scrobbleManager.onPlayStateChanged(false)
	}

	override fun next() {
		val state = _uiState.value
		val nextIndex = state.currentIndex + 1
		if (nextIndex < state.queue.size) {
			playAt(nextIndex)
		} else if (state.repeatMode == 2) {
			playAt(0)
		} else {
			audio.pause()
			_uiState.update { it.copy(isPaused = true, progress = 0f) }
		}
	}

	override fun previous() {
		val state = _uiState.value
		val previousIndex = state.currentIndex - 1
		if (previousIndex >= 0) {
			playAt(previousIndex)
		} else {
			playAt(0)
		}
	}

	override fun toggleShuffle() {
		_uiState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
	}

	override fun toggleRepeat() {
		_uiState.update { it.copy(repeatMode = (it.repeatMode + 1) % 3) }
	}

	override fun shufflePlay(collection: DomainSongCollection) {
		val songs = collection.songs.shuffled()
		_uiState.update { it.copy(queue = songs, currentIndex = -1, currentSong = null) }
		playAt(0)
	}

	override fun setPlaybackSpeed(value: Float) {
		audio.playbackRate = value.toDouble()
		_uiState.update { it.copy(playbackSpeed = value) }
	}

	override fun seek(normalized: Float) {
		val duration = audio.duration
		if (duration.isFinite() && duration > 0) {
			audio.currentTime = duration * normalized.toDouble()
		}
		_uiState.update { it.copy(progress = normalized) }
	}

	override fun syncPlayerWithState(state: PlayerUiState) {
		// Called from the base class with the restored, paused state
		val song = state.currentSong ?: return
		val url = getSongUrl(song) ?: return

		isTransitioningBetweenTracks = true
		try {
			audio.pause()
			stopAudio()
			audio.src = url
			audio.load()
			if (state.progress > 0f) {
				val duration = audio.duration
				if (duration.isFinite() && duration > 0) {
					audio.currentTime = duration * state.progress.toDouble()
				}
			}
			_uiState.value = state
			updateNowPlayingInfo(song)
		} finally {
			isTransitioningBetweenTracks = false
		}
	}

	private fun stopAudio() {
		audio.removeAttribute("src")
		audio.load()
	}

	override fun onCleared() {
		audio.pause()
		stopAudio()
	}
}
