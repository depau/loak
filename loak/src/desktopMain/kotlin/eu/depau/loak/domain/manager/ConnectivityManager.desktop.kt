package eu.depau.loak.domain.manager

import java.net.NetworkInterface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Desktop: JVM has no cellular connection, so isCellular is always false
 * (desktop is effectively WiFi/Ethernet). isOnline is always true; a real
 * reachability check is a DESIGN_CHANGES backlog item.
 */
actual class ConnectivityManager(
	private val preferenceManager: PreferenceManager
) {
	actual val isCellular: StateFlow<Boolean> = MutableStateFlow(false)
	actual val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}
