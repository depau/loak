package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Hides the mouse cursor over this element while [hidden], where the platform allows it. */
@Composable
expect fun Modifier.hiddenCursor(hidden: Boolean): Modifier
