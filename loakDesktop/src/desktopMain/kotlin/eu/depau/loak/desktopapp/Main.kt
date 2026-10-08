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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.window.DecoratedWindowScope
import dev.nucleusframework.window.WindowControlType
import dev.nucleusframework.window.WindowControlsRenderer
import dev.nucleusframework.window.tao.TaoDecoratedWindowScope
import dev.nucleusframework.window.utils.linux.LinuxTitleBarButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Alignment
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
	// The packaged app's version (nucleus.app.properties); null under `./gradlew run`.
	initializeSentry(release = NucleusApp.version ?: "desktop-dev")
	nucleusApplication(args) {
		DecoratedWindow(
			onCloseRequest = ::exitApplication,
			title = "Lo'ak",
			icon = remember { loadIcon() },
			state = rememberWindowState(width = 1400.dp, height = 900.dp),
			minimumSize = DpSize(width = 380.dp, height = 300.dp),
		) {
			// the bar slot only reports the title bar height to the OS (macOS centres the
			// traffic lights in it, Windows sizes its caption zone); the app draws the bar
			WindowScaffold(
				titleBar = { Spacer(Modifier.fillMaxWidth().height(titleBarHeight)) },
				titleBarPlacement = TitleBarPlacement.Overlay(passThroughToContent = true),
			) {
				// the controls follow the app theme, not the OS: the native parts through
				// WindowAppearance (traffic lights on macOS), the drawn ones (GNOME/KDE, Windows
				// glyphs) through Nucleus' theme, which defaults to dark
				val themed = @Composable { darkTheme: Boolean, content: @Composable () -> Unit ->
					NucleusDecoratedWindowTheme(isDark = darkTheme) {
						WindowAppearance(if (darkTheme) WindowAppearanceMode.Dark else WindowAppearanceMode.Light)
						content()
					}
				}
				val window = (this@DecoratedWindow as TaoDecoratedWindowScope).window
				val setFullscreen: (Boolean) -> Unit = { window.setFullscreen(it) }
				val chrome = when {
					isMac -> WindowChrome(
						barHeight = titleBarHeight,
						dragArea = Modifier.windowDragArea(),
						leftControls = { dark -> themed(dark) { WindowControls() } },
						rightControls = null,
						setFullscreen = setFullscreen,
					)
					// Windows caption buttons keep their native 32dp height, flush in the corner
					isWindows -> WindowChrome(
						barHeight = titleBarHeight,
						dragArea = Modifier.windowDragArea(),
						leftControls = null,
						rightControls = { dark -> themed(dark) { WindowControls(Modifier.height(32.dp)) } },
						controlsInCorner = true,
						setFullscreen = setFullscreen,
					)
					else -> {
						// Linux: the buttons can sit on both sides at once (close:minimize,maximize),
						// which Nucleus' own layout drops, so each side gets its own row
						val nucleus = rememberLinuxButtonLayout()
						val layout = rememberGsettingsButtonLayout()?.let(::parseButtonLayout)
							?: nucleus.buttons.let { if (nucleus.controlsOnRight) emptyList<LinuxTitleBarButton>() to it else it to emptyList() }
						val side = { slots: List<LinuxTitleBarButton> ->
							if (slots.isEmpty()) null
							else @Composable { dark: Boolean -> themed(dark) { LinuxControls(slots) } }
						}
						WindowChrome(
							barHeight = titleBarHeight,
							dragArea = Modifier.windowDragArea(),
							leftControls = side(layout.first),
							rightControls = side(layout.second),
							// as far from the side edge as from the top: (64 - 24) / 2, less Nucleus' own 8
							edgeInset = 12.dp,
							setFullscreen = setFullscreen,
						)
					}
				}
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
 * The desktop's window button layout, from GNOME's `button-layout` GSettings key, following
 * changes. Nucleus reads and watches that key only on GNOME, but other desktops keep it too
 * (KDE mirrors its own layout into it), so read it everywhere through Nucleus' own bridge, and
 * start its watcher where Nucleus doesn't. Null where the key can't be read.
 * ponytail: drop once Nucleus reads split layouts on every desktop.
 */
@Composable
private fun rememberGsettingsButtonLayout(): String? {
	var layout by remember {
		mutableStateOf(runCatching {
			layoutBridge?.getMethod("nativeGetButtonLayout")?.invoke(null) as String?
		}.getOrNull())
	}
	DisposableEffect(Unit) {
		val listener = Consumer<String> { layout = it }
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
	return layout
}

/**
 * The window buttons before and after the colon of a `button-layout` value, in order, e.g.
 * `close:minimize,maximize` -> ([Close], [Minimize, Maximize]). Other entries (appmenu,
 * spacer, icon) are skipped. Null for a value without any of the three buttons.
 */
internal fun parseButtonLayout(layout: String): Pair<List<LinuxTitleBarButton>, List<LinuxTitleBarButton>>? {
	fun side(part: String) = part.split(',').mapNotNull {
		when (it.trim()) {
			"close" -> LinuxTitleBarButton.CLOSE
			"minimize" -> LinuxTitleBarButton.MINIMIZE
			"maximize" -> LinuxTitleBarButton.MAXIMIZE
			else -> null
		}
	}
	val result = side(layout.substringBefore(':')) to side(layout.substringAfter(':', ""))
	return result.takeIf { it.first.isNotEmpty() || it.second.isNotEmpty() }
}

/** One side's window buttons, drawn by Nucleus' platform renderer and wired to the window. */
@Composable
private fun DecoratedWindowScope.LinuxControls(buttons: List<LinuxTitleBarButton>) {
	val window = (this as TaoDecoratedWindowScope).window
	Row(Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
		for (button in buttons) {
			val (type, onClick) = when (button) {
				LinuxTitleBarButton.CLOSE -> WindowControlType.Close to window::requestUserClose
				LinuxTitleBarButton.MINIMIZE -> WindowControlType.Minimize to window::minimize
				LinuxTitleBarButton.MAXIMIZE ->
					(if (state.isMaximized) WindowControlType.Restore else WindowControlType.Maximize) to
						{ window.setMaximized(!state.isMaximized) }
			}
			WindowControlsRenderer.Platform.Control(type, state, onClick)
		}
	}
}
