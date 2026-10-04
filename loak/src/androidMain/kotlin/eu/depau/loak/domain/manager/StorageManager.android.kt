package eu.depau.loak.domain.manager

import android.content.Context
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

actual class StorageManager(
	private val context: Context
) {
	private val dispatcher = Dispatchers.IO

	actual fun getDownloadPath(songId: String, extension: String): String {
		val dir = File(context.filesDir, "downloads")
		if (!dir.exists()) dir.mkdirs()
		return File(dir, "$songId.$extension").absolutePath
	}

	actual fun deleteFile(path: String): Boolean {
		return File(path).delete()
	}

	actual fun getFileSize(path: String): Long {
		return try {
			File(path).length()
		} catch (_: Exception) {
			0L
		}
	}

	actual suspend fun saveFile(path: String, channel: ByteReadChannel) {
		withContext(dispatcher) {
			FileOutputStream(path).use { outputStream ->
				channel.copyTo(outputStream)
			}
		}
	}

	actual fun clearDownloads() {
		val dir = File(context.filesDir, "downloads")
		if (dir.exists()) {
			dir.listFiles()?.forEach { it.deleteRecursively() }
		}
	}

	// not cacheDir: the system would purge downloads too; the store evicts by itself
	actual fun audioStoreDir(): String? = File(context.filesDir, "audio").absolutePath
}

internal actual fun freeSpace(dir: String): Long? = File(dir).usableSpace.takeIf { it > 0 }
