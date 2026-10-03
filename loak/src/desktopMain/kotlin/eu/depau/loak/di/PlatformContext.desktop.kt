package eu.depau.loak.di

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.systemcolor.systemAccentColor
import eu.depau.loak.util.systemDeviceName

/**
 * Desktop (JVM): dynamically tracks the window's actual dimensions via
 * [calculateWindowSizeClass], allowing the app layout to respond cleanly
 * between Compact (< 600 dp), Medium (600–839 dp), and Expanded (≥ 840 dp).
 *
 * The [PlatformContext.colorScheme] is the Dynamic theme's source: Nucleus'
 * reactive system accent is used to seed a Material scheme that re-composes
 * live when the OS accent colour or light/dark mode changes, mirroring
 * Android's dynamic (Material You) scheme.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
actual fun rememberPlatformContext(): PlatformContext {
	val sizeClass = calculateWindowSizeClass()
	val name = remember { systemDeviceName() }
	// Reactive to OS accent changes (null where the platform doesn't expose one).
	val accentColor = systemAccentColor()
	val inDarkTheme = isSystemInDarkTheme()
	val colorScheme = if (accentColor != null) {
		rememberDynamicColorScheme(
			seedColor = accentColor,
			isDark = inDarkTheme,
			style = PaletteStyle.TonalSpot,
			specVersion = ColorSpec.SpecVersion.SPEC_2025
		)
	} else {
		null
	}
	return remember(sizeClass, name, colorScheme) {
		object : PlatformContext {
			override val platformType = PlatformType.Desktop
			override val name = name
			// The packaged app's version (nucleus.app.properties); null under
			// `./gradlew run`, so fall back to a stable label.
			override val appVersion = NucleusApp.version ?: "desktop"
			override val colorScheme = colorScheme
			override val sizeClass = sizeClass
		}
	}
}
