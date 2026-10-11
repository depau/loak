package eu.depau.loak.shared

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.ParcelFileDescriptor
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.CachePolicy
import coil3.request.SuccessResult
import eu.depau.loak.di.COVER_ART_MEDIUM
import eu.depau.loak.di.CoverArtId
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.FileNotFoundException

/**
 * Cover art for media browsers: Android Auto and Automotive load artwork only from content
 * URIs, never from the server's URLs (which also carry the login). Served from the app's
 * image cache, so it works offline for whatever the app has shown before.
 */
class CoverArtProvider : ContentProvider(), KoinComponent {
	private val imageLoader: ImageLoader by inject()

	override fun onCreate(): Boolean {
		authority = "${context!!.packageName}.coverart"
		return true
	}

	override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
		val id = uri.lastPathSegment ?: throw FileNotFoundException(uri.toString())
		val file = runBlocking {
			val result = imageLoader.execute(
				ImageRequest.Builder(context!!).data(CoverArtId(id, COVER_ART_MEDIUM))
					// a memory hit may not say where the file is
					.memoryCachePolicy(CachePolicy.WRITE_ONLY)
					.build()
			)
			val key = (result as? SuccessResult)?.diskCacheKey
			key?.let { imageLoader.diskCache?.openSnapshot(it)?.use { snapshot -> snapshot.data.toFile() } }
		} ?: throw FileNotFoundException(uri.toString())
		return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
	}

	override fun getType(uri: Uri) = "image/*"
	override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?) = null
	override fun insert(uri: Uri, values: ContentValues?) = null
	override fun delete(uri: Uri, s: String?, a: Array<String>?) = 0
	override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<String>?) = 0

	companion object {
		private var authority = ""

		/** The content URI media browsers load [coverArtId]'s art from. */
		fun uri(coverArtId: String): Uri = Uri.Builder()
			.scheme("content").authority(authority).appendPath(coverArtId).build()
	}
}
