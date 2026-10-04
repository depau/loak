package eu.depau.loak.di

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.size.pxOrElse
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.util.IoDispatcher
import kotlinx.coroutines.withContext

const val COVER_ART_SMALL = 300
const val COVER_ART_MEDIUM = 600

/** The largest size, capped by the CoverArtQuality setting. */
const val COVER_ART_FULL = Int.MAX_VALUE

/**
 * Coil model for a Subsonic cover art: [CoverArtInterceptor] turns it into a `getCoverArt` URL
 * at a size bucket and keys the caches by `<id>@<bucket>`.
 *
 * @param size wanted pixels, or null for the size it's displayed at
 */
data class CoverArtId(val id: String, val size: Int? = null)

/** The bucket to request for art wanted at [px] (null: unknown), capped by the [full] setting. */
fun coverArtBucket(px: Int?, full: Int): Int = when {
	px == null -> full
	px <= COVER_ART_SMALL -> COVER_ART_SMALL
	px <= COVER_ART_MEDIUM -> COVER_ART_MEDIUM
	else -> full
}.coerceAtMost(full)

class CoverArtInterceptor(
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager
) : Interceptor {
	override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
		val art = chain.request.data as? CoverArtId ?: return chain.proceed()
		val full = preferenceManager.coverArtQuality.value
		val shown = chain.size.let { maxOf(it.width.pxOrElse { 0 }, it.height.pxOrElse { 0 }) }
		val wanted = coverArtBucket(art.size ?: shown.takeIf { it > 0 }, full)
		val buckets = listOf(COVER_ART_SMALL, COVER_ART_MEDIUM, full)
			.map { it.coerceAtMost(full) }.distinct()
		fun key(size: Int) = "${art.id}@$size"
		fun load(key: String, size: Int) = chain.withRequest(
			chain.request.newBuilder()
				.data(sessionManager.getCoverArtUrl(art.id, size))
				.memoryCacheKey(key)
				.diskCacheKey(key)
				.build()
		)

		// a cached bigger copy beats a download; the pre-bucket id-only key counts as full
		val larger = buckets.filter { it >= wanted }.map { key(it) to it } + (art.id to full)
		val (hit, hitSize) = larger.firstOrNull { isCached(it.first) } ?: (key(wanted) to wanted)
		val result = load(hit, hitSize).proceed()
		if (result !is ErrorResult) return result

		// offline or server error: settle for a smaller cached copy
		val smaller = buckets.filter { it < wanted }.lastOrNull { isCached(key(it)) }
			?: return result
		return load(key(smaller), smaller).proceed()
	}

	private suspend fun isCached(key: String) = withContext(IoDispatcher) {
		getImageDiskCache()?.openSnapshot(key)?.also { it.close() } != null
	}
}
