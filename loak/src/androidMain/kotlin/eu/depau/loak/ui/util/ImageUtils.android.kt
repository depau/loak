package eu.depau.loak.ui.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.Image
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap

actual fun Image.toImageBitmap(): ImageBitmap?
	= runCatching { toBitmap().asImageBitmap() }.getOrNull()

actual fun imageBitmapOf(argb: IntArray, width: Int, height: Int): ImageBitmap =
	Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

actual fun ImageRequest.Builder.softwareBitmap() = allowHardware(false)
