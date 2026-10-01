package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import eu.depau.loak.ui.navigation.Screen

/**
 * Desktop: no animated-vector resource API; the UI falls back to its static
 * icons (it already null-checks these painters).
 */
@Composable
actual fun animatedTabIconPainter(destination: Screen): Painter? = null

@Composable
actual fun playPauseIconPainter(reversed: Boolean): Painter? = null
