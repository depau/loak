package eu.depau.loak.util

import kotlin.math.log10
import kotlin.math.pow
import kotlin.time.Duration

/**
 * Formats a `Duration` as a `String` in HH:MM:SS
 * format. If there is no hours, the 00: at the
 * beginning will be omitted
 */
fun Duration.toHoursMinutesSeconds(): String {
	val totalSeconds = inWholeSeconds

	val hours = totalSeconds / 3600
	val minutes = (totalSeconds % 3600) / 60
	val seconds = totalSeconds % 60

	fun Long.twoDigits() = toString().padStart(2, '0')

	return if (hours > 0) {
		"${hours.twoDigits()}:${minutes.twoDigits()}:${seconds.twoDigits()}"
	} else {
		"${minutes.twoDigits()}:${seconds.twoDigits()}"
	}
}

/**
 * Formats a `Long` as a human-readable file size
 * string, e.g. 1 GB
 */
fun Long.toFileSize(): String {
	if (this <= 0) return "0 B"
	val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
	val digitGroups = (log10(this.toDouble()) / log10(1024.0)).toInt()

	val size = this / 1024.0.pow(digitGroups.toDouble())
	val roundedSize = (size * 100).toInt() / 100.0

	return "$roundedSize ${units[digitGroups]}"
}

private val PRIVATE_IP_RE = Regex(
	"""^(?:10)\.\d{1,3}\.\d{1,3}\.\d{1,3}$|""" +
		"""^(?:172)\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}$|""" +
		"""^(?:192)\.168\.\d{1,3}\.\d{1,3}$|""" +
		"""^(?:100)\.(?:6[4-9]|[7-9]\d|1[0-1]\d|12[0-7])\.\d{1,3}\.\d{1,3}$|""" +
		"""^(?:127)\.\d{1,3}\.\d{1,3}$|""" +
		"""^169\.254\.\d{1,3}\.\d{1,3}$"""
)

/**
 * Returns the host portion (without scheme/port/path) of an instance URL.
 */
fun instanceHost(url: String): String {
	val withoutScheme = url
		.removePrefix("https://")
		.removePrefix("http://")
		.substringBefore("/")
	return withoutScheme.substringBefore(":")
}

/**
 * True when the given host is a private/LAN/IPv4-loopback/carrier-grade NAT
 * address (RFC 1918, 127.0.0.0/8, 169.254.0.0/16, 100.64.0.0/10, or a bare
 * `.local` mDNS hostname). Used to decide whether Android's
 * ACCESS_LOCAL_NETWORK permission matters for a Subsonic instance.
 */
fun isPrivateHost(host: String): Boolean {
	val h = host.trim().lowercase()
	if (h == "localhost" || h.endsWith(".local")) return true
	return PRIVATE_IP_RE.matches(h.split(':')[0])
}
