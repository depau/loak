package eu.depau.loak.shared

import eu.depau.loak.di.COVER_ART_MEDIUM
import android.app.Application
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import eu.depau.loak.domain.models.formatSampleRate
import eu.depau.loak.util.effectiveGain
import kotlin.math.roundToInt
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
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
import androidx.media3.session.MediaButtonReceiver
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.entities.TransferCategory
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.di.ResourceProvider
import eu.depau.loak.domain.manager.AndroidScrobbleManager
import eu.depau.loak.domain.manager.AudioFetcher
import eu.depau.loak.domain.manager.AudioGainManager
import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.EqualiserManager
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.manager.prefetchTargets
import eu.depau.loak.domain.models.AudioQuality
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.PlaybackDetails
import eu.depau.loak.domain.models.formatCodecName
import eu.depau.loak.domain.models.settings.EqualiserMode
import eu.depau.loak.domain.models.settings.ReplayGainMode
import eu.depau.loak.domain.repositories.PlayerStateRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.exoplayer.AudioGainProcessor
import eu.depau.loak.exoplayer.ExoPlayerCoilBitmapLoader
import eu.depau.loak.exoplayer.StoreDataSource
import eu.depau.loak.exoplayer.fetchSong
import eu.depau.loak.exoplayer.songId
import eu.depau.loak.exoplayer.songUri
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


internal data class AndroidAudioSinkState(
	val songId: String? = null,
	val decoder: String? = null,
	val pcmFormat: String? = null,
	val outputFormat: String? = null,
	val isOffloaded: Boolean = false
)

internal object AndroidAudioSinkTracker {
	val state = MutableStateFlow(AndroidAudioSinkState())
}

internal fun currentAudioOutputDevice(context: Context): String? {
	val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
	val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
	val preferred = devices.firstOrNull {
		it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
			it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
			(android.os.Build.VERSION.SDK_INT >= 31 && (it.type == 26 || it.type == 27))
	} ?: devices.firstOrNull {
		it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
			it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
			it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
			it.type == AudioDeviceInfo.TYPE_USB_DEVICE
	} ?: devices.firstOrNull {
		it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
	} ?: devices.firstOrNull()

	return preferred?.productName?.toString()?.ifBlank { null }
		?: when {
			preferred == null -> null
			preferred.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
				preferred.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
				(android.os.Build.VERSION.SDK_INT >= 31 && (preferred.type == 26 || preferred.type == 27)) -> "Bluetooth audio"
			preferred.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || preferred.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Headphones"
			preferred.type == AudioDeviceInfo.TYPE_USB_HEADSET || preferred.type == AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
			preferred.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
			else -> null
		}
}
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
	private val stateRepository: PlayerStateRepository by inject()
	private val networkStatsManager: NetworkStatsManager by inject()
	private val audioStore: AudioStore by inject()
	private val audioFetcher: AudioFetcher by inject()
	private val currentSongId = MutableStateFlow<String?>(null)
	private val networkReads = MutableStateFlow(0)
	private var prefetchJob: Job? = null

	/** Current and prefetched songs, protected from eviction. */
	private var retained = emptySet<String>()

	private var equaliser: Equalizer? = null
	private var audioEffectSessionId: Int = C.AUDIO_SESSION_ID_UNSET
	private var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
	private var equaliserMode: EqualiserMode = EqualiserMode.Disabled

	override fun onCreate() {
		super.onCreate()
		// songs mostly play from disk (see StoreDataSource); what still streams (radio, songs
		// with the cache off) refills in rare long bursts rather than a trickle
		val loadControl = DefaultLoadControl.Builder()
			.setBufferDurationsMs(
				/* minBufferMs = */ 120_000,
				/* maxBufferMs = */ 600_000,
				/* bufferForPlaybackMs = */ 2_500,
				/* bufferForPlaybackAfterRebufferMs = */ 5_000
			)
			.setTargetBufferBytes(64 * 1024 * 1024)
			.setPrioritizeTimeOverSizeThresholds(false)
			.setBackBuffer(10_000, true)
			.build()

		val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
			.build().apply {
				setSmallIcon(resourceProvider.icLoak)
			}

		val httpDataSourceFactory = KtorDataSource.Factory(sessionManager.api.httpClient)
		val upstreamFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
			.setTransferListener(StreamStatsListener(networkStatsManager))
		val dataSourceFactory = DataSource.Factory {
			StoreDataSource(
				upstreamFactory.createDataSource(),
				audioStore,
				audioFetcher,
				networkStatsManager,
				connectivityManager,
				::streamSource,
				networkReads
			)
		}

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

		// skipping is a request to hear the other song: the notification, headsets, the car and
		// the app's own buttons all skip through here, and none should stay paused
		val sessionPlayer = object : ForwardingPlayer(player) {
			override fun seekToNext() = super.seekToNext().also { play() }
			override fun seekToNextMediaItem() = super.seekToNextMediaItem().also { play() }
			override fun seekToPrevious() = super.seekToPrevious().also { play() }
			override fun seekToPreviousMediaItem() =
				super.seekToPreviousMediaItem().also { play() }
		}

		mediaSession = MediaSession.Builder(this, sessionPlayer)
			.setSessionActivity(sessionPendingIntent)
			.setBitmapLoader(bitmapLoader)
			.setCallback(MediaSessionCallback(::toggleStar, ::savedQueue))
			.setSessionActivity(sessionPendingIntent)
			.build()

		currentAudioSessionId = player.audioSessionId
		equaliserMode = equaliserManager.config.value.mode
		applyEqualiserMode(equaliserMode, currentAudioSessionId)

		player.addListener(object : Player.Listener {
			override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
				currentSongId.value = mediaItem?.mediaId
				AndroidAudioSinkTracker.state.value = AndroidAudioSinkState(songId = mediaItem?.mediaId)
				schedulePrefetch(player)
			}

			override fun onTimelineChanged(timeline: Timeline, reason: Int) =
				schedulePrefetch(player)

			override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) =
				schedulePrefetch(player)

			override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) =
				schedulePrefetch(player)

			override fun onRepeatModeChanged(repeatMode: Int) = schedulePrefetch(player)

			override fun onAudioSessionIdChanged(audioSessionId: Int) {
				currentAudioSessionId = audioSessionId
				applyEqualiserMode(equaliserMode, audioSessionId)
			}
		})

		player.addAnalyticsListener(object : AnalyticsListener {
			override fun onAudioDecoderInitialized(
				eventTime: AnalyticsListener.EventTime,
				decoderName: String,
				initializedTimestampMs: Long,
				initializationDurationMs: Long
			) {
				AndroidAudioSinkTracker.state.update {
					it.copy(songId = currentSongId.value, decoder = decoderName)
				}
			}

			override fun onAudioTrackInitialized(
				eventTime: AnalyticsListener.EventTime,
				audioTrackConfig: AudioSink.AudioTrackConfig
			) {
				val pcm = when (audioTrackConfig.encoding) {
					AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float PCM"
					else -> "16-bit PCM"
				}
				val channels = if (audioTrackConfig.channelConfig == AudioFormat.CHANNEL_OUT_MONO) "Mono" else "Stereo"
				val outFmt = "${formatSampleRate(audioTrackConfig.sampleRate)} · $channels"
				AndroidAudioSinkTracker.state.update {
					it.copy(
						songId = currentSongId.value,
						pcmFormat = pcm,
						outputFormat = outFmt,
						isOffloaded = audioTrackConfig.offload
					)
				}
			}
		})

		// a Wi-Fi lock only while the network is used; files need just the CPU
		serviceScope.launch {
			combine(audioFetcher.busy, networkReads) { fetching, reads -> fetching || reads > 0 }
				.distinctUntilChanged()
				.collect { player.setWakeMode(if (it) C.WAKE_MODE_NETWORK else C.WAKE_MODE_LOCAL) }
		}

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
				// Room re-emits on every write to the songs table (syncs, album views): each new
				// layout re-posts the notification, so only send real changes
				.distinctUntilChanged()
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
		audioFetcher.cancelExcept(emptySet())
		retained.forEach(audioStore::release)
		retained = emptySet()
		super.onDestroy()
		mediaSession = null
		stopSelf()
	}

	private fun streamSource(songId: String) =
		streamSource(songId, sessionManager, connectivityManager, preferenceManager)

	/**
	 * Fetches the songs after the current one into the store, one at a time once nothing else is
	 * fetching (the current song first), so the next track plays from disk without waking the
	 * radio again. Redone on every queue or track change, which also stops stale fetches.
	 */
	private fun schedulePrefetch(player: Player) {
		val upcoming = mutableListOf<Uri?>()
		val timeline = player.currentTimeline
		if (!timeline.isEmpty) {
			// repeating one song still moves on when skipped
			val mode = player.repeatMode.takeIf { it != Player.REPEAT_MODE_ONE }
				?: Player.REPEAT_MODE_OFF
			var i = player.currentMediaItemIndex
			while (upcoming.size < 3) {
				i = timeline.getNextWindowIndex(i, mode, player.shuffleModeEnabled)
				if (i == C.INDEX_UNSET) break
				upcoming += player.getMediaItemAt(i).localConfiguration?.uri
			}
		}
		val current = player.currentMediaItem?.localConfiguration?.uri?.songId
		val targets = prefetchTargets(
			current, upcoming.map { it?.songId }, connectivityManager.isCellular.value
		)
		val keep = setOfNotNull(current) + targets
		(keep - retained).forEach(audioStore::retain)
		(retained - keep).forEach(audioStore::release)
		retained = keep
		audioFetcher.cancelExcept(keep)

		prefetchJob?.cancel()
		if (!player.playWhenReady) return
		prefetchJob = serviceScope.launch {
			audioFetcher.busy.first { !it }
			for (uri in upcoming.filterNotNull().filter { it.songId in targets }) {
				if (!connectivityManager.isOnline.value) break
				val id = uri.songId!!
				val wanted = streamSource(id).second
				if (audioStore.playable(id, wanted, online = true) != null) continue
				audioFetcher.fetchSong(uri, ::streamSource)
					?.progress?.first { it.done || it.failed }
			}
		}
	}

	private fun toggleStar() {
		val id = currentSongId.value ?: return
		scope.launch {
			runCatching {
				songRepository.setSongStarred(id, !songRepository.observeSongStarred(id).first())
			}
		}
	}

	/** The queue the app last saved, for a media button press that finds the player empty. */
	private fun savedQueue(): MediaSession.MediaItemsWithStartPosition? {
		val state = stateRepository.state.value?.takeIf { it.queue.isNotEmpty() } ?: return null
		mediaSession?.player?.apply {
			shuffleModeEnabled = state.isShuffleEnabled
			repeatMode = state.repeatMode
		}
		val index = state.currentIndex.coerceIn(state.queue.indices)
		return MediaSession.MediaItemsWithStartPosition(
			state.queue.map {
				it.toMediaItem(sessionManager)
			},
			index,
			(state.progress * state.queue[index].duration.inWholeMilliseconds).toLong()
		)
	}

	/**
	 * Starts the service for a media button only if there's a saved queue to resume: Media3 starts
	 * it in the foreground, and with nothing to play no notification ever comes and Android
	 * kills the app with ForegroundServiceDidNotStartInTimeException.
	 */
	class ButtonReceiver : MediaButtonReceiver(), KoinComponent {
		private val stateRepository: PlayerStateRepository by inject()

		override fun shouldStartForegroundService(context: Context, intent: Intent) =
			stateRepository.state.value?.queue.isNullOrEmpty().not()
	}

	class MediaSessionCallback(
		private val onStar: () -> Unit,
		private val savedQueue: () -> MediaSession.MediaItemsWithStartPosition?
	) : MediaSession.Callback {
		override fun onPlaybackResumption(
			mediaSession: MediaSession,
			controller: MediaSession.ControllerInfo,
			isForPlayback: Boolean
		): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
			savedQueue()?.let { Futures.immediateFuture(it) }
				?: Futures.immediateFailedFuture(UnsupportedOperationException("no saved queue"))

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
	private val equaliserManager: EqualiserManager by inject()

	private var loadingCollectionId: String? = null

	private var pendingSyncState: PlayerUiState? = null

	/** The running progress poller; at most one is ever active. */
	private var progressJob: Job? = null

	init {
		connectToService()
		viewModelScope.launch {
			uiVisible.collect { visible ->
				if (!visible) return@collect
				updateProgress()
				if (controller?.isPlaying == true) startProgressLoop()
			}
		}
		viewModelScope.launch {
			AndroidAudioSinkTracker.state.collect { sink ->
				enrichPlaybackDetails(sink)
			}
		}
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

	private fun setupController() {
		viewModelScope.launch {
			controller?.apply {
				addListener(object : Player.Listener {
					override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
						updatePlaybackState()
						skipUnavailableSong()
					}

					// offline, a song with no stored copy fails at once: move on to one that has
					override fun onPlayerError(error: PlaybackException) {
						val song = _uiState.value.currentSong ?: return
						if (!isUnavailable(song)) return
						skipUnavailableSong()
						// paused: nothing left to play
						if (controller?.playWhenReady == true) controller?.prepare()
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
										sessionManager.getCoverArtUrl(it, COVER_ART_MEDIUM)
									})
							}

						application.sendBroadcast(intent)
						updatePlaybackState()
					}

					// play pressed while buffering doesn't change isPlaying: show it right away
					override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
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

				pendingSyncState?.let { state ->
					syncPlayerWithState(state)
					pendingSyncState = null
				}
			}
		}
	}

	/** Explicit songs being skipped, and offline, songs with no stored copy (see [canPlay]). */
	private fun isUnavailable(song: DomainSong) = isExplicit(song) || !canPlay(song)

	/**
	 * strategically skip around in the queue until the
	 * current song is available while avoiding infinite
	 * loops
	 */
	private fun skipUnavailableSong() {
		val currentSong = _uiState.value.currentSong ?: return
		if (!isUnavailable(currentSong)) return
		Logger.i("MediaPlayer", "trying to skip unavailable song")
		val queue = _uiState.value.queue
		val currentIdx = queue.indexOf(currentSong)

		// look for the next available song, wrapping around, but stop before
		// we loop back past our own starting point
		val nextAvailableIdx = (1..queue.size)
			.map { offset -> (currentIdx + offset) % queue.size }
			.firstOrNull { index -> !isUnavailable(queue[index]) }

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
		// first, so the state saved for this pause or track change has its position
		updateProgress()

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
		enrichPlaybackDetails()
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
		// again while a poller is already running; only ever keep one.
		// Off screen nothing shows the position: events (pause, track change) update it instead
		if (progressJob?.isActive == true || !uiVisible.value) return
		progressJob = viewModelScope.launch {
			while (controller?.isPlaying == true && uiVisible.value) {
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
			val pos = player.currentPosition
			val progress = when {
				duration > 0 -> (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
				// a new track whose length isn't known yet: not the last one's position
				pos == 0L -> 0f
				else -> return
			}
			_uiState.update { it.copy(progress = progress) }
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
					val songId = _uiState.value.currentSong?.id ?: return
					_uiState.update { state ->
						val current = state.playbackDetails?.takeIf { it.songId == songId }
							?: PlaybackDetails(songId = songId)
						state.copy(
							playbackDetails = current.copy(
								codec = formatCodecName(format.sampleMimeType, format.containerMimeType) ?: current.codec,
								bitrateKbps = format.bitrate.takeIf { it > 0 }?.let { it / 1000 } ?: current.bitrateKbps,
								sampleRateHz = format.sampleRate.takeIf { it > 0 } ?: current.sampleRateHz,
								channelCount = format.channelCount.takeIf { it > 0 } ?: current.channelCount
							)
						)
					}
					break
				}
			}
		}
		enrichPlaybackDetails()
	}

	override fun requestedQuality(): AudioQuality {
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
		return AudioQuality.of(container, bitrate)
	}

	private fun computeReplayGainDescription(): String? {
		if (preferenceManager.replayGainMode == ReplayGainMode.Off) return null
		val currentSong = _uiState.value.currentSong ?: return null
		val rg = currentSong.replayGain ?: return null
		val mode = if (preferenceManager.replayGainMode == ReplayGainMode.Dynamic) {
			if (_uiState.value.queue.all { it.albumId == currentSong.albumId }) ReplayGainMode.Album else ReplayGainMode.Track
		} else {
			preferenceManager.replayGainMode
		}
		val gain = rg.effectiveGain(mode)
		val sign = if (gain > 0) "+" else ""
		val rounded = (gain * 10).roundToInt() / 10.0
		return "${mode.name} $sign$rounded dB"
	}

	private fun enrichPlaybackDetails(sink: AndroidAudioSinkState = AndroidAudioSinkTracker.state.value) {
		val song = _uiState.value.currentSong ?: return
		val songId = song.id
		if (sink.songId != null && sink.songId != songId) return
		_uiState.update { state ->
			val current = state.playbackDetails?.takeIf { it.songId == songId } ?: return@update state
			val rgDesc = computeReplayGainDescription()
			val eqDesc = when (equaliserManager.config.value.mode) {
				EqualiserMode.BuiltIn -> "Built-in"
				EqualiserMode.External -> "External session"
				EqualiserMode.Disabled -> null
			}
			val outDevice = currentAudioOutputDevice(application)
			state.copy(
				playbackDetails = current.copy(
					decoder = sink.decoder ?: current.decoder,
					pcmFormat = sink.pcmFormat ?: current.pcmFormat,
					outputFormat = sink.outputFormat ?: current.outputFormat,
					isOffloaded = sink.isOffloaded,
					outputDevice = outDevice ?: current.outputDevice,
					replayGain = rgDesc,
					equalizer = eqDesc
				)
			)
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
		val playable = playable(collection.songs).ifEmpty { return }
		playLog.recordCollection(collection)
		viewModelScope.launch {
			val (shuffledSongs, mediaItems) = withContext(Dispatchers.Default) {
				val songs = playable.shuffled()
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
				controller.play()
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

	private fun DomainSong.toMediaItem() = toMediaItem(sessionManager)
}

/**
 * Counts streamed bytes as the player reads them, so nothing is counted ahead of playback.
 * The Ktor stats plugin leaves stream requests to this.
 */
@OptIn(UnstableApi::class)
private class StreamStatsListener(private val stats: NetworkStatsManager) : TransferListener {
	private val transfers = ConcurrentHashMap<DataSource, NetworkStatsManager.AudioTransfer>()

	override fun onTransferInitializing(source: DataSource, spec: DataSpec, isNetwork: Boolean) {}

	override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
		if (!isNetwork) return
		transfers[source] =
			stats.AudioTransfer(TransferCategory.STREAM, dataSpec.uri.getQueryParameter("id"))
	}

	override fun onBytesTransferred(
		source: DataSource,
		dataSpec: DataSpec,
		isNetwork: Boolean,
		bytesTransferred: Int
	) {
		if (isNetwork) transfers[source]?.add(bytesTransferred.toLong())
	}

	override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
		transfers.remove(source)?.end()
	}
}

private fun DomainSong.toMediaItem(sessionManager: SessionManager): MediaItem {
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
		coverArtId?.let { sessionManager.getCoverArtUrl(it, COVER_ART_MEDIUM).toUri() }
	)

	val metadata = metadataBuilder.build()

	val uri = when {
		id.startsWith("radio_") && !filePath.isNullOrEmpty() -> {
			filePath.toUri()
		}

		// resolved when played: from the store (downloads too), or streamed at the then
		// network's quality
		else -> songUri(id, fileExtension)
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

/** The URL to stream song [id] from, and the [AudioQuality] it comes at. */
private fun streamSource(
	id: String,
	sessionManager: SessionManager,
	connectivityManager: ConnectivityManager,
	preferenceManager: PreferenceManager,
): Pair<Uri, AudioQuality> {
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
	val format = container?.takeIf { it.isNotBlank() }
	val url = sessionManager.api.getStreamUrl(id, bitrate, format, estimateContentLength = true)
	return url.toUri() to AudioQuality.of(format, bitrate)
}
