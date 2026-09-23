package eu.depau.loak.di

import coil3.disk.DiskCache
import okio.FileSystem

private var sharedImageDiskCache: DiskCache? = null

internal actual fun getImageDiskCache(): DiskCache? {
	return sharedImageDiskCache ?: DiskCache.Builder()
		.directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "image_cache")
		.maxSizeBytes(2L shl 30)
		.build().also { sharedImageDiskCache = it }
}
