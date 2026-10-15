package eu.depau.loak.ui.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import coil3.toBitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.ImageInfo

actual fun Image.toImageBitmap(): ImageBitmap?
	= runCatching { toBitmap().asComposeImageBitmap() }.getOrNull()

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

actual fun ImageRequest.Builder.softwareBitmap() = this
