package eu.depau.loak.domain.manager

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import eu.depau.loak.di.desktopDataDir
import java.io.File
import java.nio.file.Files

/**
 * Desktop: real on-disk storage under `~/Downloads/Loak` (or the configured
 * downloads dir). Mirrors iOS semantics.
 */
actual class StorageManager {
	private val dispatcher = Dispatchers.IO

	private fun downloadsDir(): File {
		val dir = File(System.getProperty("user.home"), "Downloads/Loak")
		if (!dir.exists()) Files.createDirectories(dir.toPath())
		return dir
	}

	actual fun getDownloadPath(songId: String, extension: String): String =
		File(downloadsDir(), "$songId.$extension").absolutePath

	actual fun deleteFile(path: String): Boolean =
		Files.deleteIfExists(File(path).toPath())

	actual fun getFileSize(path: String): Long =
		File(path).takeIf { it.exists() }?.length() ?: 0L

	actual suspend fun saveFile(path: String, channel: ByteReadChannel) {
		withContext(dispatcher) {
			File(path).parentFile?.mkdirs()
			File(path).outputStream().use { outputStream ->
				val buffer = ByteArray(64 * 1024)
				while (true) {
					val read = channel.readAvailable(buffer)
					if (read == -1) break
					if (read > 0) outputStream.write(buffer, 0, read)
				}
			}
		}
	}

	actual fun clearDownloads() {
		downloadsDir().listFiles()?.forEach { it.delete() }
	}

	actual fun audioStoreDir(): String? = File(desktopDataDir, "audio").absolutePath
}

internal actual fun freeSpace(dir: String): Long? = File(dir).usableSpace.takeIf { it > 0 }
