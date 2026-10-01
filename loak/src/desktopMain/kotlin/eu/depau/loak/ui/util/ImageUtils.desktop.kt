package eu.depau.loak.ui.util

import androidx.compose.ui.graphics.ImageBitmap
import coil3.BitmapImage
import coil3.Image

/**
 * Desktop: Coil's JVM [Image] is a `BitmapImage` backed by skia. Converting
 * the raw skia Bitmap to a Compose [ImageBitmap] is trivial on desktop, but
 * the conversion helper lives in the JVM ui-graphics artifact and isn't
 * resolvable from this module currently; the consumers already do `?.let`, so
 * this returns null and cover theming degrades gracefully (same as web).
 */
actual fun Image.toImageBitmap(): ImageBitmap? = null
