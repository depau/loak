package eu.depau.loak.shared

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.ScrobbleManager
import eu.depau.loak.domain.manager.ScrobblePlayerSource
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.repositories.PlayerStateRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine

/**
 * Desktop (JVM) implementation of [MediaPlayerViewModel], using Java Sound.
 *
 * MP3 decoding comes from mp3spi (JLayer); WAV/AIFF are native. FLAC streaming
 * and gapless playback are not implemented yet — see DESIGN_CHANGES.
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
	override val snackBarManager: SnackBarManager
) : MediaPlayerViewModel(
	stateRepository = stateRepository,
	songRepository = songRepository,
	connectivityManager = connectivityManager,
	downloadManager = downloadManager,
	preferenceManager = preferenceManager,
	queueSyncManager = queueSyncManager
) {

	private val player = DesktopAudioPlayer()
	private var isTransitioningBetweenTracks = false
	private var progressJob: Job? = null

	private val scrobbleManager = ScrobbleManager(
		playerSource = player,
		connectivityManager = connectivityManager,
		syncManager = syncManager,
		sessionManager = sessionManager,
		scope = viewModelScope,
		preferenceManager = preferenceManager
	)

	override val volume: StateFlow<Float>
		field = MutableStateFlow(preferenceManager.playerVolume)

	init {
		progressJob = viewModelScope.launch(Dispatchers.IO) {
			while (true) {
				delay(250)
				_uiState.update { state ->
					state.copy(progress = player.progress().coerceIn(0f, 1f))
				}
				if (player.ended && !isTransitioningBetweenTracks) {
					onTrackEnded()
				}
			}
		}
	}

	override fun onCleared() {
		super.onCleared()
		player.release()
		progressJob?.cancel()
	}

	// --- stream URL (same logic as iOS) ---

	private fun getStreamUrl(id: String): String {
		val isCellular = connectivityManager.isCellular.value
		val bitrate = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.bitrateIos else preferenceManager.streamingQualityWifi.bitrateIos
		}
		val container = if (preferenceManager.isAdvancedTranscodingActive) {
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

	private fun resolveSourceUrl(song: DomainSong): String? =
		when {
			song.id.startsWith("radio_") && !song.filePath.isNullOrEmpty() -> song.filePath
			else -> {
				val localPath = downloadManager.getDownloadedFilePath(song.id)
				if (localPath != null) "file://$localPath"
				else getStreamUrl(song.id)
			}
		}

	// --- playback control ---

	override fun playAt(index: Int) {
		if (isTransitioningBetweenTracks) return
		val songToPlay = _uiState.value.queue.getOrNull(index) ?: return
		val url = resolveSourceUrl(songToPlay) ?: return

		isTransitioningBetweenTracks = true
		try {
			_uiState.update {
				it.copy(
					currentIndex = index,
					currentSong = songToPlay,
					isPaused = false,
					isLoading = false
				)
			}
			player.playUrl(url)
			player.volume = preferenceManager.playerVolume
			scrobbleManager.onMediaChanged(songToPlay.id)
			scrobbleManager.onPlayStateChanged(true)
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

		player.playUrl(radio.streamUrl)
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
		player.stopStream()
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
		player.resume()
		_uiState.update { it.copy(isPaused = false) }
		scrobbleManager.onPlayStateChanged(true)
	}

	override fun pause() {
		player.pause()
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
		val shuffledSongs = collection.songs.shuffled()
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
		// ponytail: SourceDataLine can't change rate; playback-speed is stored for UI.
	}

	override fun seek(normalized: Float) {
		if (player.durationMs <= 0) return
		val targetMs = (player.durationMs * normalized).toLong()
		player.seekTo(targetMs)
		_uiState.update { it.copy(progress = normalized) }
	}

	override fun setVolume(value: Float) {
		player.volume = value
		preferenceManager.playerVolume = value
	}

	override fun syncPlayerWithState(state: PlayerUiState) {
		// State restored from the server: paused, no audio started.
		player.stopStream()
	}

	private fun onTrackEnded() {
		scrobbleManager.onPlayStateChanged(false)
		when {
			_uiState.value.repeatMode == 1 -> playAt(_uiState.value.currentIndex)
			_uiState.value.currentIndex + 1 < _uiState.value.queue.size -> playAt(_uiState.value.currentIndex + 1)
			else -> pause()
		}
	}

	private class DesktopAudioPlayer : ScrobblePlayerSource {
		private var line: SourceDataLine? = null
		private var stream: AudioInputStream? = null
		private var source: HttpURLConnection? = null
		private var buffer = ByteArray(64 * 1024)
		private var playing = false
		private var paused = false
		private var atEnd = false
		private var totalFrames: Long = 0

		override var currentPosition: Long = 0
			private set
		override var duration: Long = 0
			private set
		override var isPlaying: Boolean
			get() = playing && !paused
			set(value) {}

		val ended: Boolean get() = atEnd

		val durationMs: Long
			get() = if (duration > 0 && frameRate > 0) duration * 1000 / frameRate.toLong() else 0

		var frameRate: Float = 0f

		fun playUrl(url: String) {
			try {
				stopStream()
				atEnd = false
				playing = false
				paused = false

				val conn = if (url.startsWith("file:"))
					null else URL(url).openConnection() as HttpURLConnection
				source = conn

				val inStream = when {
					conn != null -> {
						conn.connect()
						if (conn.responseCode !in 200..299) {
							Logger.e("DesktopAudioPlayer", "HTTP ${conn.responseCode} for $url")
							return
						}
						conn.inputStream
					}

					else -> File(URL(url).toURI()).inputStream()
				}

				val audioStream = AudioSystem.getAudioInputStream(inStream)
				stream = audioStream
				val format = audioStream.format
				frameRate = format.frameRate
				// estimate duration from the stream when the header says so
				totalFrames = try {
					AudioSystem.getAudioFileFormat(inStream)?.frameLength?.toLong() ?: -1
				} catch (e: Exception) {
					-1L
				}
				duration = songDurationFromFrames(totalFrames, format)

				val info = DataLine.Info(SourceDataLine::class.java, format)
				val l = AudioSystem.getLine(info) as SourceDataLine
				line = l
				l.open(format)
				l.start()
				playing = true

				Thread {
					try {
						val buf = buffer
						while (playing) {
							if (paused) {
								Thread.sleep(50)
								continue
							}
							val read = audioStream.read(buf, 0, buf.size)
							if (read < 0) {
								atEnd = true
								break
							}
							if (read > 0) {
								l.write(buf, 0, read)
								currentPosition += read
							}
						}
					} catch (e: Exception) {
						Logger.e("DesktopAudioPlayer", "playback error", e)
					} finally {
						try {
							l.drain()
							l.stop()
							l.close()
						} catch (_: Exception) {}
						playing = false
						cleanupStream()
					}
				}.start()
			} catch (e: Exception) {
				Logger.e("DesktopAudioPlayer", "failed to play $url", e)
				atEnd = true
				cleanupStream()
			}
		}

		private fun songDurationFromFrames(frames: Long, format: AudioFormat): Long {
			if (frames > 0 && format.frameRate > 0) return (frames / format.frameRate.toLong()).toLong()
			return 0
		}

		fun resume() {
			paused = false
			line?.start()
		}

		fun pause() {
			if (line != null) {
				line!!.stop()
				paused = true
			}
		}

		fun stopStream() {
			playing = false
			atEnd = false
			try {
				line?.flush()
				line?.drain()
				line?.stop()
				line?.close()
			} catch (_: Exception) {}
			line = null
			cleanupStream()
			currentPosition = 0
		}

		@Suppress("UNUSED_PARAMETER")
		fun seekTo(ms: Long) {
			// ponytail: Java Sound has no seamless seek and this player isn't a
			// stream-restart loop yet. The UI updates progress (ViewModel-level);
			// audio-side seeking needs a restart at a byte estimate — DESIGN_CHANGES.
		}

		fun progress(): Float =
			if (durationMs > 0) currentPosition.toFloat() / durationMs else 0f

		var volume: Float = 1f
			set(value) {
				field = value.coerceIn(0f, 1f)
				val l = line ?: return
				runCatching {
					val control = l.getControl(FloatControl.Type.MASTER_GAIN)
					if (control is FloatControl) {
						val min = control.minimum
						val dB = if (field <= 0f) min else (20.0 * Math.log10(field.toDouble())).toFloat().coerceIn(control.minimum, 0.5f)
						control.value = dB.coerceIn(control.minimum, control.maximum)
					}
				}.onFailure { Logger.w("DesktopAudioPlayer", "no volume control") }
			}

		fun release() {
			stopStream()
		}

		private fun cleanupStream() {
			try {
				stream?.close()
			} catch (_: Exception) {}
			stream = null
			try {
				source?.disconnect()
			} catch (_: Exception) {}
			source = null
		}
	}
}
