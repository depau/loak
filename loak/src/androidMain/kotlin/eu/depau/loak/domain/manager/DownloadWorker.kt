package eu.depau.loak.domain.manager

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import java.util.concurrent.TimeUnit

/**
 * Keeps the process around while downloads are queued and network rules allow them, and starts
 * it again if it died meanwhile; the [DownloadManager] does the downloading, in process.
 */
class DownloadWorker(context: Context, params: WorkerParameters) :
	CoroutineWorker(context, params) {
	override suspend fun doWork(): Result {
		val downloads = KoinPlatform.getKoin().get<DownloadManager>()
		downloads.retryFailed()
		return if (downloads.awaitIdle()) Result.retry() else Result.success()
	}
}

/** Mirrors the download queue into a unique [DownloadWorker] job; call once Koin is up. */
fun scheduleDownloads(context: Context) {
	val downloads = KoinPlatform.getKoin().get<DownloadManager>()
	val workManager = WorkManager.getInstance(context)
	CoroutineScope(Dispatchers.Default).launch {
		combine(downloads.queued, downloads.overCellular, ::Pair)
			.distinctUntilChanged()
			.collect { (queued, overCellular) ->
				if (!queued) return@collect
				val network = if (overCellular) NetworkType.CONNECTED else NetworkType.UNMETERED
				val request = OneTimeWorkRequestBuilder<DownloadWorker>()
					.setConstraints(Constraints(requiredNetworkType = network))
					.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
					.build()
				// replacing only drops a waiting worker: the downloads run in DownloadManager
				workManager.enqueueUniqueWork("downloads", ExistingWorkPolicy.REPLACE, request)
			}
	}
}
