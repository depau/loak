package eu.depau.loak.di

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Web (Wasm): tracks the browser viewport via [calculateWindowSizeClass], so the layout
 * switches between Compact, Medium and Expanded as the window is resized.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
actual fun rememberPlatformContext(): PlatformContext {
	val sizeClass = calculateWindowSizeClass()
	return remember(sizeClass) {
		object : PlatformContext {
			override val platformType = PlatformType.Web
			override val name = "Web"
			override val appVersion = "web"
			override val sizeClass = sizeClass

			@Composable
			override fun systemColorScheme(isDark: Boolean): ColorScheme? = null
		}
	}
}
