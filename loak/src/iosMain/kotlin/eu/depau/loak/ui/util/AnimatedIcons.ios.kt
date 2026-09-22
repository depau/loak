package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import eu.depau.loak.ui.navigation.Screen

@Composable
actual fun animatedTabIconPainter(destination: Screen): Painter? = null

@Composable
actual fun playPauseIconPainter(reversed: Boolean): Painter? = null
