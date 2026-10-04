package eu.depau.loak.domain.manager

/** Web: no file system, so no audio store and no downloads. */
actual class StorageManager {
	actual fun audioStoreDir(): String? = null
}

internal actual fun freeSpace(dir: String): Long? = null
