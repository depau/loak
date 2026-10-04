package eu.depau.loak.exoplayer

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import eu.depau.loak.data.database.entities.TransferCategory
import eu.depau.loak.domain.manager.AudioFetcher
import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.canReplace
import eu.depau.loak.domain.models.AudioQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

private const val SCHEME = "loak"

/** A song to play through [StoreDataSource]; no URL yet, so no stale quality or auth token. */
fun songUri(id: String, suffix: String?): Uri = Uri.Builder().scheme(SCHEME).authority("song")
	.appendPath(id)
	.apply { if (!suffix.isNullOrBlank()) appendQueryParameter("suffix", suffix) }
	.build()

/** The song id of a [songUri], null for anything else (radio, downloaded files). */
val Uri.songId: String? get() = if (scheme == SCHEME) lastPathSegment else null

/** Stream URL and quality to fetch a song at, per the current network and settings. */
typealias StreamSource = (songId: String) -> Pair<Uri, AudioQuality>

/** Starts or joins fetching the [songUri] [uri] into the store; null if the store won't. */
suspend fun AudioFetcher.fetchSong(uri: Uri, stream: StreamSource): AudioFetcher.Fetch? {
	val id = uri.songId ?: return null
	val (url, wanted) = stream(id)
	// the format names the file; the original keeps the song's own suffix
	val extension = wanted.format?.takeIf { it != "default" }
		?: uri.getQueryParameter("suffix") ?: "bin"
	return fetch(id, wanted, extension) { url.toString() }
}

/**
 * Plays [songUri]s from the [AudioStore]: a complete file if it's good enough, else the file a
 * fetch is writing, read as it grows; it streams only when the store won't take the song. Other
 * URIs (radio, downloads) go straight to [upstream]. [networkReads] counts open direct streams.
 */
@OptIn(UnstableApi::class)
class StoreDataSource(
	private val upstream: DataSource,
	private val store: AudioStore,
	private val fetcher: AudioFetcher,
	private val stats: NetworkStatsManager,
	private val connectivity: ConnectivityManager,
	private val stream: StreamSource,
	private val networkReads: MutableStateFlow<Int>
) : DataSource {
	private var uri: Uri? = null
	private var usingUpstream = false
	private var countedRead = false
	private var file: RandomAccessFile? = null
	private var fetch: AudioFetcher.Fetch? = null
	private var position = 0L
	private var remaining = 0L

	/** Bytes read from a complete file and requests (0 or 1), reported as a cache hit. */
	private var hit: Pair<Long, Long>? = null

	override fun addTransferListener(transferListener: TransferListener) =
		upstream.addTransferListener(transferListener)

	override fun open(dataSpec: DataSpec): Long {
		uri = dataSpec.uri
		val songId = dataSpec.uri.songId ?: return openUpstream(dataSpec)
		return blocking { openSong(dataSpec, songId) }
			?: openUpstream(dataSpec.withUri(stream(songId).first))
	}

	private suspend fun openSong(spec: DataSpec, songId: String): Long? {
		val wanted = stream(songId).second
		val online = connectivity.isOnline.value
		val entry = store.bestComplete(songId)
			?.takeIf { AudioQuality.parse(it.quality).canReplace(wanted, online) }
		if (entry != null) {
			// null: evicted meanwhile
			openFile(spec, store.pathOf(entry), entry.bytes)?.let {
				store.touch(entry)
				hit = 0L to if (spec.position == 0L) 1L else 0L
				return it
			}
		}
		if (!online) return null
		val fetch = fetcher.fetchSong(spec.uri, stream) ?: return null
		val progress = fetch.progress.first { it.started || it.done || it.failed }
		if (progress.failed) throw IOException("fetching $songId failed")
		// null: done and renamed meanwhile, now a complete entry
		return openFile(spec, fetch.path, progress.total)?.also { this.fetch = fetch }
			?: openSong(spec, songId)
	}

	private fun openFile(spec: DataSpec, path: String, size: Long?): Long? {
		val file = try {
			RandomAccessFile(path, "r")
		} catch (_: FileNotFoundException) {
			return null
		}
		if (size != null && spec.position > size) {
			file.close()
			throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
		}
		file.seek(spec.position)
		this.file = file
		position = spec.position
		remaining = when {
			spec.length != C.LENGTH_UNSET.toLong() -> spec.length
			size != null -> size - spec.position
			else -> C.LENGTH_UNSET.toLong()
		}
		return remaining
	}

	private fun openUpstream(spec: DataSpec): Long {
		usingUpstream = true
		if (spec.uri.scheme?.startsWith("http") == true) {
			countedRead = true
			networkReads.update { it + 1 }
		}
		return upstream.open(spec)
	}

	override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
		if (usingUpstream) return upstream.read(buffer, offset, length)
		if (length == 0) return 0
		if (remaining == 0L) return C.RESULT_END_OF_INPUT
		var want = if (remaining == C.LENGTH_UNSET.toLong()) length.toLong()
		else minOf(length.toLong(), remaining)
		fetch?.let { fetch ->
			// wait for the fetch to get past the read position
			val p = blocking {
				fetch.progress.first { it.bytes > position || it.done || it.failed }
			}
			if (p.bytes <= position) {
				if (p.done) return C.RESULT_END_OF_INPUT
				throw IOException("fetching ${fetch.songId} failed")
			}
			want = minOf(want, p.bytes - position)
		}
		val n = file!!.read(buffer, offset, want.toInt())
		if (n == -1) return C.RESULT_END_OF_INPUT
		position += n
		if (remaining != C.LENGTH_UNSET.toLong()) remaining -= n
		hit = hit?.let { (bytes, requests) -> bytes + n to requests }
		return n
	}

	override fun getUri(): Uri? = if (usingUpstream) upstream.uri else uri

	override fun getResponseHeaders(): Map<String, List<String>> =
		if (usingUpstream) upstream.responseHeaders else emptyMap()

	override fun close() {
		try {
			if (usingUpstream) upstream.close() else file?.close()
		} finally {
			if (countedRead) networkReads.update { it - 1 }
			hit?.let { (bytes, requests) ->
				stats.record(TransferCategory.CACHE_HIT, bytes, requests)
			}
			uri = null
			usingUpstream = false
			countedRead = false
			file = null
			fetch = null
			hit = null
		}
	}

	// Media3 interrupts the loading thread to cancel a load
	private fun <T> blocking(block: suspend () -> T): T = try {
		runBlocking { block() }
	} catch (_: InterruptedException) {
		throw InterruptedIOException()
	}
}
