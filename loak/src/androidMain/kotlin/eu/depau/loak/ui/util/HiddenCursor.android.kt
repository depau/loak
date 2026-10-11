package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon

private val Hidden = PointerIcon(android.view.PointerIcon.TYPE_NULL)

@Composable
actual fun Modifier.hiddenCursor(hidden: Boolean): Modifier =
	pointerHoverIcon(if (hidden) Hidden else PointerIcon.Default, overrideDescendants = hidden)
