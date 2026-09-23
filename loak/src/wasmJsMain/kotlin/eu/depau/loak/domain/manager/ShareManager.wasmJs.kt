package eu.depau.loak.domain.manager

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Web: no native share sheet / gallery. The browser has `navigator.share`
 * and a download API, but wiring those in is out of scope for the initial web
 * target, so these are no-ops.
 */
actual class ShareManager {
	actual suspend fun shareImage(bitmap: ImageBitmap, fileName: String) {}
	actual suspend fun saveImage(bitmap: ImageBitmap, fileName: String) {}
	actual suspend fun shareString(string: String) {}
}
