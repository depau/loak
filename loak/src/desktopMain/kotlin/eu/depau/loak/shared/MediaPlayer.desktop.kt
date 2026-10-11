package eu.depau.loak.shared

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.AudioFetcher
import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.MediaControlManager
import dev.nucleusframework.media.control.MediaControlEvent
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.ScrobbleManager
import eu.depau.loak.domain.manager.ScrobblePlayerSource
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.manager.audioExtension
import eu.depau.loak.domain.models.AudioQuality
import eu.depau.loak.domain.models.PlaybackSource
import eu.depau.loak.domain.models.formatCodecName
import eu.depau.loak.domain.models.formatSampleRate
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.repositories.PlayerStateRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_server_unreachable
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger
import dev.nucleusframework.rodio.PlaybackCallback
import dev.nucleusframework.rodio.PlaybackEvent
import dev.nucleusframework.rodio.RodioPlayer
import org.koin.core.component.inject

/**
 * Desktop (JVM) implementation of [MediaPlayerViewModel], using Nucleus' rodio backend
 * (Rust rodio → Symphonia decoders: FLAC, AAC, Opus, Vorbis, MP3, WAV).
 *
 * rodio's HTTP path (playUrl) can't seek, and a FLAC file that isn't complete won't decode from
 * mid-stream, so lossless playback goes through the [AudioStore]: the Raw file is fetched to disk
 * (AudioFetcher), then played via playFile — the only fully-seekable path. To keep instant start
 * the lossy (MP3) stream begins immediately and hot-swaps to the lossless file once it's ready.
 * Everything else (lossy streams, live radio) plays straight over HTTP, native to the decoders.
 */
class DesktopMediaPlayerViewModel(
	stateRepository: PlayerStateRepository,
	songRepository: SongRepository,
	connectivityManager: ConnectivityManager,
	downloadManager: DownloadManager,
	preferenceManager: PreferenceManager,
	queueSyncManager: QueueSyncManager,
	private val syncManager: SyncManager,
	private val sessionManager: SessionManager,
	private val mediaControlManager: MediaControlManager,
	override val snackBarManager: SnackBarManager
) : MediaPlayerViewModel(
	stateRepository = stateRepository,
	songRepository = songRepository,
	connectivityManager = connectivityManager,
	downloadManager = downloadManager,
	preferenceManager = preferenceManager,
	queueSyncManager = queueSyncManager
) {

	private val audioFetcher: AudioFetcher by inject()

	private val rodio = RodioPlayer().apply {
		setCallback(object : PlaybackCallback {
			override fun onEvent(event: PlaybackEvent) {}
			override fun onMetadata(key: String, value: String) {}
			override fun onError(message: String) {
				Logger.e("DesktopMediaPlayerViewModel", "rodio error: $message")
			}
		})
	}

	/** A lossy fallback likely still streaming; cancelled before a new source takes over. */
	private var swappedAwayJob: Job? = null

	private var isTransitioningBetweenTracks = false
	private var progressJob: Job? = null
	private var lastTrackEndTime = 0L
	private var rapidEndCount = 0

	private val scrobbleManager = ScrobbleManager(
		playerSource = object : ScrobblePlayerSource {
			override val currentPosition: Long get() = rodio.getPositionMs()
			override val duration: Long get() = rodio.getDurationMs() ?: 0L
			override val isPlaying: Boolean get() = !rodio.isPaused() && !rodio.isEmpty()
		},
		connectivityManager = connectivityManager,
		syncManager = syncManager,
		sessionManager = sessionManager,
		scope = viewModelScope,
		preferenceManager = preferenceManager
	)

	override val volume = MutableStateFlow(preferenceManager.playerVolume)

	init {
		progressJob = viewModelScope.launch(Dispatchers.IO) {
			while (true) {
				delay(250)
				_uiState.update { state ->
					state.copy(progress = progress())
				}
				if (rodio.isEmpty() && !isTransitioningBetweenTracks && !rodio.isPaused()) {
					onTrackEnded()
				}
			}
		}

		// OS media controls (MPRIS / SMTC / Now Playing): media keys, shell widgets, headsets
		mediaControlManager.start(uiState, volume, ::onMediaControlEvent)
	}

	private fun onMediaControlEvent(event: MediaControlEvent) {
		val state = _uiState.value
		val durationMs = state.currentSong?.duration?.inWholeMilliseconds ?: 0L
		when (event) {
			MediaControlEvent.Play -> if (state.currentSong != null) resume()
			MediaControlEvent.Pause, MediaControlEvent.Stop -> pause()
			MediaControlEvent.Toggle -> if (state.currentSong != null) togglePlay()
			MediaControlEvent.Next -> next()
			MediaControlEvent.Previous -> previous()
			is MediaControlEvent.SeekBy -> if (durationMs > 0) {
				seek(((state.progress * durationMs + event.offsetMs) / durationMs).coerceIn(0f, 1f))
			}
			is MediaControlEvent.SetPosition -> if (durationMs > 0) {
				seek((event.positionMs.toFloat() / durationMs).coerceIn(0f, 1f))
			}
			is MediaControlEvent.SetVolume -> setVolume(event.volume.toFloat())
			// the window and app lifecycle aren't the player's to raise or quit
			MediaControlEvent.Raise, MediaControlEvent.Quit, is MediaControlEvent.OpenUri -> Unit
		}
	}

	override fun onCleared() {
		super.onCleared()
		rodio.close()
		progressJob?.cancel()
		swappedAwayJob?.cancel()
		mediaControlManager.stop()
	}

	// --- stream URL / quality ---

	private fun maxBitrate(): Int {
		val isCellular = connectivityManager.isCellular.value
		return if (preferenceManager.isAdvancedTranscodingActive) {
			(if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi)
				?.takeIf { it > 0 } ?: 320
		} else {
			preferenceManager.streamingQualityCellular.takeIf { isCellular }?.bitrateIos
				?.takeIf { it > 0 } ?: preferenceManager.streamingQualityWifi.bitrateIos.takeIf { it > 0 } ?: 320
		}
	}

	private fun currentContainer(): String {
		val isCellular = connectivityManager.isCellular.value
		return if (preferenceManager.isAdvancedTranscodingActive) {
			(if (isCellular) preferenceManager.customFormatCellular else preferenceManager.customFormatWifi)
				.orEmpty().ifBlank { "mp3" }
		} else {
			"mp3"
		}
	}

	/** The stream URL for [song]'s lossy fallback (mp3 unless the user picked a format). */
	private fun streamSource(song: DomainSong): String =
		sessionManager.api.getStreamUrl(
			id = song.id,
			maxBitRate = maxBitrate(),
			format = currentContainer()
		) + "&estimateContentLength=true"

	/** The original-file URL for lossless playback (Raw): no transcode, no bitrate cap. */
	private fun losslessStreamUrl(song: DomainSong): String =
		sessionManager.api.getStreamUrl(
			id = song.id,
			maxBitRate = 0,
			format = null
		) + "&estimateContentLength=true"

	/** Whether [song] should be played losslessly (Raw): the user chose lossless (bitrate 0, no
	 *  container) or an advanced "raw"/"flac" container. Radio is never lossless. */
	private fun wantsLossless(song: DomainSong): Boolean {
		if (song.id.startsWith("radio_")) return false
		val q = qualityRequested()
		return q.format == null || q.format.equals("flac", true)
	}

	private fun qualityRequested(): AudioQuality {
		val isCellular = connectivityManager.isCellular.value
		return if (preferenceManager.isAdvancedTranscodingActive) {
			AudioQuality.of(currentContainer(), maxBitrate())
		} else {
			val q = if (isCellular) preferenceManager.streamingQualityCellular else preferenceManager.streamingQualityWifi
			AudioQuality.of(q.containerIos, q.bitrateIos)
		}
	}

	override fun requestedQuality(): AudioQuality = qualityRequested()

	private suspend fun storedPath(song: DomainSong): String? =
		audioStore.bestComplete(song.id)?.let { audioStore.pathOf(it) }

	private fun setDecoderInfo(song: DomainSong, source: PlaybackSource, filePath: String?) {
		_uiState.update { state ->
			val current = state.playbackDetails?.takeIf { it.songId == song.id } ?: return@update state
			val codec = if (filePath != null) "FLAC" else
				formatCodecName(song.mimeType, song.fileExtension)
			val stereo = (current.channelCount ?: song.audioChannelCount) != 1
			val sampleRate = current.sampleRateHz ?: song.sampleRate ?: 44_100
			state.copy(playbackDetails = current.copy(
				source = source,
				decoder = "rodio (Symphonia)",
				codec = codec,
				pcmFormat = "PCM",
				outputFormat = "${formatSampleRate(sampleRate)} · ${if (stereo) "Stereo" else "Mono"}",
				outputDevice = "Default audio device"
			))
		}
	}

	// --- playback ---

	override fun playAt(index: Int) {
		if (isTransitioningBetweenTracks) return
		val songToPlay = _uiState.value.queue.getOrNull(index) ?: return

		isTransitioningBetweenTracks = true
		try {
			_uiState.update {
				it.copy(
					currentIndex = index,
					currentSong = songToPlay,
					isPaused = false,
					isLoading = false,
					progress = 0f
				)
			}
			scrobbleManager.onMediaChanged(songToPlay.id)
			transferTo(songToPlay)
		} finally {
			isTransitioningBetweenTracks = false
		}
	}

	/** Plays [song]: a complete store file if present; else for lossless it starts the mp3 fallback
	 *  now and hot-swaps to the FLAC file once fetched; else it streams in the selected container. */
	private fun transferTo(song: DomainSong) {
		viewModelScope.launch(Dispatchers.IO) {
			swappedAwayJob?.cancel()
			swappedAwayJob = null
			val stored = storedPath(song)
			if (stored != null) {
				playFileSource(song, stored)
				return@launch
			}
			if (!wantsLossless(song)) {
				playStreamingFallback(song)
				return@launch
			}
			// lossless wanted but no file yet: start mp3 now, fetch Raw, swap on ready
			playStreamingFallback(song)
			val fetch = audioFetcher.fetch(
				songId = song.id,
				quality = AudioQuality.Raw,
				extension = audioExtension(AudioQuality.Raw, song.fileExtension),
				download = false,
				url = { losslessStreamUrl(song) }
			) ?: return@launch
			val result = fetch.progress.first { it.done || it.failed }
			if (!result.done) {
				Logger.w("DesktopMediaPlayerViewModel", "lossless fetch of ${song.id} failed")
				return@launch
			}
			if (_uiState.value.currentSong?.id != song.id) return@launch
			val donePath = audioStore.bestComplete(song.id)?.let { audioStore.pathOf(it) }
				?: run { Logger.w("DesktopMediaPlayerViewModel", "lossless file for ${song.id} vanished"); return@launch }
			// mp3 was playing; swap to the FLAC file at the same position
			val positionMs = rodio.getPositionMs().coerceAtLeast(0L)
			val wasPaused = rodio.isPaused()
			swappedAwayJob = viewModelScope.launch(Dispatchers.IO) {
				playFileSource(song, donePath, startPosMs = positionMs)
				if (wasPaused) pause()
			}
		}
	}

	private fun playStreamingFallback(song: DomainSong) {
		try {
			rodio.playUrl(streamSource(song), loop = false)
			rodio.setVolume(volume.value)
			setDecoderInfo(song, PlaybackSource.Stream, filePath = null)
			scrobbleManager.onPlayStateChanged(true)
		} catch (e: Exception) {
			Logger.e("DesktopMediaPlayerViewModel", "Failed to stream ${song.id}", e)
			_uiState.update { it.copy(isPaused = true, isLoading = false) }
			snackBarManager.notify(Res.string.notice_server_unreachable)
		}
	}

	private fun playFileSource(song: DomainSong, path: String, startPosMs: Long = 0L) {
		try {
			rodio.playFile(path, loop = false)
			if (startPosMs > 0) runCatching { rodio.seekToMs(startPosMs) }
			rodio.setVolume(volume.value)
			setDecoderInfo(song, PlaybackSource.Download, filePath = path)
			scrobbleManager.onPlayStateChanged(true)
		} catch (e: Exception) {
			Logger.e("DesktopMediaPlayerViewModel", "Failed to play file $path", e)
			_uiState.update { it.copy(isPaused = true, isLoading = false) }
			snackBarManager.notify(Res.string.notice_server_unreachable)
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
			fileExtension = null,
			mimeType = null,
			filePath = radio.streamUrl,
			starredAt = null,
			musicBrainzId = null,
			explicitStatus = DomainExplicitStatus.Unknown,
			artists = emptyList(),
			albumArtists = emptyList(),
			isExternal = false
		)

		try {
			rodio.playRadio(radio.streamUrl)
			rodio.setVolume(volume.value)
			_uiState.update { state ->
				state.copy(
					queue = listOf(dummyRadioSong),
					currentIndex = 0,
					currentSong = dummyRadioSong,
					isLoading = false,
					isPaused = false
				)
			}
			scrobbleManager.onMediaChanged(radioId)
			scrobbleManager.onPlayStateChanged(true)
		} catch (e: Exception) {
			Logger.e("DesktopMediaPlayerViewModel", "Failed to start radio stream", e)
			_uiState.update { it.copy(isPaused = true, isLoading = false) }
			snackBarManager.notify(Res.string.notice_server_unreachable)
		}
	}

	override fun insertIntoQueue(index: Int, songs: List<DomainSong>) {
		_uiState.update { it.withInserted(index, songs) }
	}

	override fun removeFromQueue(index: Int) {
		_uiState.update { it.withRemoved(index) }
	}

	override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
		_uiState.update { it.withMoved(fromIndex, toIndex) }
	}

	override fun clearQueue() {
		swappedAwayJob?.cancel()
		swappedAwayJob = null
		rodio.clear()
		_uiState.update {
			it.copy(
				queue = emptyList(),
				currentSong = null,
				currentIndex = -1,
				progress = 0f,
				isPaused = true
			)
		}
		scrobbleManager.onPlayStateChanged(false)
	}

	override fun resume() {
		if (rodio.isPaused()) {
			rodio.play()
			_uiState.update { it.copy(isPaused = false) }
			scrobbleManager.onPlayStateChanged(true)
		} else if (rodio.isEmpty()) {
			playAt(_uiState.value.currentIndex)
		}
	}

	override fun pause() {
		rodio.pause()
		_uiState.update { it.copy(isPaused = true) }
		scrobbleManager.onPlayStateChanged(false)
	}

	override fun next() {
		if (_uiState.value.currentIndex + 1 < _uiState.value.queue.size) {
			playAt(_uiState.value.currentIndex + 1)
		}
	}

	override fun previous() {
		if (_uiState.value.currentIndex - 1 >= 0) {
			playAt(_uiState.value.currentIndex - 1)
		} else {
			seek(0f)
		}
	}

	override fun toggleShuffle() {
		_uiState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
	}

	override fun toggleRepeat() {
		_uiState.update {
			it.copy(repeatMode = if (it.repeatMode == 0) 1 else 0)
		}
	}

	override fun shufflePlay(collection: DomainSongCollection) {
		val shuffledSongs = playable(collection.songs).ifEmpty { return }.shuffled()
		playLog.recordCollection(collection)
		_uiState.update { state ->
			state.copy(
				queue = shuffledSongs,
				currentIndex = 0,
				currentSong = shuffledSongs.firstOrNull()
			)
		}
		playAt(0)
	}

	override fun setPlaybackSpeed(value: Float) {
		_uiState.update { it.copy(playbackSpeed = value) }
	}

	override fun seek(normalized: Float) {
		val durationMs = rodio.getDurationMs() ?: _uiState.value.currentSong?.duration?.inWholeMilliseconds ?: 0L
		if (durationMs <= 0) return
		val targetMs = (durationMs * normalized).toLong().coerceIn(0L, durationMs)
		_uiState.update { it.copy(progress = normalized) }
		viewModelScope.launch(Dispatchers.IO) {
			if (rodio.isSeekable()) {
				runCatching { rodio.seekToMs(targetMs) }
			} else {
				// HTTP/stream: rodio can't seek remote streams — restart the stream, no re-spool
				val song = _uiState.value.currentSong ?: return@launch
				val wasPaused = rodio.isPaused()
				playStreamingFallback(song)
				if (wasPaused) pause()
			}
		}
	}

	override fun setVolume(value: Float) {
		val v = value.coerceIn(0f, 1f)
		volume.value = v
		rodio.setVolume(v)
		preferenceManager.playerVolume = v
	}

	override fun syncPlayerWithState(state: PlayerUiState) {
		// State restored from the server: paused, no audio started.
		swappedAwayJob?.cancel()
		swappedAwayJob = null
		rodio.clear()
	}

	private fun progress(): Float {
		val durationMs = _uiState.value.currentSong?.duration?.inWholeMilliseconds ?: 0L
		if (durationMs <= 0) return 0f
		val pos = rodio.getPositionMs().coerceIn(0L, durationMs)
		return (pos.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
	}

	private fun onTrackEnded() {
		val now = System.currentTimeMillis()
		if (now - lastTrackEndTime < 1000) {
			rapidEndCount++
		} else {
			rapidEndCount = 0
		}
		lastTrackEndTime = now

		if (rapidEndCount >= 3) {
			Logger.w("DesktopMediaPlayerViewModel", "Rapid track ending detected, pausing playback")
			pause()
			return
		}

		scrobbleManager.onPlayStateChanged(false)
		when {
			_uiState.value.repeatMode == 1 -> playAt(_uiState.value.currentIndex)
			_uiState.value.currentIndex + 1 < _uiState.value.queue.size -> playAt(_uiState.value.currentIndex + 1)
			else -> pause()
		}
	}
}
