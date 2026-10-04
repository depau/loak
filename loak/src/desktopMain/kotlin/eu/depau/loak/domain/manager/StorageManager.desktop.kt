package eu.depau.loak.domain.manager

import eu.depau.loak.di.desktopDataDir
import java.io.File

actual class StorageManager {
	actual fun audioStoreDir(): String? = File(desktopDataDir, "audio").absolutePath
}

internal actual fun freeSpace(dir: String): Long? = File(dir).usableSpace.takeIf { it > 0 }
