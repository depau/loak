package eu.depau.loak.shared

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
import eu.depau.loak.domain.models.AudioQuality
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
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
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
 * Requests MP3 container from Subsonic for universal compatibility across all
 * server formats (FLAC, AAC, Opus, Vorbis, etc.). The stream is decoded via
 * mp3spi into 16-bit signed PCM and written to a [SourceDataLine].
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

	private val player = DesktopAudioPlayer()
	private var isTransitioningBetweenTracks = false
	private var progressJob: Job? = null
	private var lastTrackEndTime = 0L
	private var rapidEndCount = 0

	private val scrobbleManager = ScrobbleManager(
		playerSource = player,
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
					state.copy(progress = player.progress().coerceIn(0f, 1f))
				}
				if (player.ended && !isTransitioningBetweenTracks) {
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
		player.release()
		progressJob?.cancel()
		mediaControlManager.stop()
	}

	// --- stream URL ---

	private fun getStreamUrl(id: String): String {
		val isCellular = connectivityManager.isCellular.value
		val bitrate = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
		} else {
			val quality = if (isCellular) preferenceManager.streamingQualityCellular else preferenceManager.streamingQualityWifi
			if (quality.bitrateIos > 0) quality.bitrateIos else 320
		}
		val container = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customFormatCellular else preferenceManager.customFormatWifi
		} else {
			"mp3"
		}
		return sessionManager.api.getStreamUrl(
			id = id,
			maxBitRate = bitrate,
			format = container?.takeIf { it.isNotBlank() } ?: "mp3"
		) + "&estimateContentLength=true"
	}

	override fun requestedQuality(): AudioQuality {
		val isCellular = connectivityManager.isCellular.value
		val bitrate = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
		} else {
			val quality = if (isCellular) preferenceManager.streamingQualityCellular else preferenceManager.streamingQualityWifi
			if (quality.bitrateIos > 0) quality.bitrateIos else 320
		}
		val container = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customFormatCellular else preferenceManager.customFormatWifi
		} else {
			"mp3"
		}
		return AudioQuality.of(container?.takeIf { it.isNotBlank() } ?: "mp3", bitrate)
	}

	private fun resolveSourceUrl(song: DomainSong): String? =
		when {
			song.id.startsWith("radio_") && !song.filePath.isNullOrEmpty() -> song.filePath
			else -> {
				val localPath = downloadManager.getDownloadedFilePath(song.id)
				// Only use local file if it's already decoded PCM / WAV or MP3 that Java Sound can decode.
				// File.toURI() percent-encodes properly: a raw "file://$localPath" breaks URI parsing on
				// spaces (e.g. "Application Support") and literal %NN in the store's file names (song ids
				// are URL-encoded, so @ shows up as %40 in the on-disk name).
				if (localPath != null && (localPath.endsWith(".mp3", true) || localPath.endsWith(".wav", true))) {
					File(localPath).toURI().toString()
				} else {
					getStreamUrl(song.id)
				}
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
					isLoading = false,
					progress = 0f
				)
			}
			val durationMs = songToPlay.duration.inWholeMilliseconds
			player.playUrl(url, durationMs)
			player.volume = preferenceManager.playerVolume
			scrobbleManager.onMediaChanged(songToPlay.id)
			scrobbleManager.onPlayStateChanged(true)
		} catch (e: Exception) {
			Logger.e("DesktopMediaPlayerViewModel", "Failed to start playback for ${songToPlay.id}", e)
			_uiState.update { it.copy(isPaused = true, isLoading = false) }
			snackBarManager.notify(Res.string.notice_server_unreachable)
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

		try {
			player.playUrl(radio.streamUrl, 0L)
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
		if (!player.resume()) {
			playAt(_uiState.value.currentIndex)
			return
		}
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
		if (player.duration <= 0) return
		val targetMs = (player.duration * normalized).toLong()
		_uiState.update { it.copy(progress = normalized) }
		// a seek back reopens the stream: network, so not on the UI thread
		viewModelScope.launch(Dispatchers.IO) { player.seekTo(targetMs) }
	}

	override fun setVolume(value: Float) {
		val v = value.coerceIn(0f, 1f)
		volume.value = v
		player.volume = v
		preferenceManager.playerVolume = v
	}

	override fun syncPlayerWithState(state: PlayerUiState) {
		// State restored from the server: paused, no audio started.
		player.stopStream()
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

	private class DesktopAudioPlayer : ScrobblePlayerSource {
		private var line: SourceDataLine? = null
		private var stream: AudioInputStream? = null
		private var source: HttpURLConnection? = null
		private val buffer = ByteArray(64 * 1024)
		private var playing = false
		private var paused = false
		private var atEnd = false

		private var trackDurationMs: Long = 0
		private var bytesPerSecond: Long = 0
		private var frameSize: Int = 1
		private var totalDecodedBytes: Long = 0
		private var url: String? = null
		/** Bumped by every new stream: a playback thread only touches the player while it's current. */
		@Volatile private var session = 0
		/** A seek forward the playback thread carries out: decode and drop up to this position. */
		@Volatile private var pendingSeekMs: Long = -1
		/** Where a seek is heading, shown as the position until the stream gets there. */
		@Volatile private var seekingTo: Long = -1

		override var currentPosition: Long = 0
			private set

		override val duration: Long
			get() = trackDurationMs

		override var isPlaying: Boolean
			get() = playing && !paused
			set(value) {}

		val ended: Boolean get() = atEnd

		/** [startMs] > 0 starts there (the playback thread skips ahead before any audio). */
		fun playUrl(url: String, durationMs: Long, startMs: Long = 0, startPaused: Boolean = false) {
			stopStream()
			this.url = url
			pendingSeekMs = if (startMs > 0) startMs else -1
			seekingTo = pendingSeekMs
			atEnd = false
			playing = false
			paused = startPaused
			currentPosition = 0
			trackDurationMs = durationMs

			try {
				val conn = if (url.startsWith("file:"))
					null else URL(url).openConnection() as HttpURLConnection
				source = conn

				val rawIn = when {
					conn != null -> {
						conn.connect()
						if (conn.responseCode !in 200..299) {
							throw IOException("HTTP ${conn.responseCode} for $url")
						}
						conn.inputStream
					}
					else -> File(URL(url).toURI()).inputStream()
				}
				val bufferedIn = BufferedInputStream(rawIn)

				val audioStream = AudioSystem.getAudioInputStream(bufferedIn)
				val baseFormat = audioStream.format

				// If the audio format is not PCM_SIGNED (e.g. MP3 via mp3spi), convert to PCM_SIGNED
				val decodedFormat = if (baseFormat.encoding != AudioFormat.Encoding.PCM_SIGNED) {
					AudioFormat(
						AudioFormat.Encoding.PCM_SIGNED,
						baseFormat.sampleRate,
						16,
						baseFormat.channels,
						baseFormat.channels * 2,
						baseFormat.sampleRate,
						false
					)
				} else {
					baseFormat
				}

				val pcmStream = if (baseFormat.encoding != AudioFormat.Encoding.PCM_SIGNED) {
					AudioSystem.getAudioInputStream(decodedFormat, audioStream)
				} else {
					audioStream
				}
				stream = pcmStream

				bytesPerSecond = (decodedFormat.sampleRate * decodedFormat.frameSize).toLong()
				frameSize = decodedFormat.frameSize.coerceAtLeast(1)
				totalDecodedBytes = if (durationMs > 0 && bytesPerSecond > 0) {
					durationMs * bytesPerSecond / 1000L
				} else {
					0L
				}

				val info = DataLine.Info(SourceDataLine::class.java, decodedFormat)
				val l = AudioSystem.getLine(info) as SourceDataLine
				line = l
				l.open(decodedFormat)
				applyVolumeToLine(l, volume)
				if (!startPaused) l.start()
				playing = true
				val mySession = ++session
				val current = { session == mySession }

				Thread {
					try {
						val buf = buffer
						while (playing && current()) {
							val seek = pendingSeekMs
							if (seek >= 0) {
								pendingSeekMs = -1
								skipTo(pcmStream, buf, seek)
								l.flush()
								seekingTo = -1
							}
							if (paused) {
								Thread.sleep(50)
								continue
							}
							val read = pcmStream.read(buf, 0, buf.size)
							if (read < 0) {
								l.drain()
								if (current()) atEnd = true
								break
							}
							if (read > 0) {
								l.write(buf, 0, read)
								if (bytesPerSecond > 0 && current()) {
									currentPosition += (read.toLong() * 1000L) / bytesPerSecond
								}
							}
						}
					} catch (e: Exception) {
						if (current()) Logger.e("DesktopAudioPlayer", "playback stream error", e)
					} finally {
						// this thread's own line and stream; a newer stream may be playing by now
						try {
							l.drain()
							l.stop()
							l.close()
						} catch (_: Exception) {}
						runCatching { pcmStream.close() }
						if (current()) {
							playing = false
							cleanupStream()
						}
					}
				}.start()
			} catch (e: Exception) {
				Logger.e("DesktopAudioPlayer", "failed to play $url", e)
				atEnd = false
				cleanupStream()
				throw e
			}
		}

		private fun applyVolumeToLine(l: SourceDataLine?, vol: Float) {
			if (l == null) return
			runCatching {
				val control = l.getControl(FloatControl.Type.MASTER_GAIN)
				if (control is FloatControl) {
					val min = control.minimum
					val dB = if (vol <= 0f) min else (20.0 * Math.log10(vol.toDouble())).toFloat().coerceIn(control.minimum, 0.5f)
					control.value = dB.coerceIn(control.minimum, control.maximum)
				}
			}.onFailure { Logger.w("DesktopAudioPlayer", "no volume control") }
		}

		fun resume(): Boolean {
			val line = line ?: return false
			paused = false
			line.start()
			return true
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

		/**
		 * Java Sound streams only go forward: a seek ahead decodes and drops the audio up to the
		 * target, a seek back reopens the stream and does the same from the start.
		 * ponytail: seeking far into a long track re-downloads and decodes up to there; HTTP range
		 * requests would need a byte offset per format.
		 */
		fun seekTo(ms: Long) {
			val target = ms.coerceIn(0, trackDurationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
			val url = url ?: return
			if (target < currentPosition || !playing) {
				runCatching { playUrl(url, trackDurationMs, target, startPaused = paused) }
				return
			}
			seekingTo = target
			pendingSeekMs = target
		}

		/** Reads and drops whole frames from [stream] until [currentPosition] reaches [ms]. */
		private fun skipTo(stream: AudioInputStream, buf: ByteArray, ms: Long) {
			if (bytesPerSecond <= 0) return
			var left = (ms - currentPosition) * bytesPerSecond / 1000L
			left -= left % frameSize
			while (left > 0) {
				val read = stream.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
				if (read < 0) break
				left -= read
				currentPosition += read.toLong() * 1000L / bytesPerSecond
			}
		}

		fun progress(): Float {
			val position = seekingTo.takeIf { it >= 0 } ?: currentPosition
			return if (trackDurationMs > 0) (position.toFloat() / trackDurationMs.toFloat()).coerceIn(0f, 1f) else 0f
		}

		var volume: Float = 1f
			set(value) {
				field = value.coerceIn(0f, 1f)
				applyVolumeToLine(line, field)
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
