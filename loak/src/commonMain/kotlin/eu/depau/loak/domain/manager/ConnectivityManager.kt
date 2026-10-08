package eu.depau.loak.domain.manager

import kotlinx.coroutines.flow.StateFlow

expect class ConnectivityManager {
	val isCellular: StateFlow<Boolean>
	val isOnline: StateFlow<Boolean>
	val isRoaming: StateFlow<Boolean>
	/** True while a charger is connected; platform-specific (desktop/web: always true). */
	val isCharging: StateFlow<Boolean>
}
