@file:OptIn(ExperimentalForeignApi::class)

package eu.depau.loak.shared

import eu.depau.loak.di.COVER_ART_MEDIUM
import androidx.lifecycle.viewModelScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.core.component.inject
import eu.depau.loak.data.database.entities.TransferCategory
import eu.depau.loak.domain.manager.AudioFetcher
import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.IOSScrobbleManager
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.manager.audioExtension
import eu.depau.loak.domain.manager.prefetchTargets
import eu.depau.loak.domain.models.AudioQuality
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.repositories.PlayerStateRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionPortDescription
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.AVURLAsset
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.preferredForwardBufferDuration
import platform.AVFoundation.removeTimeObserver
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.setRate
import platform.AVFoundation.timeControlStatus
import platform.CoreGraphics.CGSizeMake
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.addValue
import platform.Foundation.dataTaskWithRequest
import platform.MediaPlayer.MPChangePlaybackPositionCommandEvent
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyAlbumTitle
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusCommandFailed
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait

class IOSMediaPlayerViewModel(
	stateRepository: PlayerStateRepository,
	songRepository: SongRepository,
	downloadManager: DownloadManager,
	connectivityManager: ConnectivityManager,
	preferenceManager: PreferenceManager,
	queueSyncManager: QueueSyncManager,
	syncManager: SyncManager,
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
	private val player = AVPlayer()
	private var timeObserver: Any? = null
	private var playbackEndObserver: Any? = null
	private val scrobbleManager =
		IOSScrobbleManager(
			player,
			viewModelScope,
			connectivityManager,
			syncManager,
			sessionManager,
			preferenceManager
		)
	private val audioStore: AudioStore by inject()
	private val audioFetcher: AudioFetcher by inject()
	private val stats: NetworkStatsManager by inject()
	private var pendingSyncState: PlayerUiState? = null

	/** Resolves the current song's source and swaps it in; a newer track change cancels it. */
	private var loadJob: Job? = null
	private var prefetchJob: Job? = null

	/** Current and prefetched songs, protected from eviction. */
	private var retained = emptySet<String>()

	init {
		setupAudioSession()
		setupRemoteCommands()
		viewModelScope.launch {
			// the progress only matters on screen; pause() saves it otherwise
			uiVisible.collect { visible ->
				if (visible) startProgressObserver() else stopProgressObserver()
			}
		}
		viewModelScope.launch {
			_uiState
				.distinctUntilChanged { old, new ->
					old.currentIndex == new.currentIndex &&
						old.queue === new.queue &&
						old.isPaused == new.isPaused
				}
				.collect { schedulePrefetch() }
		}

		playbackEndObserver = NSNotificationCenter.defaultCenter.addObserverForName(
			name = AVPlayerItemDidPlayToEndTimeNotification,
			`object` = null,
			queue = NSOperationQueue.mainQueue
		) { _ ->
			val currentItem = player.currentItem
			if (currentItem != null) {
				val duration = currentItem.duration
				val currentTime = player.currentTime()
				val durationSeconds = CMTimeGetSeconds(duration)
				val currentSeconds = CMTimeGetSeconds(currentTime)

				if (!durationSeconds.isNaN() && !currentSeconds.isNaN() &&
					(durationSeconds - currentSeconds) < 1.0
				) {
					when (_uiState.value.repeatMode) {
						1 -> {
							seek(0f); resume()
						}

						else -> next()
					}
				}
			}
		}

		pendingSyncState?.let { state ->
			syncPlayerWithState(state)
			pendingSyncState = null
		}
	}

	private fun setupAudioSession() {
		val audioSession = AVAudioSession.sharedInstance()
		try {
			audioSession.setCategory(AVAudioSessionCategoryPlayback, error = null)
			audioSession.setActive(true, error = null)
		} catch (e: Exception) {
			Logger.e("IOSMediaPlayerViewModel", "Failed to setup audio session!", e)
		}
	}

	private fun setupRemoteCommands() {
		val commandCenter = MPRemoteCommandCenter.sharedCommandCenter()

		commandCenter.playCommand.addTargetWithHandler {
			resume()
			MPRemoteCommandHandlerStatusSuccess
		}

		commandCenter.pauseCommand.addTargetWithHandler {
			pause()
			MPRemoteCommandHandlerStatusSuccess
		}

		commandCenter.nextTrackCommand.addTargetWithHandler {
			next()
			MPRemoteCommandHandlerStatusSuccess
		}

		commandCenter.previousTrackCommand.addTargetWithHandler {
			previous()
			MPRemoteCommandHandlerStatusSuccess
		}

		commandCenter.changePlaybackPositionCommand.addTargetWithHandler { event ->
			val positionEvent = event as? MPChangePlaybackPositionCommandEvent
			if (positionEvent != null) {
				seekToTime(positionEvent.positionTime)
				MPRemoteCommandHandlerStatusSuccess
			} else {
				MPRemoteCommandHandlerStatusCommandFailed
			}
		}
	}

	override fun playAt(index: Int) {
		val queue = _uiState.value.queue
		// offline, songs with no stored copy are skipped
		val i = (index.coerceAtLeast(0) until queue.size).firstOrNull { canPlay(queue[it]) }
			?: return
		val songToPlay = queue[i]

		player.pause()
		_uiState.update {
			it.copy(currentIndex = i, currentSong = songToPlay, isPaused = false, isLoading = false)
		}
		scrobbleManager.onMediaChanged(songToPlay.id)
		scrobbleManager.onIsPlayingChanged(true)

		loadJob?.cancel()
		loadJob = viewModelScope.launch {
			val item = songItem(songToPlay)
			if (item == null) {
				// offline and evicted meanwhile
				_uiState.update { it.copy(isPaused = true) }
				return@launch
			}
			player.replaceCurrentItemWithPlayerItem(null)
			player.replaceCurrentItemWithPlayerItem(item)
			if (!_uiState.value.isPaused) player.play()
			updateNowPlayingInfo(songToPlay)
			updateIosPlaybackDetails(songToPlay.id)
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

		val url = NSURL.URLWithString(radio.streamUrl)
		loadJob?.cancel()
		if (url != null) {
			player.pause()
			player.replaceCurrentItemWithPlayerItem(null)
			player.replaceCurrentItemWithPlayerItem(createAVPlayerItem(url))
			player.play()
		}

		_uiState.update { state ->
			state.copy(
				queue = listOf(dummyRadioSong),
				currentIndex = 0,
				currentSong = dummyRadioSong,
				isLoading = true
			)
		}

		scrobbleManager.onMediaChanged(radioId)
		scrobbleManager.onIsPlayingChanged(true)
		updateNowPlayingInfo(dummyRadioSong)
		updateIosPlaybackDetails(dummyRadioSong.id)
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
		loadJob?.cancel()
		player.pause()
		player.replaceCurrentItemWithPlayerItem(null)
		_uiState.update {
			it.copy(
				queue = emptyList(),
				currentSong = null,
				currentIndex = -1,
				progress = 0f,
				isPaused = true
			)
		}
		scrobbleManager.onIsPlayingChanged(false)
		updateNowPlayingInfo(null)
	}

	override fun resume() {
		player.play()
		_uiState.update { it.copy(isPaused = false) }
		scrobbleManager.onIsPlayingChanged(true)
		updateNowPlayingInfo(_uiState.value.currentSong)
	}

	override fun pause() {
		player.pause()
		// the progress observer only runs on screen: the saved position comes from here
		val progress = currentProgress()
		_uiState.update { it.copy(isPaused = true, progress = progress ?: it.progress) }
		scrobbleManager.onIsPlayingChanged(false)
		updateNowPlayingInfo(_uiState.value.currentSong)
	}

	override fun next() {
		if (_uiState.value.currentIndex + 1 < _uiState.value.queue.size) {
			playAt(_uiState.value.currentIndex + 1)
		}
	}

	override fun previous() {
		if ((_uiState.value.currentIndex - 1) >= 0) {
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
		player.setRate(value)
		_uiState.update { it.copy(playbackSpeed = value) }
	}

	override fun seek(normalized: Float) {
		val duration = player.currentItem?.duration ?: return
		val totalSeconds = CMTimeGetSeconds(duration)
		if (!totalSeconds.isNaN()) {
			seekToTime(totalSeconds * normalized)
			_uiState.update { it.copy(progress = normalized) }
		}
	}

	private fun seekToTime(seconds: Double) {
		val cmTime = CMTimeMakeWithSeconds(seconds, preferredTimescale = 1000)
		player.seekToTime(cmTime)
	}

	private fun startProgressObserver() {
		if (timeObserver != null) return
		val interval = CMTimeMake(1, 20)
		timeObserver = player.addPeriodicTimeObserverForInterval(interval, null) { _ ->
			// also called when playback stops, e.g. to wait for data
			val waiting = player.timeControlStatus ==
				AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
			if (_uiState.value.isLoading != waiting) _uiState.update { it.copy(isLoading = waiting) }
			currentProgress()?.let { progress -> _uiState.update { it.copy(progress = progress) } }
		}
	}

	private fun stopProgressObserver() {
		timeObserver?.let { player.removeTimeObserver(it) }
		timeObserver = null
	}

	private fun currentProgress(): Float? {
		val total = CMTimeGetSeconds(player.currentItem?.duration ?: return null)
		if (total.isNaN() || total <= 0) return null
		return (CMTimeGetSeconds(player.currentTime()) / total).toFloat()
	}

	/**
	 * Fetches the songs after the current one into the store, one at a time while playing, so
	 * they play from disk and the radio does one burst per track. The current song isn't
	 * fetched: AVPlayer can't follow a growing file, so a song not stored streams directly
	 * (see [songItem]) and its own prefetch, if one was running, stops to not load it twice.
	 */
	private fun schedulePrefetch() {
		val state = _uiState.value
		val upcoming = state.queue.drop(state.currentIndex + 1).take(3)
			.map { song -> song.takeUnless { it.id.startsWith("radio_") } }
		val current = state.currentSong?.id?.takeUnless { it.startsWith("radio_") }
		val targets = prefetchTargets(
			current, upcoming.map { it?.id }, connectivityManager.isCellular.value
		)
		val keep = setOfNotNull(current) + targets
		(keep - retained).forEach(audioStore::retain)
		(retained - keep).forEach(audioStore::release)
		retained = keep
		audioFetcher.cancelExcept(targets.toSet())

		prefetchJob?.cancel()
		if (state.isPaused) return
		prefetchJob = viewModelScope.launch {
			for (song in upcoming.filterNotNull().filter { it.id in targets }) {
				if (!connectivityManager.isOnline.value) break
				val wanted = streamSource(song.id).second
				if (audioStore.playable(song.id, wanted, online = true) != null) continue
				audioFetcher.fetch(song.id, wanted, audioExtension(wanted, song.fileExtension)) {
					streamSource(song.id).first
				}?.progress?.first { it.done || it.failed }
			}
		}
	}

	private fun updateNowPlayingInfo(song: DomainSong?) {
		if (song == null) {
			MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
			return
		}

		val info = mutableMapOf<Any?, Any?>()
		info[MPMediaItemPropertyTitle] = song.title
		info[MPMediaItemPropertyArtist] = song.artistName
		info[MPMediaItemPropertyAlbumTitle] = song.albumTitle
		info[MPNowPlayingInfoPropertyPlaybackRate] = if (_uiState.value.isPaused) 0.0 else 1.0

		val duration = player.currentItem?.duration
		if (duration != null) {
			val seconds = CMTimeGetSeconds(duration)
			if (!seconds.isNaN()) {
				info[MPMediaItemPropertyPlaybackDuration] = seconds
			}
		}

		info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = CMTimeGetSeconds(player.currentTime())

		info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(
			boundsSize = CGSizeMake(512.0, 512.0),
			requestHandler = { _ ->
				runCatching {
					val url = song.coverArtId
						?.let { sessionManager.getCoverArtUrl(it, COVER_ART_MEDIUM) }
						?.let { NSURL.URLWithString(it) } ?: return@runCatching null

					val request = NSMutableURLRequest.requestWithURL(url).apply {
						val customHeaders = preferenceManager.customHeadersMap()
						if (customHeaders.isNotEmpty()) {
							customHeaders.forEach { (key, value) ->
								addValue(value, forHTTPHeaderField = key)
							}
						}
					}

					var fetchedData: NSData? = null
					val semaphore = dispatch_semaphore_create(0)

					val task =
						NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, _ ->
							fetchedData = data
							dispatch_semaphore_signal(semaphore)
						}
					task.resume()

					dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)

					fetchedData?.let { UIImage(data = it) }
				}.getOrNull() ?: UIImage()
			}
		)

		MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info
	}

	override fun onCleared() {
		super.onCleared()
		audioFetcher.cancelExcept(emptySet())
		retained.forEach(audioStore::release)
		retained = emptySet()
		stopProgressObserver()
		playbackEndObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
		player.replaceCurrentItemWithPlayerItem(null)
	}

	override fun syncPlayerWithState(state: PlayerUiState) {
		if (state.queue.isEmpty() || player.currentItem != null) return

		val index = if (state.currentIndex in state.queue.indices) state.currentIndex else 0
		val song = state.queue.getOrNull(index) ?: return

		loadJob?.cancel()
		loadJob = viewModelScope.launch {
			val item = songItem(song) ?: return@launch
			player.setRate(state.playbackSpeed)
			player.pause()
			player.replaceCurrentItemWithPlayerItem(null)
			player.replaceCurrentItemWithPlayerItem(item)

			if (!song.id.startsWith("radio_")) {
				val durationMs = song.duration.inWholeMilliseconds
				if (durationMs > 0) {
					val positionSeconds = (state.progress * durationMs) / 1000.0
					seekToTime(positionSeconds)
				}
			}

			updateNowPlayingInfo(song)
			updateIosPlaybackDetails(song.id)
		}
	}

	private fun updateIosPlaybackDetails(songId: String) {
		val session = AVAudioSession.sharedInstance()
		val sampleRate = session.sampleRate.toInt()
		val outputs = session.currentRoute.outputs
		val port = outputs.firstOrNull() as? AVAudioSessionPortDescription
		val deviceName = port?.portName
		_uiState.update { state ->
			val current = state.playbackDetails?.takeIf { it.songId == songId } ?: return@update state
			state.copy(
				playbackDetails = current.copy(
					decoder = "AVFoundation",
					sampleRateHz = if (sampleRate > 0) sampleRate else current.sampleRateHz,
					outputDevice = deviceName ?: current.outputDevice
				)
			)
		}
	}

	private fun createAVPlayerItem(url: NSURL): AVPlayerItem {
		val headers = preferenceManager.customHeadersMap()
		if (headers.isEmpty() || url.isFileURL()) {
			return AVPlayerItem(url)
		}
		val options: Map<Any?, Any?> = mapOf("AVURLAssetHTTPHeaderFieldsKey" to headers)

		return AVPlayerItem(AVURLAsset(uRL = url, options = options))
	}

	/** The URL to stream song [id] from, and the [AudioQuality] it comes at. */
	private fun streamSource(id: String): Pair<String, AudioQuality> {
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
		val format = container?.takeIf { it.isNotBlank() }
		val url = sessionManager.api.getStreamUrl(id, bitrate, format, estimateContentLength = true)
		return url to AudioQuality.of(format, bitrate)
	}

	/**
	 * The item to play [song] from: a stored copy if it's good enough (any, offline), else the
	 * stream; null offline when there's no copy.
	 */
	private suspend fun songItem(song: DomainSong): AVPlayerItem? {
		if (song.id.startsWith("radio_")) {
			return song.filePath?.let { NSURL.URLWithString(it) }?.let(::createAVPlayerItem)
		}
		val (url, wanted) = streamSource(song.id)
		val online = connectivityManager.isOnline.value
		audioStore.playable(song.id, wanted, online)?.let { entry ->
			val path = audioStore.pathOf(entry)
			// not there: evicted meanwhile
			if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
				audioStore.touch(entry)
				stats.record(TransferCategory.CACHE_HIT, entry.bytes, requests = 1)
				return AVPlayerItem(NSURL.fileURLWithPath(path))
			}
		}
		if (!online) return null
		// AVPlayer's own transfer isn't observable: only the request is counted
		stats.record(TransferCategory.STREAM, requests = 1)
		return createAVPlayerItem(NSURL.URLWithString(url) ?: return null).apply {
			// buffer far ahead, like Android: the radio fills it in a few long bursts
			preferredForwardBufferDuration = 600.0
		}
	}
}
