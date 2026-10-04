package eu.depau.loak.di

import coil3.disk.DiskCache
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

private var sharedImageDiskCache: DiskCache? = null

// Caches, not tmp: purgeable under storage pressure but not wiped as eagerly
private fun cachesDirectory() =
	(NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
		.firstOrNull() as? String)?.toPath() ?: FileSystem.SYSTEM_TEMPORARY_DIRECTORY

internal actual fun getImageDiskCache(): DiskCache? {
	return sharedImageDiskCache ?: DiskCache.Builder()
		.directory(cachesDirectory() / "image_cache")
		.maxSizeBytes(2L shl 30)
		.build().also { sharedImageDiskCache = it }
}
