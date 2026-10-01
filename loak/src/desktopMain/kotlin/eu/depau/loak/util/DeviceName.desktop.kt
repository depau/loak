package eu.depau.loak.util

import java.net.InetAddress

/**
 * Desktop: the machine hostname. Falls back to the OS name when no hostname
 * is resolvable.
 */
actual fun systemDeviceName(): String {
	return runCatching { InetAddress.getLocalHost().hostName }
		.getOrDefault(System.getProperty("os.name"))
}
