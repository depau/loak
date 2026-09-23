package eu.depau.loak.di

import coil3.disk.DiskCache

/**
 * Web: no stable on-disk cache (IndexedDB is not exposed by Coil's disk API),
 * so Coil falls back to a memory-only cache. `getImageDiskCache()` returning
 * null is what [initializeSingletonImageLoader] already handles.
 */
internal actual fun getImageDiskCache(): DiskCache? = null
