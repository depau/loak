package eu.depau.loak.domain.discover

import eu.depau.loak.util.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.BufferedInputStream
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
 * JVM [PreviewPlayer] for Deezer 30-second previews. Like [DesktopMediaPlayerViewModel], it
 * decodes the MP3 via mp3spi through [AudioSystem] into PCM and streams it to the default
 * SourceDataLine. Previews are short, so position advances by frame count rather than a
 * wall-clock job.
 *
 * Playback runs on an io thread; a streaming job is only current while its [session] token
 * matches, so [stop] makes an in-flight stream end right there.
 */
class DesktopPreviewPlayer : PreviewPlayer {
	private var line: SourceDataLine? = null
	private var stream: AudioInputStream? = null
	private var source: HttpURLConnection? = null

	@Volatile private var playing = false
	@Volatile private var paused = false
	@Volatile private var session = 0

	private val _state = MutableStateFlow(PreviewPlayerState())
	override val state: StateFlow<PreviewPlayerState> = _state.asStateFlow()

	override fun play(url: String) {
		stop()
		val mySession = ++session
		playing = true
		paused = false
		_state.value = PreviewPlayerState(isPlaying = true)

		Thread {
			try {
				val conn = URL(url).openConnection() as HttpURLConnection
				source = conn
				conn.connect()
				if (conn.responseCode !in 200..299) {
					throw IOException("HTTP ${conn.responseCode} for $url")
				}
				val audioStream = AudioSystem.getAudioInputStream(BufferedInputStream(conn.inputStream))
				val baseFormat = audioStream.format
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
				val durationMs = (pcmStream.frameLength * 1000L / decodedFormat.frameRate).toLong()
					.coerceAtLeast(0L)

				if (mySession != session) {
					runCatching { pcmStream.close() }
					return@Thread
				}

				val info = DataLine.Info(SourceDataLine::class.java, decodedFormat)
				val l = AudioSystem.getLine(info) as SourceDataLine
				line = l
				l.open(decodedFormat)
				applyVolumeToLine(l)
				l.start()
				_state.update { it.copy(durationMs = durationMs) }

				val buf = ByteArray(64 * 1024)
				var positionMs = 0L
				val bytesPerSecond = (decodedFormat.sampleRate * decodedFormat.frameSize.coerceAtLeast(1)).toLong()
				while (playing && mySession == session) {
					if (paused) {
						Thread.sleep(50)
						continue
					}
					val read = pcmStream.read(buf, 0, buf.size)
					if (read < 0) {
						_state.update { it.copy(isPlaying = false, isEnded = true, positionMs = durationMs) }
						playing = false
						break
					}
					if (read > 0) {
						l.write(buf, 0, read)
						positionMs += read * 1000L / bytesPerSecond
						if (bytesPerSecond > 0 && mySession == session) {
							_state.update { it.copy(positionMs = positionMs.coerceAtMost(durationMs)) }
						}
					}
				}
			} catch (e: Exception) {
				if (mySession == session && !Thread.currentThread().isInterrupted) {
					Logger.e(TAG, "preview failed", e)
					_state.update { it.copy(isPlaying = false, isEnded = true) }
					playing = false
				}
			} finally {
				closeLine()
				closeStream()
				if (mySession == session) {
					playing = false
					line = null
				}
			}
		}.start()
	}

	override fun pause() {
		if (playing) {
			paused = true
			line?.stop()
			_state.update { it.copy(isPlaying = false) }
		}
	}

	override fun resume() {
		if (playing && paused) {
			paused = false
			line?.start()
			_state.update { it.copy(isPlaying = true, isEnded = false) }
		}
	}

	override fun stop() {
		session++  // invalidate any in-flight stream
		playing = false
		closeLine()
		closeStream()
		paused = false
		_state.value = PreviewPlayerState()
	}

	private fun closeLine() {
		runCatching { line?.stop() }
		runCatching { line?.close() }
		line = null
	}

	private fun closeStream() {
		runCatching { stream?.close() }
		stream = null
		runCatching { source?.disconnect() }
		source = null
	}

	private fun applyVolumeToLine(l: SourceDataLine) {
		runCatching {
			val control = l.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
			// previews at ~0.8 linear (−1.9 dB)
			control.value = -1.9f.coerceIn(control.minimum, control.maximum)
		}.onFailure { Logger.w(TAG, "no volume control") }
	}

	private companion object {
		const val TAG = "DesktopPreviewPlayer"
	}
}
