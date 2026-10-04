package eu.depau.loak.di

import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import eu.depau.loak.domain.manager.NetworkStatsManager
import io.ktor.client.HttpClient
import coil3.PlatformContext as CoilPlatformContext

/**
 * Disk cache used by the image loaders.
 *
 * Returns `null` on platforms with no stable on-disk cache (web), which lets
 * Coil fall back to a memory-only cache.
 */
internal expect fun getImageDiskCache(): DiskCache?

fun initializeSingletonImageLoader(
	context: CoilPlatformContext,
	networkStatsManager: NetworkStatsManager
): ImageLoader {
	val builder = ImageLoader.Builder(context)
		.components {
			// ponytail: getStaticImageLoader's (blend background) traffic isn't counted
			add(KtorNetworkFetcherFactory(httpClient = {
				HttpClient { install(networkStatsManager.ktorPlugin) }
			}))
		}
		.crossfade(true)
	getImageDiskCache()?.let { builder.diskCache(it) }
	return builder.build()
}

/**
 * image loader which doesn't animate GIFs
 *
 * only used in `BlendBackground.kt` right now
 */
fun getStaticImageLoader(context: CoilPlatformContext): ImageLoader {
	val builder = ImageLoader.Builder(context)
		.serviceLoaderEnabled(false)
		.components {
			add(KtorNetworkFetcherFactory())
		}
		.crossfade(true)
	getImageDiskCache()?.let { builder.diskCache(it) }
	return builder.build()
}
