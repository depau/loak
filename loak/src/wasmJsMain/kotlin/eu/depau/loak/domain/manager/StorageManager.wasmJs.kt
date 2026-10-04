package eu.depau.loak.domain.manager

import io.ktor.utils.io.ByteReadChannel

/**
 * Web: offline downloads are disabled, so the storage layer is a no-op.
 */
actual class StorageManager {
	actual fun getDownloadPath(songId: String, extension: String): String = ""
	actual fun deleteFile(path: String): Boolean = false
	actual fun getFileSize(path: String): Long = 0L
	actual suspend fun saveFile(path: String, channel: ByteReadChannel) {}
	actual fun clearDownloads() {}
	actual fun audioStoreDir(): String? = null
}

internal actual fun freeSpace(dir: String): Long? = null
