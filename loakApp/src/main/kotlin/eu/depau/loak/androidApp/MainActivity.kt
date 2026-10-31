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
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import eu.depau.loak.App
import eu.depau.loak.di.LocalBackToCar
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.EXTRA_OPEN_RECENTLY_ADDED
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
	private val askNotifications =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

	/** A new-albums notification tap waiting to open Recently added, once past login. */
	private val openRecentlyAdded = mutableStateOf(false)

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		if (savedInstanceState == null) takeRecentlyAdded()
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
					// on Settings, as its tab: back returns to the car. Signed out, the login
					// screen stays (the car's Log in button opens it this way too)
					LaunchedEffect(Unit) {
						if (fromCarSettings && actions.inApp) actions.settingsTab()
					}
					// the new-albums notification opens Recently added directly
					// (re)keyed: on cold start the session isn't restored yet at first composition
					LaunchedEffect(openRecentlyAdded.value, actions.inApp) {
						if (openRecentlyAdded.value && actions.inApp) {
							openRecentlyAdded.value = false
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

	/** Consumes the new-albums notification's extra, so recreation doesn't reopen it. */
	private fun takeRecentlyAdded() {
		if (intent?.getBooleanExtra(EXTRA_OPEN_RECENTLY_ADDED, false) != true) return
		intent.removeExtra(EXTRA_OPEN_RECENTLY_ADDED)
		openRecentlyAdded.value = true
	}

	override fun onNewIntent(intent: Intent) {
		super.onNewIntent(intent)
		setIntent(intent)
		takeRecentlyAdded()
	}
}
