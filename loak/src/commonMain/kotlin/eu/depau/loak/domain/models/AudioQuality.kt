package eu.depau.loak.domain.models

/**
 * What a stored audio file was fetched as: the original file, or a server transcode. Built from
 * the same `format` and `maxBitRate` the stream URL gets, never from the URL itself (its auth
 * token changes). The original beats any transcode; transcodes rank by bitrate, the format only
 * tells equal-bitrate ones apart.
 */
data class AudioQuality(
	/** null: the original file. */
	val format: String?,
	val kbps: Int
) : Comparable<AudioQuality> {
	/** Stable id stored in the index: `raw` or `<format>@<kbps>`. */
	val key: String get() = if (format == null) RAW else "$format@$kbps"

	private val rank get() = if (format == null) Int.MAX_VALUE else kbps

	override fun compareTo(other: AudioQuality) = rank.compareTo(other.rank)

	companion object {
		private const val RAW = "raw"

		val Raw = AudioQuality(null, 0)

		/**
		 * As requested from `stream`: no format and no bitrate cap, or format `raw`, is the
		 * original; a cap without a format is the server's default transcode format.
		 */
		fun of(format: String?, maxBitRate: Int?): AudioQuality {
			val f = format?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
			val kbps = maxBitRate?.coerceAtLeast(0) ?: 0
			return when {
				f == RAW || (f == null && kbps == 0) -> Raw
				else -> AudioQuality(f ?: "default", kbps)
			}
		}

		fun parse(key: String): AudioQuality =
			if (key == RAW) Raw
			else AudioQuality(key.substringBeforeLast('@'), key.substringAfterLast('@').toInt())
	}
}
