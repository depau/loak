package eu.depau.loak.domain.discover

import kotlinx.coroutines.flow.StateFlow

/** Where a Deezer 30-second preview stands right now. */
data class PreviewPlayerState(
	val isPlaying: Boolean = false,
	val isEnded: Boolean = false,
	val positionMs: Long = 0L,
	val durationMs: Long = 0L
)

/**
 * Plays a Deezer 30-second preview stream URL (a direct MP3). One url at a time, and by
 * design it never fights the main player for audio focus: the [PreviewManager] pauses the
 * main player first, so previews come out quietly.
 */
interface PreviewPlayer {
	val state: StateFlow<PreviewPlayerState>

	fun play(url: String)
	fun pause()
	fun resume()
	fun stop()
}
