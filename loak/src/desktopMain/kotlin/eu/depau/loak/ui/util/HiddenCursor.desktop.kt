package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import com.sun.jna.Library
import com.sun.jna.Native
import eu.depau.loak.util.isApplePlatform

/**
 * The Tao window maps only its built-in cursor shapes (no AWT here, so no blank custom cursor):
 * hide the cursor through CoreGraphics instead, which shows it again once we leave.
 */
// ponytail: macOS only; Windows/Linux need Nucleus to expose Tao's set_cursor_visible
@Composable
actual fun Modifier.hiddenCursor(hidden: Boolean): Modifier {
	if (hidden && isApplePlatform) DisposableEffect(Unit) {
		coreGraphics.CGDisplayHideCursor(0)
		onDispose { coreGraphics.CGDisplayShowCursor(0) }
	}
	return this
}

@Suppress("FunctionName")
private interface CoreGraphics : Library {
	// the display argument is unused: the cursor hides on every display
	fun CGDisplayHideCursor(display: Int): Int
	fun CGDisplayShowCursor(display: Int): Int
}

private val coreGraphics by lazy { Native.load("CoreGraphics", CoreGraphics::class.java) }
