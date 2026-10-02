package eu.depau.loak.desktopapp

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.ControlButtonsDirection
import dev.nucleusframework.window.NucleusDecoratedWindowTheme
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.WindowScaffold
import dev.nucleusframework.window.windowDragArea
import eu.depau.loak.App
import eu.depau.loak.di.initKoin
import org.jetbrains.skia.Image
import androidx.compose.runtime.CompositionLocalProvider
import dev.nucleusframework.window.TitleBarPlacement
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.utils.linux.rememberLinuxButtonLayout
import eu.depau.loak.ui.util.LocalWindowChrome
import eu.depau.loak.ui.util.WindowChrome

private val os = System.getProperty("os.name")
private val isMac = os.startsWith("Mac")
private val isWindows = os.startsWith("Windows")

/** The app's top bar row, which doubles as the title bar. */
private val titleBarHeight = 64.dp

/**
 * Desktop (JVM) entry point. Boots Koin and hosts the shared Compose [App] in a
 * Nucleus decorated window: no OS title bar, the app's own top bars act as one
 * (client-side decorations) and App draws the native-looking window controls.
 */
fun main(args: Array<String>) {
	initKoin()
	nucleusApplication(args) {
		DecoratedWindow(
			onCloseRequest = ::exitApplication,
			title = "Lo'ak",
			icon = remember { loadIcon() },
			state = rememberWindowState(width = 1400.dp, height = 900.dp),
		) {
			// the bar slot only reports the title bar height to the OS (macOS centres the
			// traffic lights in it, Windows sizes its caption zone); the app draws the bar
			WindowScaffold(
				titleBar = { Spacer(Modifier.fillMaxWidth().height(titleBarHeight)) },
				titleBarPlacement = TitleBarPlacement.Overlay(passThroughToContent = true),
			) {
				val nucleusOnLeft = !isWindows && (isMac || !rememberLinuxButtonLayout().controlsOnRight)
				val controlsOnLeft = nucleusOnLeft ||
					(!isMac && !isWindows && remember { gsettingsControlsOnLeft() })
				// Nucleus put them on the right (its non-GNOME default): mirror its button row
				val direction =
					if (controlsOnLeft != nucleusOnLeft) ControlButtonsDirection.Rtl
					else ControlButtonsDirection.Auto
				val chrome = WindowChrome(
					controlsOnLeft = controlsOnLeft,
					barHeight = titleBarHeight,
					dragArea = Modifier.windowDragArea(),
					controls = { darkTheme ->
						// the controls follow the app theme, not the OS: the native parts through
						// WindowAppearance (traffic lights on macOS), the drawn ones (GNOME/KDE,
						// Windows glyphs) through Nucleus' theme, which defaults to dark
						NucleusDecoratedWindowTheme(isDark = darkTheme) {
							WindowAppearance(
								if (darkTheme) WindowAppearanceMode.Dark else WindowAppearanceMode.Light
							)
							// Windows caption buttons keep their native 32dp height, in the corner
							WindowControls(
								if (isWindows) Modifier.height(32.dp) else Modifier,
								direction = direction,
							)
						}
					},
				)
				CompositionLocalProvider(LocalWindowChrome provides chrome) {
					App()
				}
			}
		}
	}
}

private fun loadIcon(): BitmapPainter? =
	Thread.currentThread().contextClassLoader?.getResourceAsStream("icons/loak.png")?.use {
		BitmapPainter(Image.makeFromEncoded(it.readAllBytes()).toComposeImageBitmap())
	}

/**
 * Whether the desktop puts the window buttons on the left, from GNOME's `button-layout`
 * GSettings key. Nucleus reads that key only on GNOME, but other desktops keep it too
 * (KDE mirrors its own layout into it), so read it everywhere with Nucleus' own reader.
 * ponytail: drop once Nucleus reads the layout on every desktop (button set still Nucleus').
 */
private fun gsettingsControlsOnLeft(): Boolean = runCatching {
	val layout = Class.forName("dev.nucleusframework.window.NativeLayoutDirectionBridge")
		.getMethod("nativeGetButtonLayout").invoke(null) as String?
	layout != null && "close" in layout.substringBefore(':', "") &&
		"close" !in layout.substringAfter(':', layout)
}.getOrDefault(false)
