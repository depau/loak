package eu.depau.loak.domain.manager

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.di.ResourceProvider
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.info_downloading_count
import eu.depau.loak.generated.resources.title_downloads
import eu.depau.loak.util.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.koin.mp.KoinPlatform
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps the process around while downloads are queued and network rules allow them, and starts
 * it again if it died meanwhile; the [DownloadManager] does the downloading, in process. Runs as
 * a foreground service showing the progress, when Android lets it start one.
 */
class DownloadWorker(context: Context, params: WorkerParameters) :
	CoroutineWorker(context, params) {
	override suspend fun doWork(): Result {
		val downloads = KoinPlatform.getKoin().get<DownloadManager>()
		downloads.retryFailed()
		val failed = coroutineScope {
			val notification = launch { showProgress(downloads) }
			downloads.awaitIdle().also { notification.cancel() }
		}
		return if (failed) Result.retry() else Result.success()
	}

	/** Keeps the foreground notification up to date, at most once a second. */
	private suspend fun showProgress(downloads: DownloadManager) {
		val songs = KoinPlatform.getKoin().get<SongDao>()
		// this run's songs, for "n of total"; cancelled ones leave it
		val batch = mutableSetOf<String>()
		var foreground = true
		combine(downloads.allDownloads, downloads.active, ::Pair).conflate()
			.collect { (all, active) ->
				if (!foreground) return@collect
				val (done, pending) = all.partition { it.status == DownloadStatus.DOWNLOADED }
				val doneIds = done.mapTo(HashSet()) { it.songId }
				val pendingIds = pending.mapTo(HashSet()) { it.songId }
				batch.retainAll { it in doneIds || it in pendingIds }
				batch += pendingIds
				val finished = batch.count { it in doneIds }
				if (finished == batch.size) return@collect
				val fraction = (finished + pending.sumOf { it.progress.toDouble() }) / batch.size
				val title = active.firstOrNull()?.let { songs.getSongById(it)?.title }
				try {
					setForeground(foregroundInfo(finished + 1, batch.size, fraction, title))
				} catch (e: Exception) {
					if (e is CancellationException) throw e
					// e.g. started in the background, or Android 15's daily dataSync budget is spent
					Logger.w(TAG, "can't run downloads in the foreground", e)
					foreground = false
					return@collect
				}
				delay(1.seconds)
			}
	}

	private suspend fun foregroundInfo(
		current: Int,
		total: Int,
		fraction: Double,
		title: String?
	): ForegroundInfo {
		val context = applicationContext
		NotificationManagerCompat.from(context).createNotificationChannel(
			NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_LOW)
				.setName(getString(Res.string.title_downloads))
				.build()
		)
		val cancel = PendingIntent.getBroadcast(
			context, 0, Intent(context, CancelReceiver::class.java),
			PendingIntent.FLAG_IMMUTABLE
		)
		val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
			?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
		val notification = NotificationCompat.Builder(context, CHANNEL)
			.setSmallIcon(KoinPlatform.getKoin().get<ResourceProvider>().icLoak)
			.setContentTitle(getString(Res.string.info_downloading_count, current, total))
			.setContentText(title)
			.setProgress(PROGRESS_MAX, (fraction * PROGRESS_MAX).toInt(), false)
			.setContentIntent(open)
			.addAction(0, getString(Res.string.action_cancel), cancel)
			.setOngoing(true)
			.setSilent(true)
			.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
			.build()
		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			ForegroundInfo(
				NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
			)
		} else {
			ForegroundInfo(NOTIFICATION_ID, notification)
		}
	}

	/** The notification's Cancel: drops every queued download, which ends this worker. */
	class CancelReceiver : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			KoinPlatform.getKoin().get<DownloadManager>().cancelAllActiveDownloads()
		}
	}

	private companion object {
		const val TAG = "DownloadWorker"
		const val CHANNEL = "downloads"
		const val NOTIFICATION_ID = 0x10AD
		const val PROGRESS_MAX = 1000
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
