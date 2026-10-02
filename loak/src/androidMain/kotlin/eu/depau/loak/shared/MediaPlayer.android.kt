package eu.depau.loak.shared

import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ktor.KtorDataSource
import androidx.media3.exoplayer.BaseRenderer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.wav.WavExtractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import coil3.ImageLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.di.ResourceProvider
import eu.depau.loak.domain.manager.AndroidScrobbleManager
import eu.depau.loak.domain.manager.AudioGainManager
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.EqualiserManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.settings.EqualiserMode
import eu.depau.loak.domain.models.settings.ReplayGainMode
import eu.depau.loak.domain.repositories.PlayerStateRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.exoplayer.AudioGainProcessor
import eu.depau.loak.exoplayer.ExoPlayerCoilBitmapLoader
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalCoroutinesApi::class)
class PlaybackService : MediaSessionService(), KoinComponent {
	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

	private var mediaSession: MediaSession? = null
	private val audioGainProcessor: AudioGainProcessor by inject()
	private val serviceScope = MainScope()
	private var scrobbleManager: AndroidScrobbleManager? = null
	private val resourceProvider: ResourceProvider by inject()

	private val connectivityManager: ConnectivityManager by inject()

	private val syncManager: SyncManager by inject()
	private val sessionManager: SessionManager by inject()
	private val preferenceManager: PreferenceManager by inject()
	private val equaliserManager: EqualiserManager by inject()
	private val imageLoader: ImageLoader by inject()
	private val songRepository: SongRepository by inject()
	private val currentSongId = MutableStateFlow<String?>(null)

	private var equaliser: Equalizer? = null
	private var audioEffectSessionId: Int = C.AUDIO_SESSION_ID_UNSET
	private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
	private var equaliserMode: EqualiserMode = EqualiserMode.Disabled

	override fun onCreate() {
		super.onCreate()
		val loadControl = DefaultLoadControl.Builder()
			.setBufferDurationsMs(
				/* minBufferMs = */ 32_000,
				/* maxBufferMs = */ 64_000,
				/* bufferForPlaybackMs = */ 2_500,
				/* bufferForPlaybackAfterRebufferMs = */ 5_000
			)
			.setBackBuffer(10_000, true)
			.build()

		val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
			.build().apply {
				setSmallIcon(resourceProvider.icLoak)
			}

		val httpDataSourceFactory = KtorDataSource.Factory(sessionManager.api.httpClient)
		val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

		val extractorsFactory = ExtractorsFactory {
			arrayOf(
				FlacExtractor(),
				WavExtractor(),
				FragmentedMp4Extractor(DefaultSubtitleParserFactory.UNSUPPORTED),
				Mp4Extractor(DefaultSubtitleParserFactory.UNSUPPORTED),
				OggExtractor(),
				MatroskaExtractor(DefaultSubtitleParserFactory.UNSUPPORTED),
				AdtsExtractor(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
				Mp3Extractor(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
			)
		}

		val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

		val audioRenderer = RenderersFactory { handler, _, audioListener, _, _ ->
			arrayOf<BaseRenderer>(
				MediaCodecAudioRenderer(
					applicationContext,
					MediaCodecSelector.DEFAULT,
					handler,
					audioListener,
					DefaultAudioSink.Builder(applicationContext)
						.setAudioProcessors(arrayOf(audioGainProcessor))
						.build()
				)
			)
		}

		val player = ExoPlayer.Builder(this, audioRenderer)
			.setLoadControl(loadControl)
			.setMediaSourceFactory(mediaSourceFactory)
			.setHandleAudioBecomingNoisy(true)
			.setWakeMode(C.WAKE_MODE_NETWORK)
			.build()
			.apply {
				setAudioAttributes(
					AudioAttributes.Builder()
						.setUsage(C.USAGE_MEDIA)
						.setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
						.build(),
					true
				)
				setMediaNotificationProvider(notificationProvider)
				trackSelectionParameters =
					trackSelectionParameters.buildUpon().setAudioOffloadPreferences(
						TrackSelectionParameters.AudioOffloadPreferences
							.Builder()
							.setIsGaplessSupportRequired(preferenceManager.gaplessPlayback)
							.setAudioOffloadMode(
								if (preferenceManager.audioOffload) {
									TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
								} else {
									TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
								}
							)
							.build()
					).build()
			}

		scrobbleManager =
			AndroidScrobbleManager(
				player,
				serviceScope,
				connectivityManager,
				syncManager,
				sessionManager,
				preferenceManager
			)

		val sessionIntent = applicationContext.packageManager
			.getLaunchIntentForPackage(applicationContext.packageName)
			?.apply {
				flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
					Intent.FLAG_ACTIVITY_CLEAR_TOP
			}

		val sessionPendingIntent = PendingIntent.getActivity(
			this,
			0,
			sessionIntent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
		)

		val bitmapLoader = ExoPlayerCoilBitmapLoader(applicationContext, imageLoader)

		mediaSession = MediaSession.Builder(this, player)
			.setSessionActivity(sessionPendingIntent)
			.setBitmapLoader(bitmapLoader)
			.setCallback(MediaSessionCallback(::toggleStar))
			.setSessionActivity(sessionPendingIntent)
			.build()

		currentAudioSessionId = player.audioSessionId
		equaliserMode = equaliserManager.config.value.mode
		applyEqualiserMode(equaliserMode, currentAudioSessionId)

		player.addListener(object : Player.Listener {
			override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
				currentSongId.value = mediaItem?.mediaId
			}

			override fun onAudioSessionIdChanged(audioSessionId: Int) {
				currentAudioSessionId = audioSessionId
				applyEqualiserMode(equaliserMode, audioSessionId)
			}
		})

		// the notification's only extra button stars the current song (radios get none)
		currentSongId.value = player.currentMediaItem?.mediaId
		serviceScope.launch {
			currentSongId
				.flatMapLatest { id ->
					if (id == null || id.startsWith("radio_")) {
						flowOf(null)
					} else {
						songRepository.observeSongStarred(id)
					}
				}
				.collect { starred ->
					mediaSession?.setCustomLayout(listOfNotNull(starred?.let(::makeStarButton)))
				}
		}

		scope.launch(Dispatchers.Main) {
			equaliserManager.config.collect { config ->
				if (config.mode != equaliserMode) {
					equaliserMode = config.mode
					applyEqualiserMode(equaliserMode, currentAudioSessionId)
				} else {
					updateEqualiser()
				}
			}
		}
	}

	override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
		return mediaSession
	}

	override fun onTaskRemoved(rootIntent: Intent?) {
		pauseAllPlayersAndStopSelf()
	}

	override fun onDestroy() {
		closeAudioEffectSession(audioEffectSessionId)
		releaseEqualiser()
		scrobbleManager?.release()
		serviceScope.cancel()
		stopForeground(STOP_FOREGROUND_REMOVE)
		mediaSession?.run {
			player.stop()
			player.release()
			release()
		}
		super.onDestroy()
		mediaSession = null
		stopSelf()
	}

	private fun toggleStar() {
		val id = currentSongId.value ?: return
		scope.launch {
			runCatching {
				songRepository.setSongStarred(id, !songRepository.observeSongStarred(id).first())
			}
		}
	}

	class MediaSessionCallback(private val onStar: () -> Unit) : MediaSession.Callback {
		override fun onConnect(
			session: MediaSession,
			controller: MediaSession.ControllerInfo
		): MediaSession.ConnectionResult {
			val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
				.buildUpon()
				.add(SessionCommand(COMMAND_STAR, Bundle.EMPTY))
				.build()

			return MediaSession.ConnectionResult.accept(
				sessionCommands,
				MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
			)
		}

		override fun onCustomCommand(
			session: MediaSession,
			controller: MediaSession.ControllerInfo,
			customCommand: SessionCommand,
			args: Bundle
		): ListenableFuture<SessionResult> {
			if (customCommand.customAction == COMMAND_STAR) onStar()

			return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
		}
	}

	// Applies the chosen equaliser mode
	private fun applyEqualiserMode(mode: EqualiserMode, sessionId: Int) {
		closeAudioEffectSession(audioEffectSessionId)
		releaseEqualiser()

		when (mode) {
			EqualiserMode.BuiltIn -> makeEqualiser(sessionId)
			EqualiserMode.External -> openAudioEffectSession(sessionId)
			EqualiserMode.Disabled -> Unit
		}
	}

	private fun releaseEqualiser() {
		equaliser?.release()
		equaliser = null
	}

	// Announces our audio session to the system so external equalizer apps can attach effects to it
	private fun openAudioEffectSession(sessionId: Int) {
		if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
		audioEffectSessionId = sessionId
		sendBroadcast(
			Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
				putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
				putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
				putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
			}
		)
	}

	// Tells external equalizer apps our audio session is going away so they can release their effects
	private fun closeAudioEffectSession(sessionId: Int) {
		if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
		sendBroadcast(
			Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
				putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
				putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
			}
		)
		audioEffectSessionId = C.AUDIO_SESSION_ID_UNSET
	}

	private fun makeEqualiser(sessionId: Int) {
		releaseEqualiser()
		try {
			val equaliser = Equalizer(0, sessionId).apply {
				enabled = true
			}

			this.equaliser = equaliser

			val bandLowerRange = equaliser.bandLevelRange.firstOrNull()?.toFloat() ?: -1500f
			val bandUpperRange = equaliser.bandLevelRange.lastOrNull()?.toFloat() ?: 1500f
			val bandCount = equaliser.numberOfBands.toInt()
			val bandFrequencies = List(bandCount) { equaliser.getCenterFreq(it.toShort()) / 1000 }

			scope.launch {
				equaliserManager.setConfig(
					equaliserManager.config.value.copy(
						bandLowerRange = bandLowerRange,
						bandUpperRange = bandUpperRange,
						bandCount = bandCount,
						bandFrequencies = bandFrequencies
					)
				)
			}

			updateEqualiser()
		} catch (ex: Exception) {
			Logger.e("PlaybackService", "error while configuring eq", ex)
		}
	}

	private fun updateEqualiser() {
		val equaliser = equaliser ?: return
		val config = equaliserManager.config.value
		try {
			// reset all band levels first in case an item in
			// config.bandLevels was removed (e.g. user presses
			// reset in the equaliser settings)
			repeat(equaliser.numberOfBands.toInt()) { band ->
				equaliser.setBandLevel(band.toShort(), 0)
			}
			config.bandLevels.forEach { (band, level) ->
				equaliser.setBandLevel(band.toShort(), level.toInt().toShort())
			}
		} catch (ex: Exception) {
			Logger.e("PlaybackService", "error while setting eq band levels", ex)
		}
	}

	companion object {
		const val COMMAND_STAR = "COMMAND_STAR"

		fun makeStarButton(starred: Boolean): CommandButton {
			val icon = if (starred) {
				CommandButton.ICON_STAR_FILLED
			} else {
				CommandButton.ICON_STAR_UNFILLED
			}
			return CommandButton.Builder(icon)
				.setDisplayName(if (starred) "Unstar" else "Star")
				.setSessionCommand(SessionCommand(COMMAND_STAR, Bundle.EMPTY))
				.build()
		}

		fun newSessionToken(context: Context): SessionToken {
			return SessionToken(context, ComponentName(context, PlaybackService::class.java))
		}
	}
}

@UnstableApi
class AndroidMediaPlayerViewModel(
	stateRepository: PlayerStateRepository,
	songRepository: SongRepository,
	downloadManager: DownloadManager,
	connectivityManager: ConnectivityManager,
	preferenceManager: PreferenceManager,
	queueSyncManager: QueueSyncManager,
	private val audioGainManager: AudioGainManager,
	private val application: Application,
	private val albumDao: AlbumDao,
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
	private var controller: MediaController? = null
	private var controllerFuture: ListenableFuture<MediaController>? = null

	private var loadingCollectionId: String? = null

	private var pendingSyncState: PlayerUiState? = null

	/** The running progress poller; at most one is ever active. */
	private var progressJob: Job? = null

	init {
		connectToService()
	}

	private fun connectToService() {
		viewModelScope.launch {
			val sessionToken = PlaybackService.newSessionToken(application)
			controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
			controllerFuture?.addListener({
				controller = controllerFuture?.get()
				setupController()
			}, MoreExecutors.directExecutor())
		}
	}

	private fun getStreamUrl(id: String): Uri {
		val isCellular = connectivityManager.isCellular.value
		val bitrate = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customMaxBitrateCellular else preferenceManager.customMaxBitrateWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.bitrateAndroid else preferenceManager.streamingQualityWifi.bitrateAndroid
		}
		val container = if (preferenceManager.isAdvancedTranscodingActive) {
			if (isCellular) preferenceManager.customFormatCellular else preferenceManager.customFormatWifi
		} else {
			if (isCellular) preferenceManager.streamingQualityCellular.containerAndroid else preferenceManager.streamingQualityWifi.containerAndroid
		}
		return sessionManager.api.getStreamUrl(id, bitrate, container?.takeIf { it.isNotBlank() })
			.toUri()
			.buildUpon()
			.appendQueryParameter("estimateContentLength", "true")
			.build()
	}

	private fun setupController() {
		viewModelScope.launch {
			controller?.apply {
				addListener(object : Player.Listener {
					override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
						updatePlaybackState()
						skipUnavailableSong()
					}

					override fun onIsPlayingChanged(isPlaying: Boolean) {
						if (isPlaying) startProgressLoop()

						val currentSong = _uiState.value.currentSong
						val displayArtist = currentSong?.artists?.joinToString { it.name }
							?.ifBlank { currentSong.artistName }

						val intent =
							Intent("${application.packageName}.NOW_PLAYING_UPDATED").apply {
								setPackage(application.packageName)
								putExtra("isPlaying", isPlaying)
								putExtra(
									"title",
									currentSong?.title ?: "Unknown song"
								)
								putExtra(
									"artist",
									displayArtist ?: "Unknown artist"
								)
								putExtra(
									"artUrl",
									currentSong?.coverArtId?.let {
										sessionManager.getCoverArtUrl(it)
									})
							}

						application.sendBroadcast(intent)
						updatePlaybackState()
					}

					override fun onPlaybackStateChanged(playbackState: Int) {
						_uiState.update { it.copy(isLoading = playbackState == Player.STATE_BUFFERING) }
						updatePlaybackState()
					}

					override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
						_uiState.update { it.copy(isShuffleEnabled = shuffleModeEnabled) }
					}

					override fun onRepeatModeChanged(repeatMode: Int) {
						_uiState.update { it.copy(repeatMode = repeatMode) }
					}

					override fun onTracksChanged(tracks: Tracks) {
						updatePlaybackProperties(tracks)
					}

					override fun onTimelineChanged(timeline: Timeline, reason: Int) {
						updatePlaybackState()
					}
				})
				updatePlaybackState()
				updatePlaybackProperties(currentTracks)

				downloadManager.allDownloads.first()
				pendingSyncState?.let { state ->
					syncPlayerWithState(state)
					pendingSyncState = null
				}
			}
		}
	}

	/**
	 * strategically skip around in the queue until the
	 * current song is available while avoiding infinite
	 * loops
	 *
	 * this **INTENTIONALLY** does not check for if the song
	 * is not downloaded and if the device is offline
	 *
	 * this used to check for that but because there have
	 * been cases where the device is falsely identified
	 * as being offline that's no longer the case, so we
	 * just try to play the song anyway
	 */
	private fun skipUnavailableSong() {
		val currentSong = _uiState.value.currentSong ?: return
		if (!isExplicit(currentSong)) return
		Logger.i("MediaPlayer", "trying to skip unavailable song")
		val queue = _uiState.value.queue
		val currentIdx = queue.indexOf(currentSong)

		// look for the next available song, wrapping around, but stop before
		// we loop back past our own starting point
		val nextAvailableIdx = (1..queue.size)
			.map { offset -> (currentIdx + offset) % queue.size }
			.firstOrNull { index -> !isExplicit(queue[index]) }

		if (nextAvailableIdx == null) {
			Logger.i(
				"MediaPlayer",
				"pausing because this song is unavailable and there isn't anything to skip to"
			)
			controller?.pause()
			return
		}

		if (nextAvailableIdx <= currentIdx) {
			Logger.i(
				"MediaPlayer",
				"skipping and pausing because the last song in the queue was unavailable"
			)
			controller?.seekTo(nextAvailableIdx, 0L)
			controller?.pause()
		} else {
			// just skip to the next song
			controller?.seekTo(nextAvailableIdx, 0L)
		}
	}

	private fun refreshCurrentCollection(albumId: String) {
		if (loadingCollectionId == albumId) return
		loadingCollectionId = albumId

		viewModelScope.launch {
			runCatching {
				val album = albumDao.getAlbumById(albumId)

				_uiState.update { it.copy(currentCollection = album?.toDomainModel()) }
			}.onFailure {
				loadingCollectionId = null
			}
		}
	}

	private fun updatePlaybackState() {
		val controller = controller ?: return
		val index = controller.currentMediaItemIndex
		if (index == C.INDEX_UNSET) return

		val currentSong = _uiState.value.queue.getOrNull(index)

		val derivedCollection = currentSong?.let { song ->
			val stateCollection = _uiState.value.currentCollection

			if (stateCollection?.id == song.albumId.toString()) {
				stateCollection
			} else {
				refreshCurrentCollection(song.albumId.toString())
				null
			}
		}

		_uiState.update { state ->
			state.copy(
				currentIndex = index,
				currentSong = currentSong,
				currentCollection = derivedCollection ?: state.currentCollection,
				isPaused = !controller.playWhenReady,
				isShuffleEnabled = controller.shuffleModeEnabled,
				repeatMode = controller.repeatMode
			)
		}
		applyAudioGain()
		updateProgress()
	}

	private fun applyAudioGain() {
		audioGainManager.setAmplifierValues(preferenceManager.rgAmpGain, preferenceManager.ampGain)

		if (preferenceManager.replayGainMode != ReplayGainMode.Off) {
			val currentSong = _uiState.value.currentSong
			val replayGain = currentSong?.replayGain

			if (replayGain != null) {
				audioGainManager.setReplayGainMetadata(replayGain)

				if (preferenceManager.replayGainMode != ReplayGainMode.Dynamic) {
					audioGainManager.applyGainMode(preferenceManager.replayGainMode)
				} else {
					if (_uiState.value.queue.all { it.albumId == currentSong.albumId }) {
						audioGainManager.applyGainMode(ReplayGainMode.Album)
					} else {
						audioGainManager.applyGainMode(ReplayGainMode.Track)
					}
				}
			} else {
				audioGainManager.setReplayGainMetadata(null)
			}
		} else {
			audioGainManager.resetGain()
		}
	}

	override fun syncPlayerWithState(state: PlayerUiState) {
		viewModelScope.launch {
			val player = controller

			if (player == null) {
				pendingSyncState = state
				return@launch
			}

			if (state.queue.isEmpty() || player.mediaItemCount > 0) {
				updatePlaybackState()
				return@launch
			}

			val mediaItems = withContext(Dispatchers.Default) {
				state.queue.map { it.toMediaItem() }
			}

			player.setMediaItems(mediaItems)

			player.shuffleModeEnabled = state.isShuffleEnabled
			player.repeatMode = state.repeatMode
			player.playbackParameters = PlaybackParameters(state.playbackSpeed)

			val index = if (state.currentIndex in mediaItems.indices) state.currentIndex else 0

			val songDurationMs = state.queue.getOrNull(index)?.duration?.inWholeMilliseconds ?: 0L

			val position = if (songDurationMs > 0) {
				(state.progress * songDurationMs).toLong()
			} else {
				0L
			}

			player.seekTo(index, position)
			player.prepare()
			if (!state.isPaused) {
				player.play()
			}
		}
	}

	private fun startProgressLoop() {
		// re-buffering or a play/pause race can fire onIsPlayingChanged(true)
		// again while a poller is already running; only ever keep one
		if (progressJob?.isActive == true) return
		progressJob = viewModelScope.launch {
			while (controller?.isPlaying == true) {
				val player = controller ?: break
				val duration = player.duration
				if (duration > 0) {
					val progress =
						(player.currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
					_uiState.update { it.copy(progress = progress) }
				}
				delay(200.milliseconds)
			}
		}
	}

	private fun updateProgress() {
		controller?.let { player ->
			val duration = player.duration
			if (duration > 0) {
				val pos = player.currentPosition
				val progress = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
				_uiState.update { it.copy(progress = progress) }
			}
		}
	}

	@OptIn(UnstableApi::class)
	private fun updatePlaybackProperties(tracks: Tracks) {
		val audioGroup =
			tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
		if (audioGroup != null) {
			for (i in 0 until audioGroup.length) {
				if (audioGroup.isTrackSelected(i)) {
					val format = audioGroup.getTrackFormat(i)
					Logger.i("MediaPlayer", "Active Track Format: $format")
					_uiState.update { state ->
						state.copy(
							playbackBitrate = format.bitrate.takeIf { it > 0 },
							playbackSampleRate = format.sampleRate.takeIf { it > 0 },
							playbackMimeType = format.sampleMimeType
						)
					}
					break
				}
			}
		}
	}




	override fun insertIntoQueue(index: Int, songs: List<DomainSong>) {
		viewModelScope.launch {
			controller?.addMediaItems(index, songs.map { it.toMediaItem() })
			_uiState.update { it.withInserted(index, songs) }
		}
	}

	override fun removeFromQueue(index: Int) {
		viewModelScope.launch {
			controller?.removeMediaItem(index)
			_uiState.update { it.withRemoved(index) }
		}
	}

	override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
		viewModelScope.launch {
			controller?.moveMediaItem(fromIndex, toIndex)
			_uiState.update { it.withMoved(fromIndex, toIndex) }
		}
	}

	override fun clearQueue() {
		viewModelScope.launch {
			_uiState.update {
				it.copy(
					queue = emptyList(),
					currentSong = null,
					currentIndex = -1,
					progress = 0f
				)
			}
			controller?.clearMediaItems()
		}
	}

	override fun playAt(index: Int) {
		viewModelScope.launch {
			controller?.let { player ->
				if (index in 0 until player.mediaItemCount) {
					player.seekTo(index, 0L)
					player.play()
				}
			}
		}
	}



	override fun playRadio(radio: DomainRadio) {
		viewModelScope.launch {
			val radioId = "radio_${radio.name.hashCode()}"

			val dummyRadioSong = DomainSong(
				id = radioId,
				title = radio.name,
				artistName = "Live Radio",
				albumId = "radio_album",
				albumTitle = "Live Stream",
				duration = Duration.ZERO,
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

			val metadata = MediaMetadata.Builder()
				.setTitle(radio.name)
				.setArtist("Live Radio")
				.setIsPlayable(true)
				.build()

			val mediaItem = MediaItem.Builder()
				.setUri(radio.streamUrl)
				.setMediaId("radio_${radio.name.hashCode()}")
				.setMediaMetadata(metadata)
				.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
				.build()

			controller?.let { player ->
				player.stop()
				player.clearMediaItems()
				player.setMediaItem(mediaItem)
				player.prepare()
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
		}
	}

	override fun shufflePlay(collection: DomainSongCollection) {
		playLog.recordCollection(collection)
		viewModelScope.launch {
			val (shuffledSongs, mediaItems) = withContext(Dispatchers.Default) {
				val songs = collection.songs.shuffled()
				songs to songs.map { it.toMediaItem() }
			}

			controller?.let { player ->
				player.shuffleModeEnabled = false
				player.setMediaItems(mediaItems, 0, 0L)
				player.prepare()
				player.play()
			}

			_uiState.update { state ->
				state.copy(
					queue = shuffledSongs,
					currentIndex = 0,
					currentSong = shuffledSongs.firstOrNull()
				)
			}
		}
	}

	override fun pause() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			controller?.pause()
		}
	}

	override fun resume() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			controller?.play()
		}
	}

	override fun next() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			if (controller?.hasNextMediaItem() == true) controller?.seekToNextMediaItem()
		}
	}

	override fun previous() {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			val controller = controller ?: return@launch
			if (controller.hasPreviousMediaItem() && controller.currentPosition <= 1000) {
				controller.seekToPreviousMediaItem()
			} else {
				controller.seekTo(0)
			}
		}
	}

	override fun toggleShuffle() {
		viewModelScope.launch {
			controller?.let { player ->
				player.shuffleModeEnabled = !player.shuffleModeEnabled
			}
		}
	}

	override fun toggleRepeat() {
		viewModelScope.launch {
			controller?.let { player ->
				player.repeatMode = when (player.repeatMode) {
					Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
					Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
					else -> Player.REPEAT_MODE_OFF
				}
			}
		}
	}

	override fun seek(normalized: Float) {
		viewModelScope.launch(Dispatchers.Main.immediate) {
			controller?.let {
				val target = (it.duration * normalized).toLong()
				it.seekTo(target)
				_uiState.update { state ->
					state.copy(progress = normalized)
				}
			}
		}
	}

	override fun onCleared() {
		viewModelScope.launch {
			super.onCleared()
			controllerFuture?.let { MediaController.releaseFuture(it) }
		}
	}

	override fun setPlaybackSpeed(value: Float) {
		viewModelScope.launch {
			controller?.setPlaybackSpeed(value)
		}
		_uiState.update { it.copy(playbackSpeed = value) }
	}

	private fun DomainSong.toMediaItem(): MediaItem {
		val displayArtist = artists.joinToString { it.name }.ifBlank { artistName }
		val albumArtistName = albumArtists.joinToString { it.name }.ifBlank { artistName }

		val metadataBuilder = MediaMetadata.Builder()
			.setTitle(title)
			.setSubtitle(displayArtist)
			.setArtist(displayArtist)
			.setAlbumArtist(albumArtistName)
			.setAlbumTitle(albumTitle)
			.setDurationMs(duration.inWholeMilliseconds)
			.setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)

		metadataBuilder.setArtworkUri(
			coverArtId?.let { sessionManager.getCoverArtUrl(it).toUri() }
		)

		val metadata = metadataBuilder.build()

		val uri = when {
			id.startsWith("radio_") && !filePath.isNullOrEmpty() -> {
				filePath.toUri()
			}

			else -> {
				val localPath = downloadManager.getDownloadedFilePath(id)
				if (localPath != null) {
					File(localPath).toUri()
				} else {
					getStreamUrl(id)
				}
			}
		}

		val builder = MediaItem.Builder()
			.setUri(uri)
			.setMediaId(id)
			.setMediaMetadata(metadata)

		if (id.startsWith("radio_")) {
			builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
		}

		return builder.build()
	}
}
