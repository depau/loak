package eu.depau.loak.domain.manager

import eu.depau.loak.data.database.entities.TransferCategory
import eu.depau.loak.di.traced
import eu.depau.loak.domain.models.AudioQuality
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.IOException

/** Whether a stored copy at this quality may play instead of streaming at [wanted]. */
fun AudioQuality.canReplace(wanted: AudioQuality, online: Boolean) = !online || this >= wanted

/** The store file extension for a song at [quality]: the format, else the song's own [suffix]. */
fun audioExtension(quality: AudioQuality, suffix: String?) =
	quality.format?.takeIf { it != "default" } ?: suffix ?: "bin"

/**
 * Songs to fetch ahead of [current]: the next queue entries, one on a metered network and three
 * otherwise. [upcoming] holds null for entries that don't stream (radio, downloaded files).
 */
fun prefetchTargets(current: String?, upcoming: List<String?>, metered: Boolean): List<String> =
	upcoming.take(if (metered) 1 else 3).filterNotNull().filter { it != current }.distinct()

/**
 * Downloads whole songs into the [AudioStore] at line rate, one request each, so the radio does
 * one burst per track and then sleeps. A player follows the file while it's written, see
 * [Fetch.progress]. One fetch per song at a time: fetching it again joins the running one.
 */
class AudioFetcher(
	private val store: AudioStore,
	private val sessionManager: SessionManager,
	private val stats: NetworkStatsManager,
	private val connectivityManager: ConnectivityManager
) {
	private val scope = CoroutineScope(SupervisorJob() + IoDispatcher)
	private val mutex = Mutex()
	private val active = MutableStateFlow(emptyMap<String, Fetch>())

	/** Whether any song is being fetched for playback (downloads aside). */
	val busy: Flow<Boolean> =
		active.map { all -> all.values.any { !it.download } }.distinctUntilChanged()

	/**
	 * [bytes] are in the file at [Fetch.path]; [total] is known once the server answered, if it
	 * sent a length (an estimate for transcodes). [done]: the file is complete in the store.
	 */
	data class Progress(
		val bytes: Long,
		val total: Long? = null,
		val started: Boolean = false,
		val done: Boolean = false,
		val failed: Boolean = false
	)

	inner class Fetch internal constructor(
		val songId: String,
		val quality: AudioQuality,
		/** A download: pinned, counted as such, and left alone by [cancelExcept]. */
		val download: Boolean,
		internal val writer: AudioStore.Writer
	) {
		val path = writer.path
		val progress = MutableStateFlow(Progress(writer.offset))
		internal lateinit var job: Job

		suspend fun cancelAndJoin() = job.cancelAndJoin()
	}

	/**
	 * Fetches [songId] at [quality] from [url], or joins the song's running fetch (whatever its
	 * quality). Null when the store won't take it (none, caching off, already complete): stream
	 * it instead. A [download] is pinned in the store.
	 */
	suspend fun fetch(
		songId: String,
		quality: AudioQuality,
		extension: String,
		download: Boolean = false,
		url: () -> String
	): Fetch? = mutex.withLock {
		active.value[songId]?.let { return it }
		val writer = store.openWrite(songId, quality, extension, pinned = download, resume = true)
			?: return null
		val fetch = Fetch(songId, quality, download, writer)
		fetch.job = scope.launch(start = CoroutineStart.LAZY) {
			try {
				run(fetch, url())
			} finally {
				active.update { if (it[songId] === fetch) it - songId else it }
			}
		}
		active.update { it + (songId to fetch) }
		fetch.job.start()
		fetch
	}

	/** Stops fetching songs not in [keep], e.g. ones that left the play queue's window. */
	fun cancelExcept(keep: Set<String>) = active.value.values
		.filter { !it.download && it.songId !in keep }.forEach { it.job.cancel() }

	private suspend fun run(fetch: Fetch, url: String) {
		val writer = fetch.writer
		val category = if (fetch.download) TransferCategory.DOWNLOAD else TransferCategory.STREAM
		try {
			traced("audio.fetch", "Fetch audio") { data ->
				data["song_id"] = fetch.songId
				data["quality"] = fetch.quality.key
				data["download"] = fetch.download
				data["metered"] = connectivityManager.isCellular.value
				data["resumed_from"] = writer.offset
				stats.record(category, requests = 1)
				var received = 0L
				try {
					sessionManager.api.httpClient.prepareGet(url) {
						if (writer.offset > 0) header(HttpHeaders.Range, "bytes=${writer.offset}-")
					}.execute { response ->
						if (!response.status.isSuccess()) {
							throw IOException("HTTP ${response.status.value}")
						}
						if (response.status != HttpStatusCode.PartialContent) writer.truncate()
						val total = response.contentLength()?.plus(writer.offset)
						fetch.progress.value = Progress(writer.offset, total, started = true)
						// read directly: Ktor's counting wrapper would read ahead (see stats)
						val body = response.bodyAsChannel()
						val buffer = ByteArray(64 * 1024)
						while (true) {
							val n = body.readAvailable(buffer)
							if (n == -1) break
							writer.write(buffer, 0, n)
							received += n
							stats.record(category, bytes = n.toLong())
							fetch.progress.update { it.copy(bytes = writer.offset) }
						}
						if (total != null && writer.offset != total) {
							throw IOException("got ${writer.offset} of $total bytes")
						}
					}
				} finally {
					data["bytes"] = received
				}
			}
		} catch (e: Throwable) {
			// the original file resumes with a Range request; a transcode is made anew each time
			withContext(NonCancellable) {
				if (fetch.quality == AudioQuality.Raw) writer.close() else writer.abandon()
			}
			fetch.progress.update { it.copy(failed = true) }
			if (e is CancellationException) throw e
			Logger.w(TAG, "fetching ${fetch.songId} failed", e)
			return
		}
		val entry = withContext(NonCancellable) { writer.complete() }
		fetch.progress.update {
			if (entry == null) it.copy(failed = true) else it.copy(bytes = entry.bytes, done = true)
		}
	}

	private companion object {
		const val TAG = "AudioFetcher"
	}
}
