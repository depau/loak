package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// ponytail: Compose web keeps its CSS cursors internal; a `cursor: none` stylesheet if it matters
@Composable
actual fun Modifier.hiddenCursor(hidden: Boolean): Modifier = this
