package eu.depau.loak.di

import coil3.disk.DiskCache
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/**
 * Desktop: a real on-disk image cache in the system temp dir, like Android.
 */
private var sharedImageDiskCache: DiskCache? = null

internal actual fun getImageDiskCache(): DiskCache? {
	return sharedImageDiskCache ?: DiskCache.Builder()
		.directory(File(System.getProperty("java.io.tmpdir"), "loak_image_cache").toPath().toOkioPath())
		.maxSizeBytes(2L shl 30)
		.build().also { sharedImageDiskCache = it }
}
