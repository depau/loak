package eu.depau.loak.ui.util

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isLandscape

/**
 * A desktop window without an OS title bar (client-side decorations): the bars along the
 * window's top edge double as the title bar, and [App][eu.depau.loak.App] draws [controls]
 * in the top corner. Null on platforms that keep their own window chrome.
 */
@Immutable
data class WindowChrome(
	/** The controls sit at the window's left edge (macOS, some Linux layouts). */
	val controlsOnLeft: Boolean,
	/** Height of the title bar row; the controls are centred in it. */
	val barHeight: Dp,
	/** Makes a surface move the window when dragged (double-click maximizes). */
	val dragArea: Modifier,
	/**
	 * Minimize / maximize / close, as the platform draws them. [darkTheme] is the app theme's,
	 * so the native parts (traffic lights, caption glyphs) can match it rather than the OS.
	 */
	val controls: @Composable (darkTheme: Boolean) -> Unit,
	/**
	 * The controls must sit flush in the window's top corner (Windows: snap layouts open from
	 * Maximize), so a pane under them docks to the window edge instead of floating.
	 */
	val controlsInCorner: Boolean = false,
	/**
	 * Extra room between the controls and the window's side edge, so they sit as far from it
	 * as from the top (the drawn Linux controls); none where the platform places them.
	 */
	val edgeInset: Dp = 0.dp,
	/** Measured by App once the controls are laid out. */
	val controlsWidth: Dp = 0.dp,
	/** A pane along the controls' edge draws them in its header, so the top bars needn't. */
	val controlsInPane: Boolean = false,
)

val LocalWindowChrome = compositionLocalOf<WindowChrome?> { null }

/** Width of the app's navigation rail (Material's NavigationRail). */
private val RailWidth = 80.dp

/**
 * Room a bar along the window's top edge leaves for the window controls. With the controls
 * on the left and a navigation rail, the rail sits under them, so only what overhangs it;
 * bars over the whole window (the player) pass [besideRail] false.
 */
@Composable
fun windowControlsInsets(besideRail: Boolean = true): WindowInsets {
	val chrome = LocalWindowChrome.current ?: return WindowInsets(0)
	if (chrome.controlsInPane) return WindowInsets(0)
	val room = chrome.controlsWidth + 8.dp
	return when {
		!chrome.controlsOnLeft -> WindowInsets(right = room)
		besideRail && LocalPlatformContext.current.isLandscape() ->
			WindowInsets(left = (room - RailWidth).coerceAtLeast(0.dp))
		else -> WindowInsets(left = room)
	}
}

/** Lets a bar along the window's top edge move the window, where the window has no title bar. */
@Composable
fun Modifier.windowDragArea(): Modifier = then(LocalWindowChrome.current?.dragArea ?: Modifier)

/**
 * Draws [WindowChrome.controls] in the window's top corner over [content], and hands the
 * measured controls width down so the top bars keep clear of them. With [paneOpen], a pane
 * along the right edge is showing: controls on the right move into its header instead.
 */
@Composable
fun WindowChromeHost(paneOpen: Boolean, content: @Composable () -> Unit) {
	val chrome = LocalWindowChrome.current ?: return content()
	val density = LocalDensity.current
	var controlsWidth by remember { mutableStateOf(0.dp) }
	val inPane = paneOpen && !chrome.controlsOnLeft
	CompositionLocalProvider(
		LocalWindowChrome provides chrome.copy(controlsWidth = controlsWidth, controlsInPane = inPane)
	) {
		Box(Modifier.fillMaxSize()) {
			content()
			if (!inPane) Box(
				Modifier
					.align(if (chrome.controlsOnLeft) Alignment.TopStart else Alignment.TopEnd)
					.height(chrome.barHeight)
					.then(chrome.dragArea)
					.padding(
						start = if (chrome.controlsOnLeft) chrome.edgeInset else 0.dp,
						end = if (chrome.controlsOnLeft) 0.dp else chrome.edgeInset
					)
					.onSizeChanged { controlsWidth = with(density) { it.width.toDp() } }
			) {
				chrome.controls(MaterialTheme.colorScheme.surface.luminance() < .5f)
			}
		}
	}
}

/**
 * The window controls, for the header of a pane along the window's right edge while
 * [WindowChrome.controlsInPane]. Nothing otherwise.
 */
@Composable
fun PaneWindowControls(modifier: Modifier = Modifier) {
	val chrome = LocalWindowChrome.current?.takeIf { it.controlsInPane } ?: return
	Box(modifier.then(chrome.dragArea)) {
		chrome.controls(MaterialTheme.colorScheme.surface.luminance() < .5f)
	}
}
