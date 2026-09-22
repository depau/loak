package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import eu.depau.loak.ui.navigation.Screen

@Composable
expect fun animatedTabIconPainter(destination: Screen): Painter?

@Composable
expect fun playPauseIconPainter(reversed: Boolean): Painter?
