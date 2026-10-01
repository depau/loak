package eu.depau.loak.di

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
			override val appVersion = "desktop"
			override val colorScheme = null
			override val sizeClass = sizeClass
		}
	}
}
