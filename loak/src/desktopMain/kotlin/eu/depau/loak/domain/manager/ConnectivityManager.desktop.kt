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
 * Desktop: JVM has no cellular connection, so isCellular is always false
 * (desktop is effectively WiFi/Ethernet). isOnline is true unless offline mode is
 * [OfflineMode.Forced]; a real reachability check is a DESIGN_CHANGES backlog item.
 */
actual class ConnectivityManager(
	private val preferenceManager: PreferenceManager
) {
	actual val isCellular: StateFlow<Boolean> = MutableStateFlow(false)
	actual val isRoaming: StateFlow<Boolean> = MutableStateFlow(false)
	// a desktop has a charger by definition: never gates downloads
	actual val isCharging: StateFlow<Boolean> = MutableStateFlow(true)
	actual val isOnline: StateFlow<Boolean> =
		snapshotFlow { preferenceManager.offlineMode != OfflineMode.Forced }
			.stateIn(
				CoroutineScope(Dispatchers.Default + SupervisorJob()),
				SharingStarted.Eagerly,
				preferenceManager.offlineMode != OfflineMode.Forced
			)
}
