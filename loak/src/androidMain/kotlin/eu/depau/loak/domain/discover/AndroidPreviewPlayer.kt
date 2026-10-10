package eu.depau.loak.domain.discover

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A second, throwaway [ExoPlayer] for Deezer 30-second previews. Lightweight: the default
 * load control, plain HTTP datasource, and audio-focus handling off so an interrupted call
 * or Spotify never ducks/steals it while the main player is paused.
 */
@OptIn(UnstableApi::class)
class AndroidPreviewPlayer(context: Context) : PreviewPlayer {
	private val player = ExoPlayer.Builder(context)
		.setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(5_000, 30_000, 1_000, 2_000).build())
		.build()
		.apply {
			setAudioAttributes(AudioAttributes.DEFAULT, false)
			setHandleAudioBecomingNoisy(false)
		}
	private val scope = CoroutineScope(Dispatchers.Main.immediate)
	private var positionJob: Job? = null

	private val _state = MutableStateFlow(PreviewPlayerState())
	override val state: StateFlow<PreviewPlayerState> = _state.asStateFlow()

	init {
		player.addListener(object : Player.Listener {
			override fun onIsPlayingChanged(isPlaying: Boolean) {
				// ends and errors set isEnded via their own callbacks; don't clobber it here
				_state.update { it.copy(isPlaying = isPlaying) }
				watchPosition(isPlaying)
			}

			override fun onPlaybackStateChanged(playbackState: Int) {
				when (playbackState) {
					Player.STATE_ENDED -> _state.value = PreviewPlayerState(isPlaying = false, isEnded = true)
					// a fresh play() stops the old instance first: clear the ended flag
					Player.STATE_IDLE -> _state.update { it.copy(isEnded = false) }
				}
			}

			override fun onPlayerError(error: PlaybackException) {
				// an unplayable stream (gone/stale URL) counts as ended so playAll moves on
				_state.value = PreviewPlayerState(isPlaying = false, isEnded = true)
			}
		})
	}

	override fun play(url: String) {
		player.stop()
		player.setMediaItem(MediaItem.fromUri(url))
		player.prepare()
		_state.value = PreviewPlayerState(isPlaying = true)
		player.play()
		watchPosition(true)
	}

	override fun pause() {
		player.pause()
	}

	override fun resume() {
		player.play()
		watchPosition(true)
	}

	override fun stop() {
		positionJob?.cancel()
		player.stop()
		_state.value = PreviewPlayerState()
	}

	/** Reports the playback position/duration up to ~4×/s while playing. */
	private fun watchPosition(active: Boolean) {
		positionJob?.cancel()
		if (!active) {
			positionJob = null
			return
		}
		positionJob = scope.launch {
			while (isActive) {
				val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
				_state.update { it.copy(positionMs = player.currentPosition, durationMs = duration) }
				delay(250)
			}
		}
	}

	fun release() {
		positionJob?.cancel()
		player.release()
	}
}
