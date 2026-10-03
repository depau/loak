package eu.depau.loak.di

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.nucleusframework.core.runtime.NucleusApp
import eu.depau.loak.util.systemDeviceName

/**
 * Desktop (JVM): dynamically tracks the window's actual dimensions via
 * [calculateWindowSizeClass], allowing the app layout to respond cleanly
 * between Compact (< 600 dp), Medium (600–839 dp), and Expanded (≥ 840 dp).
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
actual fun rememberPlatformContext(): PlatformContext {
	val sizeClass = calculateWindowSizeClass()
	val name = remember { systemDeviceName() }
	return remember(sizeClass, name) {
		object : PlatformContext {
			override val platformType = PlatformType.Desktop
			override val name = name
			// The packaged app's version (nucleus.app.properties); null under
			// `./gradlew run`, so fall back to a stable label.
			override val appVersion = NucleusApp.version ?: "desktop"
			override val colorScheme = null
			override val sizeClass = sizeClass
		}
	}
}
