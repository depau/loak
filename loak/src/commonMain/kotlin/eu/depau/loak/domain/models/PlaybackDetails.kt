package eu.depau.loak.domain.models

/**
 * Where the currently playing audio bytes were loaded from.
 */
enum class PlaybackSource {
	Stream,
	Cache,
	Download
}

/**
 * Runtime technical details of the currently playing track.
 * Produced by the player; omitted fields mean unavailable or not applicable on this platform.
 */
data class PlaybackDetails(
	val songId: String,
	val source: PlaybackSource = PlaybackSource.Stream,
	val codec: String? = null,
	val bitrateKbps: Int? = null,
	val sampleRateHz: Int? = null,
	val channelCount: Int? = null,
	val bitDepth: Int? = null,
	val isTranscoded: Boolean = false,
	val decoder: String? = null,
	val pcmFormat: String? = null,
	val replayGain: String? = null,
	val equalizer: String? = null,
	val isOffloaded: Boolean = false,
	val outputDevice: String? = null,
	val outputFormat: String? = null
)

/**
 * Normalizes an audio MIME type or file extension to a display-ready codec name (e.g. "FLAC", "Opus").
 */
fun formatCodecName(mimeType: String?, fallback: String?): String? {
	val raw = mimeType?.lowercase()
	return when {
		raw == null -> fallback?.uppercase()
		raw.contains("opus") -> "Opus"
		raw.contains("flac") -> "FLAC"
		raw.contains("mpeg") || raw.contains("mp3") -> "MP3"
		raw.contains("mp4") || raw.contains("m4a") || raw.contains("aac") -> "AAC"
		raw.contains("ogg") || raw.contains("vorbis") -> "Vorbis"
		raw.contains("wav") -> "WAV"
		raw.contains("alac") -> "ALAC"
		else -> (mimeType.substringAfterLast('/').substringAfterLast('-')).uppercase()
	}
}

/**
 * Formats sample rate in Hz to a standard string (e.g. "44.1 kHz", "48 kHz", "96 kHz").
 */
fun formatSampleRate(sampleRateHz: Int): String {
	return if (sampleRateHz >= 1000) {
		val khz = sampleRateHz / 1000.0
		if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz" else "$khz kHz"
	} else {
		"$sampleRateHz Hz"
	}
}
