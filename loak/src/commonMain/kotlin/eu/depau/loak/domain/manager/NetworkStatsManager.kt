package eu.depau.loak.domain.manager

import eu.depau.loak.data.database.dao.NetworkStatsDao
import eu.depau.loak.data.database.entities.SyncRunEntity
import eu.depau.loak.data.database.entities.TransferCategory
import eu.depau.loak.di.addTransferBreadcrumb
import eu.depau.loak.di.finishSentrySpan
import eu.depau.loak.di.startSentrySpan
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import io.ktor.client.call.replaceResponse
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import io.sentry.kotlin.multiplatform.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicLongArray
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.time.TimeSource

/** Overrides the URL-based [TransferCategory] of a request. */
val TransferCategoryKey = AttributeKey<TransferCategory>("TransferCategory")

fun HttpRequestBuilder.transferCategory(category: TransferCategory) =
	attributes.put(TransferCategoryKey, category)

val STATS_RETENTION = 14.days

/** Start of the hour [time] falls in, in epoch millis. */
fun hourOf(time: Instant): Long =
	time.toEpochMilliseconds().let { it - it % 1.hours.inWholeMilliseconds }

/**
 * Counts received bytes per network kind and category in memory, and adds them to hourly
 * rows at most once a minute, only after traffic happened: no timers while idle, no DB
 * write per chunk. Also keeps the history of full library pulls.
 */
@OptIn(ExperimentalAtomicApi::class)
class NetworkStatsManager(
	private val dao: NetworkStatsDao,
	private val connectivityManager: ConnectivityManager
) {
	private val scope = CoroutineScope(SupervisorJob() + IoDispatcher)
	private val categories = TransferCategory.entries

	// index: metered * categories.size + category.ordinal
	private val pendingBytes = AtomicLongArray(2 * categories.size)
	private val pendingRequests = AtomicLongArray(2 * categories.size)
	private val flushScheduled = AtomicBoolean(false)

	/** Install into every Ktor client whose traffic should be counted. */
	val ktorPlugin = createClientPlugin("TransferStats") {
		client.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
			proceedWith(counted(response))
		}
	}

	fun record(category: TransferCategory, bytes: Long = 0, requests: Long = 0) {
		val metered = connectivityManager.isCellular.value
		val i = (if (metered) categories.size else 0) + category.ordinal
		if (bytes != 0L) pendingBytes.addAndFetchAt(i, bytes)
		if (requests != 0L) pendingRequests.addAndFetchAt(i, requests)
		// ponytail: counts still pending when the process dies (< 1 min) are lost
		if (flushScheduled.compareAndSet(false, true)) {
			scope.launch {
				delay(1.minutes)
				flush()
			}
		}
	}

	suspend fun flush() {
		flushScheduled.store(false)
		val now = Clock.System.now()
		val hour = hourOf(now)
		try {
			for (i in 0 until pendingBytes.size) {
				val bytes = pendingBytes.exchangeAt(i, 0)
				val requests = pendingRequests.exchangeAt(i, 0)
				if (bytes == 0L && requests == 0L) continue
				val category = categories[i % categories.size]
				dao.add(hour, i >= categories.size, category, bytes, requests)
			}
			dao.pruneTransfers(hourOf(now - STATS_RETENTION))
		} catch (e: Exception) {
			Logger.e("NetworkStatsManager", "couldn't save transfer stats", e)
		}
	}

	suspend fun recordSyncRun(start: Instant, ok: Boolean, items: Int) {
		val now = Clock.System.now()
		try {
			dao.insertSyncRun(
				SyncRunEntity(start = start, duration = now - start, ok = ok, items = items)
			)
			dao.pruneSyncRuns(now - STATS_RETENTION)
		} catch (e: Exception) {
			Logger.e("NetworkStatsManager", "couldn't save sync run", e)
		}
	}

	/** One audio transfer: counted as it goes, reported to Sentry (span, breadcrumb) at the end. */
	inner class AudioTransfer(private val category: TransferCategory, private val songId: String?) {
		private val started = TimeSource.Monotonic.markNow()
		private val span = if (Sentry.isEnabled()) {
			startSentrySpan(null, "transfer.${category.name.lowercase()}", "Audio transfer")
		} else null
		private var bytes = 0L

		init {
			record(category, requests = 1)
		}

		fun add(count: Long) {
			bytes += count
			record(category, bytes = count)
		}

		fun end() {
			// song id only: never the URL (it carries the server address and auth token)
			val data = buildMap<String, Any> {
				put("source", category.name.lowercase())
				put("bytes", bytes)
				put("duration_ms", started.elapsedNow().inWholeMilliseconds)
				put("metered", connectivityManager.isCellular.value)
				songId?.let { put("song_id", it) }
			}
			addTransferBreadcrumb(data)
			span?.let { finishSentrySpan(it, true, data) }
		}
	}

	/**
	 * Re-wraps the response body to count every chunk. The copy reads ahead up to Ktor's channel
	 * buffer (~1 MB), so streams the player may abandon midway are counted by the player instead.
	 */
	@OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
	private fun counted(response: HttpResponse): HttpResponse {
		val request = response.call.request
		val category = request.attributes.getOrNull(TransferCategoryKey)
			?: categoryOf(request.url).takeUnless {
				// playback (incl. internet radio), counted by the player
				it == TransferCategory.STREAM
					|| response.contentType()?.match(ContentType.Audio.Any) == true
			}
			?: return response
		val transfer = if (category == TransferCategory.DOWNLOAD) {
			AudioTransfer(category, request.url.parameters["id"])
		} else {
			record(category, requests = 1)
			null
		}

		// lazily, like Ktor's own onDownload: the call may ask for the body more than once
		return response.call.replaceResponse {
			val body = rawContent
			GlobalScope.writer(response.coroutineContext) {
				try {
					val buffer = ByteArray(8192)
					while (true) {
						val n = body.readAvailable(buffer)
						if (n == -1) break
						channel.writeFully(buffer, 0, n)
						transfer?.add(n.toLong()) ?: record(category, bytes = n.toLong())
					}
				} finally {
					body.cancel(null)
					transfer?.end()
				}
			}.channel
		}.response
	}

	private fun categoryOf(url: Url): TransferCategory =
		when (url.segments.lastOrNull()?.removeSuffix(".view")) {
			"stream" -> TransferCategory.STREAM
			"download" -> TransferCategory.DOWNLOAD
			"getCoverArt" -> TransferCategory.IMAGE
			else -> TransferCategory.API
		}
}
