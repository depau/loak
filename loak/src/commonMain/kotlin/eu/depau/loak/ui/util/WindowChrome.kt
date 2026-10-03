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
 * window's top edge double as the title bar, and [App][eu.depau.loak.App] draws the window
 * controls in the top corners. Null on platforms that keep their own window chrome.
 *
 * The controls can sit on either side, or be split across both (Linux layouts such as
 * `close:minimize,maximize`), so each side has its own group.
 */
@Immutable
data class WindowChrome(
	/** Height of the title bar row; the controls are centred in it. */
	val barHeight: Dp,
	/** Makes a surface move the window when dragged (double-click maximizes). */
	val dragArea: Modifier,
	/**
	 * The controls at the window's left edge (macOS, some Linux layouts), as the platform
	 * draws them; null when none sit there. [darkTheme] is the app theme's, so the native
	 * parts (traffic lights, caption glyphs) can match it rather than the OS.
	 */
	val leftControls: (@Composable (darkTheme: Boolean) -> Unit)?,
	/** The controls at the window's right edge, likewise; null when none sit there. */
	val rightControls: (@Composable (darkTheme: Boolean) -> Unit)?,
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
	val leftWidth: Dp = 0.dp,
	val rightWidth: Dp = 0.dp,
	/** A pane along the right edge draws the right controls in its header, so the top bars needn't. */
	val controlsInPane: Boolean = false,
	/** Puts the window in or out of full screen (karaoke). */
	val setFullscreen: ((Boolean) -> Unit)? = null,
) {
	val hasLeftControls get() = leftControls != null
	val hasRightControls get() = rightControls != null
}

val LocalWindowChrome = compositionLocalOf<WindowChrome?> { null }

/** Width of the app's navigation rail (Material's NavigationRail). */
private val RailWidth = 80.dp

/**
 * Room a bar along the window's top edge leaves for the window controls. With controls on the
 * left and a navigation rail, the rail sits under them, so only what overhangs it; bars over
 * the whole window (the player) pass [besideRail] false. A bar that doesn't reach one of the
 * window's sides passes false for that side.
 */
@Composable
fun windowControlsInsets(besideRail: Boolean = true, left: Boolean = true, right: Boolean = true): WindowInsets {
	val chrome = LocalWindowChrome.current ?: return WindowInsets(0)
	val l = when {
		!left || !chrome.hasLeftControls -> 0.dp
		besideRail && LocalPlatformContext.current.isLandscape() ->
			(chrome.leftWidth + 8.dp - RailWidth).coerceAtLeast(0.dp)
		else -> chrome.leftWidth + 8.dp
	}
	val r = if (right && chrome.hasRightControls && !chrome.controlsInPane) chrome.rightWidth + 8.dp else 0.dp
	return WindowInsets(left = l, right = r)
}

/** Lets a bar along the window's top edge move the window, where the window has no title bar. */
@Composable
fun Modifier.windowDragArea(): Modifier = then(LocalWindowChrome.current?.dragArea ?: Modifier)

/**
 * Draws the window controls in the window's top corners over [content], and hands their
 * measured widths down so the top bars keep clear of them. With [paneOpen], a pane along the
 * right edge is showing: the right controls move into its header instead.
 */
@Composable
fun WindowChromeHost(paneOpen: Boolean, content: @Composable () -> Unit) {
	val chrome = LocalWindowChrome.current ?: return content()
	val density = LocalDensity.current
	var leftWidth by remember { mutableStateOf(0.dp) }
	var rightWidth by remember { mutableStateOf(0.dp) }
	val inPane = paneOpen && chrome.hasRightControls
	CompositionLocalProvider(
		LocalWindowChrome provides chrome.copy(leftWidth = leftWidth, rightWidth = rightWidth, controlsInPane = inPane)
	) {
		Box(Modifier.fillMaxSize()) {
			content()
			val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
			chrome.leftControls?.let { controls ->
				Box(
					Modifier
						.align(Alignment.TopStart)
						.height(chrome.barHeight)
						.then(chrome.dragArea)
						.padding(start = chrome.edgeInset)
						.onSizeChanged { leftWidth = with(density) { it.width.toDp() } }
				) { controls(dark) }
			}
			chrome.rightControls?.takeIf { !inPane }?.let { controls ->
				Box(
					Modifier
						.align(Alignment.TopEnd)
						.height(chrome.barHeight)
						.then(chrome.dragArea)
						.padding(end = chrome.edgeInset)
						.onSizeChanged { rightWidth = with(density) { it.width.toDp() } }
				) { controls(dark) }
			}
		}
	}
}

/**
 * The right window controls, for the header of a pane along the window's right edge while
 * [WindowChrome.controlsInPane]. Nothing otherwise.
 */
@Composable
fun PaneWindowControls(modifier: Modifier = Modifier) {
	val chrome = LocalWindowChrome.current?.takeIf { it.controlsInPane } ?: return
	Box(modifier.then(chrome.dragArea)) {
		chrome.rightControls?.invoke(MaterialTheme.colorScheme.surface.luminance() < .5f)
	}
}
