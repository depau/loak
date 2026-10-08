package eu.depau.loak.domain.manager

import androidx.compose.runtime.snapshotFlow
import eu.depau.loak.domain.models.settings.OfflineMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Web: the browser tab is "online" as far as the app layers care, unless offline mode is
 * [OfflineMode.Forced]. Could be tied to `navigator.onLine` later.
 */
actual class ConnectivityManager(
	private val preferenceManager: PreferenceManager
) {
	actual val isCellular: StateFlow<Boolean> = MutableStateFlow(false)
	actual val isRoaming: StateFlow<Boolean> = MutableStateFlow(false)
	// a browser has no battery API we depend on: never gates downloads
	actual val isCharging: StateFlow<Boolean> = MutableStateFlow(true)
	actual val isOnline: StateFlow<Boolean> =
		snapshotFlow { preferenceManager.offlineMode != OfflineMode.Forced }
			.stateIn(
				CoroutineScope(Dispatchers.Default + SupervisorJob()),
				SharingStarted.Eagerly,
				preferenceManager.offlineMode != OfflineMode.Forced
			)
}
