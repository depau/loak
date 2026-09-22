package eu.depau.loak.ui.util

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image

expect fun Image.toImageBitmap(): ImageBitmap?
