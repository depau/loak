package eu.depau.loak.shared

import dev.nucleusframework.rodio.PlaybackCallback
import dev.nucleusframework.rodio.RodioPlayer

/**
 * The rodio output, opened on first use and reopened after a failed open. Without an
 * audio device (a box with no sound card, a sound server that isn't running)
 * `RodioPlayer()` throws, which used to crash the app at startup. Until a device
 * opens, the output reads as idle and paused, and starting a source throws, which the
 * player reports like any source that failed to start.
 */
internal class AudioOutput(private val callback: PlaybackCallback) {
	private var player: RodioPlayer? = null
	private var volume = 1f

	@Synchronized
	private fun open(): RodioPlayer = player ?: try {
		RodioPlayer()
	} catch (e: LinkageError) {
		// the native library itself can't load (libasound missing): not fatal either
		throw IllegalStateException("No audio output", e)
	}.also {
		it.setCallback(callback)
		it.setVolume(volume)
		player = it
	}

	fun playUrl(url: String, loop: Boolean) = open().playUrl(url, loop)

	fun playFile(path: String, loop: Boolean) = open().playFile(path, loop)

	fun playRadio(url: String) = open().playRadio(url)

	fun play() = player?.play()

	fun pause() = player?.pause()

	fun clear() = player?.clear()

	fun seekToMs(ms: Long) = player?.seekToMs(ms)

	fun setVolume(value: Float) {
		volume = value
		player?.setVolume(value)
	}

	fun getPositionMs() = player?.getPositionMs() ?: 0L

	fun getDurationMs() = player?.getDurationMs()

	fun isSeekable() = player?.isSeekable() ?: false

	fun isPaused() = player?.isPaused() ?: true

	fun isEmpty() = player?.isEmpty() ?: true

	@Synchronized
	fun close() {
		player?.close()
		player = null
	}
}
