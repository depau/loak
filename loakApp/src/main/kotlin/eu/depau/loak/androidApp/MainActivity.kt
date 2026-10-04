package eu.depau.loak.androidApp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import eu.depau.loak.App
import eu.depau.loak.domain.manager.DownloadManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get

class MainActivity : ComponentActivity() {
	private val askNotifications =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		enableEdgeToEdge()
		setContent { App() }
		// for the download progress notification; downloads work without it
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) lifecycleScope.launch {
			get<DownloadManager>().queued.first { it }
			val permission = Manifest.permission.POST_NOTIFICATIONS
			if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
				askNotifications.launch(permission)
			}
		}
	}
}
