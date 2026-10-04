package eu.depau.loak.exoplayer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import eu.depau.loak.di.COVER_ART_MEDIUM
import eu.depau.loak.di.CoverArtId

/**
 * Loads Subsonic cover art (artwork URIs carrying an `id`) through Coil, so its disk cache and
 * size buckets are shared with the UI; anything else goes to ExoPlayer's default loader.
 */
@UnstableApi
class ExoPlayerCoilBitmapLoader(
	private val context: Context,
	private val imageLoader: ImageLoader,
): BitmapLoader {
	private val bitmapLoader = DataSourceBitmapLoader.Builder(context).build()

	override fun supportsMimeType(mimeType: String): Boolean {
		return bitmapLoader.supportsMimeType(mimeType)
	}

	override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
		return bitmapLoader.decodeBitmap(data)
	}

	override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
		val coverId = uri.getQueryParameter("id") ?: return bitmapLoader.loadBitmap(uri)
		val future = SettableFuture.create<Bitmap>()
		imageLoader.enqueue(
			ImageRequest.Builder(context)
				.data(CoverArtId(coverId, COVER_ART_MEDIUM))
				.allowHardware(false)
				.listener(
					onSuccess = { _, result -> future.set(result.image.toBitmap()) },
					onError = { _, result -> future.setException(result.throwable) }
				)
				.build()
		)
		return future
	}
}
