package eu.depau.loak.domain.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Web: the browser tab is always "online" as far as the app layers care
 * (offline features are gated off on web). Could be tied to `navigator.onLine`
 * later, but OfflineMode handling lives in the Android/iOS actuals.
 */
actual class ConnectivityManager(
	private val preferenceManager: PreferenceManager
) {
	actual val isCellular: StateFlow<Boolean> = MutableStateFlow(false)
	actual val isOnline: StateFlow<Boolean> = MutableStateFlow(true)
}
