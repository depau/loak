@file:OptIn(ExperimentalForeignApi::class)

package eu.depau.loak.domain.manager

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

actual class StorageManager {
	/** Application Support (not Documents): app data, kept out of iCloud backups. */
	actual fun audioStoreDir(): String? {
		val manager = NSFileManager.defaultManager
		val base = manager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
			.first() as NSURL
		val dir = base.URLByAppendingPathComponent("audio")!!
		manager.createDirectoryAtURL(dir, true, null, null)
		dir.setResourceValue(true, NSURLIsExcludedFromBackupKey, null)
		return dir.path
	}
}

internal actual fun freeSpace(dir: String): Long? =
	(NSFileManager.defaultManager.attributesOfFileSystemForPath(dir, null)
		?.get(NSFileSystemFreeSize) as? NSNumber)?.longLongValue
