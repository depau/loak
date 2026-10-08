package eu.depau.loak.di

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.systemcolor.systemAccentColor
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.util.systemDeviceName
import org.koin.compose.koinInject

/**
 * Desktop (JVM): dynamically tracks the window's actual dimensions via
 * [calculateWindowSizeClass], allowing the app layout to respond cleanly
 * between Compact (< 600 dp), Medium (600–839 dp), and Expanded (≥ 840 dp).
 *
 * [PlatformContext.systemColorScheme] is the Dynamic theme's source: Nucleus'
 * reactive system accent (macOS, Windows, and Linux through the XDG portal) seeds
 * a Material scheme in the chosen palette style, re-composing live when the OS
 * accent changes, mirroring Android's dynamic (Material You) scheme.
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
			override val sizeClass = sizeClass

			@Composable
			override fun systemColorScheme(isDark: Boolean): ColorScheme? {
				// reactive to OS accent changes; null where the desktop doesn't expose one
				val accentColor = systemAccentColor() ?: return null
				return rememberDynamicColorScheme(
					seedColor = accentColor,
					isDark = isDark,
					style = koinInject<PreferenceManager>().paletteStyle,
					specVersion = ColorSpec.SpecVersion.SPEC_2025
				)
			}
		}
	}
}
