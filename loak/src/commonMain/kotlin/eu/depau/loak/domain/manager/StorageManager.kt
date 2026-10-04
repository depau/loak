package eu.depau.loak.domain.manager


import io.ktor.utils.io.ByteReadChannel

expect class StorageManager {
	fun getDownloadPath(songId: String, extension: String): String
	fun deleteFile(path: String): Boolean
	fun getFileSize(path: String): Long
	suspend fun saveFile(path: String, channel: ByteReadChannel)
	fun clearDownloads()

	/** Where the AudioStore keeps its files; null where there is no file system. */
	fun audioStoreDir(): String?
}
