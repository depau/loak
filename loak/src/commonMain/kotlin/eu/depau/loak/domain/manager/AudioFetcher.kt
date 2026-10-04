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

	/** Whether any song is being fetched, i.e. the network is in use. */
	val busy: Flow<Boolean> = active.map { it.isNotEmpty() }.distinctUntilChanged()

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
		internal val writer: AudioStore.Writer
	) {
		val path = writer.path
		val progress = MutableStateFlow(Progress(writer.offset))
		internal lateinit var job: Job
	}

	/**
	 * Fetches [songId] at [quality] from [url], or joins the song's running fetch. Null when the
	 * store won't take it (none, caching off, already complete): stream it instead.
	 */
	suspend fun fetch(
		songId: String,
		quality: AudioQuality,
		extension: String,
		url: () -> String
	): Fetch? = mutex.withLock {
		active.value[songId]?.let { return it }
		val writer = store.openWrite(songId, quality, extension, resume = true) ?: return null
		val fetch = Fetch(songId, quality, writer)
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
	fun cancelExcept(keep: Set<String>) =
		active.value.values.filter { it.songId !in keep }.forEach { it.job.cancel() }

	private suspend fun run(fetch: Fetch, url: String) {
		val writer = fetch.writer
		try {
			traced("audio.fetch", "Fetch audio") { data ->
				data["song_id"] = fetch.songId
				data["quality"] = fetch.quality.key
				data["metered"] = connectivityManager.isCellular.value
				data["resumed_from"] = writer.offset
				stats.record(TransferCategory.STREAM, requests = 1)
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
							stats.record(TransferCategory.STREAM, bytes = n.toLong())
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
