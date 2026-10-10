@file:OptIn(ExperimentalForeignApi::class)

package eu.depau.loak.domain.discover

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.addPeriodicTimeObserverForInterval
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.removeTimeObserver
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL

/** [AVPlayer]-backed [PreviewPlayer] for Deezer 30-second preview streams. */
class IosPreviewPlayer : PreviewPlayer {
	private val player = AVPlayer()
	private var timeObserver: Any? = null
	private var endObserver: Any? = null

	private val _state = MutableStateFlow(PreviewPlayerState())
	override val state: StateFlow<PreviewPlayerState> = _state.asStateFlow()

	init {
		timeObserver = player.addPeriodicTimeObserverForInterval(
			interval = CMTimeMakeWithSeconds(0.25, 600),
			queue = null
		) { _ ->
			val positionMs = (CMTimeGetSeconds(player.currentTime()) * 1000).toLong()
			val durationMs = player.currentItem?.let { CMTimeGetSeconds(it.duration) * 1000 }.orZero()
			_state.update {
				it.copy(
					positionMs = positionMs,
					durationMs = durationMs,
					isEnded = durationMs > 0 && positionMs >= durationMs - 250
				)
			}
		}
		endObserver = NSNotificationCenter.defaultCenter.addObserverForName(
			name = AVPlayerItemDidPlayToEndTimeNotification,
			`object` = null,
			queue = NSOperationQueue.mainQueue
		) { _ ->
			val durationMs = player.currentItem?.let { CMTimeGetSeconds(it.duration) * 1000 }.orZero()
			_state.update { it.copy(isPlaying = false, isEnded = true, positionMs = durationMs) }
		}
	}

	override fun play(url: String) {
		player.pause()
		val nsUrl = NSURL.URLWithString(url) ?: return
		player.replaceCurrentItemWithPlayerItem(AVPlayerItem(nsUrl))
		_state.update { PreviewPlayerState(isPlaying = true, positionMs = 0L) }
		player.play()
	}

	override fun pause() {
		player.pause()
		_state.update { it.copy(isPlaying = false) }
	}

	override fun resume() {
		_state.update { it.copy(isPlaying = true) }
		player.play()
	}

	override fun stop() {
		player.pause()
		player.replaceCurrentItemWithPlayerItem(null)
		_state.value = PreviewPlayerState()
	}

	private fun Double?.orZero(): Long = (this ?: 0.0).toLong()
}
