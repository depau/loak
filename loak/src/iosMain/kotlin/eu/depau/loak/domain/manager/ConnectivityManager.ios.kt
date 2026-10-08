package eu.depau.loak.domain.manager

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import eu.depau.loak.domain.models.settings.OfflineMode
import platform.Network.nw_interface_type_cellular
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_constrained
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.Network.nw_path_uses_interface_type
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceBatteryStateCharging
import platform.UIKit.UIDeviceBatteryStateFull
import platform.UIKit.UIDeviceBatteryStateDidChangeNotification
import platform.darwin.dispatch_get_main_queue

private data class NetworkStatus(
	val isOnline: Boolean = false,
	val isCellular: Boolean = false,
	val isRoaming: Boolean = false
)

/** True while the device is plugged in (charging or full). */
private fun chargingState(uid: UIDevice = UIDevice.currentDevice): Boolean =
	uid.batteryState == UIDeviceBatteryStateCharging || uid.batteryState == UIDeviceBatteryStateFull

@OptIn(ExperimentalCoroutinesApi::class)
actual class ConnectivityManager(
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val dispatcher = Dispatchers.IO
	// Eagerly: stream/download URL builders read `.value` without collecting, so a lazily
	// started flow would stay at its initial value forever.
	private val started = SharingStarted.Eagerly

	private val networkStatus = callbackFlow {
		val monitor = nw_path_monitor_create()

		nw_path_monitor_set_update_handler(monitor) { path ->
			trySend(
				NetworkStatus(
					isOnline = nw_path_get_status(path) == nw_path_status_satisfied,
					// "cellular" here means "pay for every byte": Apple's expensive flag also
					// covers personal hotspots, constrained = Low Data Mode
					isCellular = nw_path_uses_interface_type(path, nw_interface_type_cellular)
						|| nw_path_is_expensive(path)
						|| nw_path_is_constrained(path),
					isRoaming = nw_path_is_expensive(path)
				)
			)
		}
		nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
		nw_path_monitor_start(monitor)

		awaitClose { nw_path_monitor_cancel(monitor) }
	}.stateIn(scope, started, NetworkStatus())

	actual val isCellular = networkStatus
		.map { it.isCellular }
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, false)

	actual val isRoaming = networkStatus
		.map { it.isRoaming }
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, false)

	// charge state via UIDevice; enabling battery monitoring is harmless and gives us the
	// current value synchronously after start.
	actual val isCharging = callbackFlow {
		val uid = UIDevice.currentDevice
		uid.batteryMonitoringEnabled = true
		val observer = NSNotificationCenter.defaultCenter.addObserverForName(
			name = UIDeviceBatteryStateDidChangeNotification,
			`object` = uid,
			queue = NSOperationQueue.mainQueue
		) { trySend(chargingState(uid)) }
		trySend(chargingState(uid))
		awaitClose {
			NSNotificationCenter.defaultCenter.removeObserver(observer)
			uid.batteryMonitoringEnabled = false
		}
	}
		.flowOn(dispatcher)
		.distinctUntilChanged()
		.stateIn(scope, started, chargingState(UIDevice.currentDevice))

	actual val isOnline = combine(
		networkStatus,
		snapshotFlow { preferenceManager.offlineMode }
	) { status, offlineMode ->
		when (offlineMode) {
			OfflineMode.Forced -> false
			OfflineMode.NoWiFi -> status.isOnline && !status.isCellular
			else -> status.isOnline
		}
	}
		.distinctUntilChanged()
		.flowOn(dispatcher)
		.stateIn(scope, started, true)
}
