package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// iPadOS hides an idle pointer by itself
@Composable
actual fun Modifier.hiddenCursor(hidden: Boolean): Modifier = this
