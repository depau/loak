package eu.depau.loak.desktopapp

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.function.Consumer
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import eu.depau.loak.di.initializeSentry
import org.jetbrains.skia.Image
import androidx.compose.runtime.CompositionLocalProvider
import dev.nucleusframework.window.TitleBarPlacement
import dev.nucleusframework.window.WindowAppearance
import dev.nucleusframework.window.WindowAppearanceMode
import dev.nucleusframework.window.utils.linux.rememberLinuxButtonLayout
import eu.depau.loak.ui.util.LocalUpdateController
import eu.depau.loak.ui.util.LocalWindowChrome
import eu.depau.loak.ui.util.WindowChrome
import eu.depau.loak.domain.manager.DesktopUpdaterManager

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
	// Nucleus' Linux HiDPI step reads GNOME's scaling-factor and exports it as GDK_SCALE, which
	// pins every window to that scale; Wayland compositors (niri, Sway, KDE…) don't use that
	// setting. Setting the property makes it skip, so GTK takes each window's scale from the
	// compositor and Nucleus re-scales when the window moves to another monitor.
	if (System.getenv("WAYLAND_DISPLAY") != null && System.getProperty("sun.java2d.uiScale") == null) {
		System.setProperty("sun.java2d.uiScale", "1")
	}
	initKoin()
	initializeSentry()
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
					(!isMac && !isWindows && rememberGsettingsControlsOnLeft())
				// Nucleus put them on the right (its non-GNOME default): mirror its button row
				val direction =
					if (controlsOnLeft != nucleusOnLeft) ControlButtonsDirection.Rtl
					else ControlButtonsDirection.Auto
				val chrome = WindowChrome(
					controlsOnLeft = controlsOnLeft,
					barHeight = titleBarHeight,
					dragArea = Modifier.windowDragArea(),
					controlsInCorner = isWindows,
					// as far from the side edge as from the top: (64 - 24) / 2, less Nucleus' own 8
					edgeInset = if (isMac || isWindows) 0.dp else 12.dp,
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
				val updateController = remember { DesktopUpdaterManager() }
				CompositionLocalProvider(
					LocalWindowChrome provides chrome,
					LocalUpdateController provides updateController,
				) {
					// Ctrl+Q quits (macOS has its own Cmd+Q)
					Box(Modifier.onPreviewKeyEvent {
						val quit = !isMac && it.type == KeyEventType.KeyDown && it.isCtrlPressed && it.key == Key.Q
						if (quit) exitApplication()
						quit
					}) {
						ProvideSystemTheme { App(menuBar = { LoakMenuBar(it, ::exitApplication) }) }
					}
				}
			}
		}
	}
}

private fun loadIcon(): BitmapPainter? =
	Thread.currentThread().contextClassLoader?.getResourceAsStream("icons/loak.png")?.use {
		BitmapPainter(Image.makeFromEncoded(it.readAllBytes()).toComposeImageBitmap())
	}

/** Nucleus' bridge to GSettings' `button-layout` key (not public API, hence reflection). */
private val layoutBridge = runCatching {
	Class.forName("dev.nucleusframework.window.NativeLayoutDirectionBridge")
}.getOrNull()

/**
 * Whether the desktop puts the window buttons on the left, from GNOME's `button-layout`
 * GSettings key, following changes. Nucleus reads and watches that key only on GNOME, but
 * other desktops keep it too (KDE mirrors its own layout into it), so read it everywhere
 * through Nucleus' own bridge, and start its watcher where Nucleus doesn't.
 * ponytail: drop once Nucleus reads the layout on every desktop (button set still Nucleus').
 */
@Composable
private fun rememberGsettingsControlsOnLeft(): Boolean {
	var onLeft by remember {
		mutableStateOf(controlsOnLeft(runCatching {
			layoutBridge?.getMethod("nativeGetButtonLayout")?.invoke(null) as String?
		}.getOrNull()))
	}
	DisposableEffect(Unit) {
		val listener = Consumer<String> { onLeft = controlsOnLeft(it) }
		val bridge = layoutBridge?.getField("INSTANCE")?.get(null)
		runCatching {
			layoutBridge!!.getMethod("registerButtonLayoutListener", Consumer::class.java)
				.invoke(bridge, listener)
			val gnome = System.getenv("XDG_CURRENT_DESKTOP").orEmpty().contains("GNOME", true)
			if (!gnome) layoutBridge.getMethod("nativeStartButtonLayoutObserving").invoke(null)
		}
		onDispose {
			runCatching {
				layoutBridge!!.getMethod("removeButtonLayoutListener", Consumer::class.java)
					.invoke(bridge, listener)
			}
		}
	}
	return onLeft
}

/** `close` before the colon and not after it, in a `button-layout` value. */
private fun controlsOnLeft(layout: String?): Boolean =
	layout != null && "close" in layout.substringBefore(':', "") &&
		"close" !in layout.substringAfter(':', layout)
