package eu.depau.loak.domain.manager

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/**
 * Desktop: saves images to the OS Downloads folder and copies strings to the
 * clipboard. There is no share sheet on desktop, so "share" is implemented as
 * save-to-Downloads + open the containing folder.
 */
actual class ShareManager {

	actual suspend fun shareImage(bitmap: ImageBitmap, fileName: String) {
		saveImage(bitmap, fileName)
		openDownloadsFolder()
	}

	actual suspend fun saveImage(bitmap: ImageBitmap, fileName: String) {
		withContext(Dispatchers.IO) {
			val downloadsDir = File(System.getProperty("user.home"), "Downloads")
			downloadsDir.mkdirs()
			val file = File(downloadsDir, fileName)
			val data = Image.makeFromBitmap(bitmap.asSkiaBitmap())
				.encodeToData(EncodedImageFormat.PNG)
				?: return@withContext
			file.writeBytes(data.bytes)
		}
	}

	actual suspend fun shareString(string: String) {
		Toolkit.getDefaultToolkit()
			.systemClipboard
			.setContents(StringSelection(string), null)
	}

	private fun openDownloadsFolder() {
		if (!Desktop.isDesktopSupported()) return
		val downloadsDir = File(System.getProperty("user.home"), "Downloads")
		if (downloadsDir.exists()) Desktop.getDesktop().open(downloadsDir)
	}
}
