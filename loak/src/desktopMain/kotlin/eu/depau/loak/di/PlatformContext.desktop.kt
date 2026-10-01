package eu.depau.loak.di

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import eu.depau.loak.util.systemDeviceName

/**
 * Desktop (JVM): the Compose desktop window has no native window-size hook in
 * commonMain; we use a fixed 1200x800 window class so desktop gets the
 * "expanded" wide layout (player bar, side pane, rail) that the wasm target
 * already gets from the browser size. appVersion is injected by the app module
 * through [ResourceProvider] later; for now it reads the git-ish placeholder.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
actual fun rememberPlatformContext(): PlatformContext {
	val density = LocalDensity.current
	val sizeClass = remember(density) {
		WindowSizeClass.calculateFromSize(DpSize(1200.dp, 800.dp))
	}
	return remember(sizeClass) {
		object : PlatformContext {
			override val platformType = PlatformType.Desktop
			override val name = systemDeviceName()
			override val appVersion = "desktop"
			override val colorScheme = null
			override val sizeClass = sizeClass
		}
	}
}
