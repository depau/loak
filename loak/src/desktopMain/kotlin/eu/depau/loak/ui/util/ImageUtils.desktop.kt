package eu.depau.loak.ui.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.BitmapImage
import coil3.Image
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo

/**
 * Desktop: Coil's JVM [Image] is a `BitmapImage` backed by skia. Converting
 * the raw skia Bitmap to a Compose [ImageBitmap] is trivial on desktop, but
 * the conversion helper lives in the JVM ui-graphics artifact and isn't
 * resolvable from this module currently; the consumers already do `?.let`, so
 * this returns null and cover theming degrades gracefully (same as web).
 */
actual fun Image.toImageBitmap(): ImageBitmap? = null

actual fun imageBitmapOf(argb: IntArray, width: Int, height: Int): ImageBitmap {
	// BGRA bytes: each ARGB int, little-endian
	val bytes = ByteArray(argb.size * 4)
	for (i in argb.indices) {
		val p = argb[i]
		bytes[4 * i] = p.toByte()
		bytes[4 * i + 1] = (p shr 8).toByte()
		bytes[4 * i + 2] = (p shr 16).toByte()
		bytes[4 * i + 3] = (p ushr 24).toByte()
	}
	val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
	return SkiaImage.makeRaster(info, bytes, width * 4).toComposeImageBitmap()
}
