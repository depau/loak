package eu.depau.loak.domain.manager

import android.content.Context
import java.io.File

actual class StorageManager(
	private val context: Context
) {
	// not cacheDir: the system would purge downloads too; the store evicts by itself
	actual fun audioStoreDir(): String? = File(context.filesDir, "audio").absolutePath
}

internal actual fun freeSpace(dir: String): Long? = File(dir).usableSpace.takeIf { it > 0 }
