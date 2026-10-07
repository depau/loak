package eu.depau.loak.domain.manager

import android.annotation.SuppressLint
import android.content.Context
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import eu.depau.loak.domain.models.settings.OfflineMode
import android.net.ConnectivityManager as AndroidConnectivityManager

private data class NetworkStatus(
	val isOnline: Boolean = false,
	val isCellular: Boolean = false,
	val isRoaming: Boolean = false
) {
	companion object {
		fun fromCaps(
			caps: NetworkCapabilities
		) = NetworkStatus(
			isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
				&& caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
			// "cellular" here means "pay for every byte": metered WiFi hotspots count too
			isCellular = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
			// reading link properties' roaming flag needs no permission on current Android
			isRoaming = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING).not()
		)
	}
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalCoroutinesApi::class)
actual class ConnectivityManager(
	context: Context,
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val dispatcher = Dispatchers.IO
	// Eagerly: stream/download URL builders read `.value` without collecting, so a lazily
	// started flow would stay at its initial value forever.
	private val started = SharingStarted.Eagerly
	private val connectivityManager =
		context.getSystemService(Context.CONNECTIVITY_SERVICE) as AndroidConnectivityManager

	private val networkStatus = callbackFlow {
		val callback = object : AndroidConnectivityManager.NetworkCallback() {
			override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
				super.onCapabilitiesChanged(network, caps)
				trySend(NetworkStatus.fromCaps(caps))
			}

			override fun onLost(network: Network) {
				super.onLost(network)
				trySend(NetworkStatus())
			}
		}

		// the default network is the one traffic actually goes through; a generic request
		// would also report background networks (e.g. cellular while on WiFi)
		connectivityManager.registerDefaultNetworkCallback(callback)

		trySend(
			connectivityManager
				.getNetworkCapabilities(connectivityManager.activeNetwork)
				?.let { NetworkStatus.fromCaps(it) }
				?: NetworkStatus()
		)

		awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
	}
		.flowOn(dispatcher)
		.conflate()
		.stateIn(scope, started, NetworkStatus())

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
