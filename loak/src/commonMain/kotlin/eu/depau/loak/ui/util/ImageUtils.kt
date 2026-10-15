package eu.depau.loak.ui.util

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image
import coil3.request.ImageRequest

expect fun Image.toImageBitmap(): ImageBitmap?

/** A picture of [width] × [height] ARGB pixels (not premultiplied), row by row. */
expect fun imageBitmapOf(argb: IntArray, width: Int, height: Int): ImageBitmap

/** Decodes into a bitmap software canvases can draw (Android's default, hardware, can't). */
expect fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder
