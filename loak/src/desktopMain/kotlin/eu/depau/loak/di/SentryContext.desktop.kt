package eu.depau.loak.di

import io.sentry.SentryEvent
import io.sentry.protocol.Device
import io.sentry.protocol.OperatingSystem
import java.awt.GraphicsEnvironment
import java.io.File
import java.lang.management.ManagementFactory
import java.util.Locale

/*
 * OS, display and hardware details for desktop events. Sentry's Android SDK and
 * Sentry Cocoa collect these themselves; the plain JVM SDK sends only the JVM.
 * Nothing here identifies the user or the machine (no hostname, paths or IDs).
 */

internal fun addDesktopContext(event: SentryEvent) {
	event.contexts.setOperatingSystem(osContext)
	event.contexts.setDevice(deviceContext())
	screens()?.let { event.contexts.put("screens", it) }
	linuxDisplayServer?.let { event.setTag("display.server", it) }
	System.getenv("XDG_CURRENT_DESKTOP")?.let { event.setTag("desktop.environment", it) }
}

private val osContext: OperatingSystem by lazy {
	val osName = System.getProperty("os.name")
	val osVersion = System.getProperty("os.version")
	OperatingSystem().apply {
		when {
			osName.startsWith("Linux") -> {
				val release = linuxOsRelease()
				name = release["NAME"] ?: osName
				// rolling distros (Arch) have no VERSION_ID
				version = release["VERSION_ID"] ?: release["BUILD_ID"]
				rawDescription = release["PRETTY_NAME"]
				kernelVersion = osVersion
			}
			osName.startsWith("Mac") -> {
				name = "macOS"
				version = osVersion
			}
			// "Windows 11", "10.0"
			else -> {
				name = osName
				version = osVersion
			}
		}
	}
}

private fun linuxOsRelease(): Map<String, String> =
	listOf("/etc/os-release", "/usr/lib/os-release").map(::File).firstOrNull(File::isFile)
		?.readLines().orEmpty()
		.mapNotNull { line -> line.split('=', limit = 2).takeIf { it.size == 2 } }
		.associate { (key, value) -> key to value.trim().removeSurrounding("\"") }

/**
 * The display server the app's windows run on: Nucleus' Tao draws them with GTK, which
 * prefers Wayland unless GDK_BACKEND puts X11 first. Null off Linux.
 */
private val linuxDisplayServer: String? by lazy {
	if (!System.getProperty("os.name").startsWith("Linux")) return@lazy null
	val env = System.getenv()
	when {
		env["WAYLAND_DISPLAY"] != null && env["GDK_BACKEND"]?.startsWith("x11") != true -> "wayland"
		env["DISPLAY"] == null -> null
		env["XDG_SESSION_TYPE"] == "wayland" -> "xwayland"
		else -> "x11"
	}
}

private fun deviceContext() = Device().apply {
	family = "Desktop"
	archs = arrayOf(System.getProperty("os.arch"))
	processorCount = Runtime.getRuntime().availableProcessors()
	(ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)?.let {
		memorySize = it.totalMemorySize
		freeMemory = it.freeMemorySize
	}
	locale = Locale.getDefault().toLanguageTag()
}

/**
 * Every connected screen as AWT sees it: "2560x1440 @1.5x 60 Hz". On Wayland AWT
 * only sees XWayland, with the scale pinned to 1 (see the desktop main), so the
 * scale is left out there.
 */
private fun screens(): Map<String, String>? = runCatching {
	if (GraphicsEnvironment.isHeadless()) return null
	GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.withIndex()
		.associate { (i, screen) ->
			val mode = screen.displayMode
			val scale = if (linuxDisplayServer == "wayland") "" else
				" @${screen.defaultConfiguration.defaultTransform.scaleX}x"
			"screen $i" to "${mode.width}x${mode.height}$scale ${mode.refreshRate} Hz"
		}
}.getOrNull()
