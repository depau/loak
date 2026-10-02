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
				val controlsOnLeft = when {
					isMac -> true
					isWindows -> false
					else -> !rememberLinuxButtonLayout().controlsOnRight
				}
				val chrome = WindowChrome(
					controlsOnLeft = controlsOnLeft,
					barHeight = titleBarHeight,
					dragArea = Modifier.windowDragArea(),
					controls = { darkTheme ->
						// native parts follow the app theme, not the OS (traffic lights on macOS,
						// caption glyphs on Windows)
						WindowAppearance(
							if (darkTheme) WindowAppearanceMode.Dark else WindowAppearanceMode.Light
						)
						// Windows caption buttons keep their native 32dp height, flush in the corner
						WindowControls(if (isWindows) Modifier.height(32.dp) else Modifier)
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
