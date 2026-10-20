package eu.depau.loak.androidApp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.lifecycleScope
import eu.depau.loak.App
import eu.depau.loak.di.LocalBackToCar
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.EXTRA_OPEN_RECENTLY_ADDED
import eu.depau.loak.ui.navigation.AppActions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
	private val askNotifications =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

	/** The latest AppActions, for the notification tap to navigate while the app is running. */
	private var appActions: AppActions? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		// the settings button of Android Automotive's media app, the way to this UI in a car
		val fromCarSettings = savedInstanceState == null &&
			intent?.action == Intent.ACTION_APPLICATION_PREFERENCES
		val backToCar = if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
			{
				openCarMediaApp()
				finish()
			}
		} else {
			null
		}
		setContent {
			CompositionLocalProvider(LocalBackToCar provides backToCar) {
				App { actions ->
					appActions = actions
					// on Settings, as its tab: back returns to the car. Signed out, the login
					// screen stays (the car's Log in button opens it this way too)
					LaunchedEffect(Unit) {
						if (fromCarSettings && actions.inApp) actions.settingsTab()
					}
					// the new-albums notification opens Recently added directly
					LaunchedEffect(Unit) {
						if (intent?.getBooleanExtra(EXTRA_OPEN_RECENTLY_ADDED, false) == true &&
							actions.inApp
						) {
							setIntent(intent?.apply { removeExtra(EXTRA_OPEN_RECENTLY_ADDED) })
							actions.recentlyAdded()
						}
					}
				}
			}
		}
		// for the download progress notification; downloads work without it
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) lifecycleScope.launch {
			get<DownloadManager>().queued.first { it }
			val permission = Manifest.permission.POST_NOTIFICATIONS
			if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
				askNotifications.launch(permission)
			}
		}
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		if (intent.getBooleanExtra(EXTRA_OPEN_RECENTLY_ADDED, false)) {
			// handle once, and only after the app already runs (cold start goes through onCreate)
			setIntent(intent.apply { removeExtra(EXTRA_OPEN_RECENTLY_ADDED) })
			appActions?.takeIf { it.inApp }?.recentlyAdded()
		} else {
			setIntent(intent)
		}
	}
}
