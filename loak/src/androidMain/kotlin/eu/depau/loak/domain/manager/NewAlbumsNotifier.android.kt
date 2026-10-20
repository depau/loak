package eu.depau.loak.domain.manager

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import eu.depau.loak.di.ResourceProvider
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.count_new_albums
import eu.depau.loak.generated.resources.count_new_album_songs
import eu.depau.loak.generated.resources.info_new_albums_tap
import eu.depau.loak.generated.resources.title_new_albums
import eu.depau.loak.util.Logger
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getPluralString
import org.koin.mp.KoinPlatform

/** Moves [intent] to the foreground and opens the Recently added list. */
const val EXTRA_OPEN_RECENTLY_ADDED = "eu.depau.loak.OPEN_RECENTLY_ADDED"

/** Posts a silent "new albums after sync" notification; opens Recently added when tapped. */
actual class NewAlbumsNotifier(private val context: android.content.Context) {
	actual suspend fun notifyNewAlbums(albumCount: Int, songCount: Int) {
		if (albumCount <= 0) return
		val manager = NotificationManagerCompat.from(context)
		if (!manager.areNotificationsEnabled()) return
		try {
			manager.createNotificationChannel(
				NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
					.setName(getString(Res.string.title_new_albums))
					.setSound(null, null) // silent: the user asked for it, not the OS
					.build()
			)
			val open = context.packageManager
				.getLaunchIntentForPackage(context.packageName)
				?.apply {
					addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
					putExtra(EXTRA_OPEN_RECENTLY_ADDED, true)
				}
				?.let { PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE) }
			manager.notify(
				NOTIFICATION_ID,
				NotificationCompat.Builder(context, CHANNEL)
					.setSmallIcon(KoinPlatform.getKoin().get<ResourceProvider>().icLoak)
					.setContentTitle(
						getPluralString(Res.plurals.count_new_albums, albumCount, albumCount)
					)
					.setContentText(
						getPluralString(Res.plurals.count_new_album_songs, songCount, songCount)
							+ " · " + getString(Res.string.info_new_albums_tap)
					)
					.setStyle(NotificationCompat.BigTextStyle().bigText(
						getString(Res.string.info_new_albums_tap)
					))
					.setContentIntent(open)
					.setAutoCancel(true)
					.setSilent(true)
					.setOnlyAlertOnce(true)
					.build()
			)
		} catch (e: Exception) {
			// must never crash the sync cycle
			Logger.w(TAG, "can't show new-albums notification", e)
		}
	}

	private companion object {
		const val TAG = "NewAlbumsNotifier"
		const val CHANNEL = "new_albums"
		const val NOTIFICATION_ID = 0x11AD
	}
}
