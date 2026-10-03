package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Esc dismisses a sheet or dialog. Popups get their own key events (Android draws them in their
 * own window), and with nothing focused in there Esc only clears focus; so this takes focus
 * when it appears (a text field inside still takes it when tapped) and catches Esc first.
 */
@Composable
fun Modifier.escapeToDismiss(onDismiss: () -> Unit): Modifier {
	val focus = remember { FocusRequester() }
	LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
	return this
		.focusRequester(focus)
		.onPreviewKeyEvent { event ->
			if (event.key != Key.Escape) return@onPreviewKeyEvent false
			if (event.type == KeyEventType.KeyDown) onDismiss()
			true
		}
		.focusTarget()
}
