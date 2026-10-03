package eu.depau.loak.domain.manager

import dev.nucleusframework.media.control.MediaControlEvent
import dev.nucleusframework.media.control.MediaControlService
import dev.nucleusframework.media.control.MediaMetadata
import dev.nucleusframework.media.control.MediaPlaybackState
import dev.nucleusframework.media.control.MediaPlaybackStatus
import eu.depau.loak.ui.core.PlayerUiState
import eu.depau.loak.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Publishes playback to the OS media controls through Nucleus: MPRIS on Linux, System Media
 * Transport Controls on Windows, Now Playing on macOS. That's what the desktop's media keys,
 * shell media widgets and headset buttons talk to, so they control Lo'ak even unfocused.
 *
 * Without a native backend (unsupported OS, no D-Bus session) every call is a no-op.
 */
class MediaControlManager(
	private val sessionManager: SessionManager
) {
	private var scope: CoroutineScope? = null

	private var lastStatus: MediaPlaybackStatus? = null
	private var lastSongId: String? = null
	private var lastPositionMs = 0L
	private var lastPublishedAt = 0L

	/**
	 * Mirrors [state] (and [volume], if the app owns one) to the OS and forwards its commands
	 * to [onEvent], on the Swing EDT.
	 */
	fun start(
		state: StateFlow<PlayerUiState>,
		volume: StateFlow<Float>?,
		onEvent: (MediaControlEvent) -> Unit,
	) {
		if (!MediaControlService.isAvailable()) {
			Logger.i("MediaControlManager", "no native media control backend; OS media keys disabled")
			return
		}
		MediaControlService.configure(dbusName = "org.mpris.MediaPlayer2.loak", displayName = "Lo'ak")
		MediaControlService.attach(onEvent)
		Logger.i("MediaControlManager", "OS media control service attached")

		val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
		scope.launch { state.collect(::publish) }
		if (volume != null) {
			scope.launch { volume.collect { MediaControlService.setVolume(it.toDouble()) } }
		}
	}

	fun stop() {
		scope?.cancel()
		scope = null
		MediaControlService.detach()
	}

	private fun publish(state: PlayerUiState) {
		val song = state.currentSong
		if (song?.id != lastSongId) {
			lastSongId = song?.id
			MediaControlService.setMetadata(
				if (song == null) MediaMetadata()
				else MediaMetadata(
					title = song.title,
					artist = song.artistName,
					album = song.albumTitle,
					coverUrl = song.coverArtId?.let(sessionManager::getCoverArtUrl),
					duration = song.duration.inWholeMilliseconds.takeIf { it > 0 },
				)
			)
			lastStatus = null // a new track always re-sends its position
		}

		val status = when {
			song == null -> MediaPlaybackStatus.STOPPED
			state.isPaused -> MediaPlaybackStatus.PAUSED
			else -> MediaPlaybackStatus.PLAYING
		}
		val positionMs = song?.let { (state.progress * it.duration.inWholeMilliseconds).toLong() } ?: 0L
		val now = System.currentTimeMillis()
		// clients extrapolate the position while playing, and the backend announces every
		// position it gets as a seek: send it on status changes and jumps only
		val expectedMs = if (lastStatus == MediaPlaybackStatus.PLAYING) {
			lastPositionMs + (now - lastPublishedAt)
		} else {
			lastPositionMs
		}
		if (status == lastStatus && abs(positionMs - expectedMs) < POSITION_TOLERANCE_MS) return

		lastStatus = status
		lastPositionMs = positionMs
		lastPublishedAt = now
		MediaControlService.setPlaybackState(MediaPlaybackState(status, positionMs))
	}

	private companion object {
		/** Drift allowed before the position counts as a seek (the progress ticks every 250 ms). */
		const val POSITION_TOLERANCE_MS = 1500L
	}
}
