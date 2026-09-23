package eu.depau.loak.domain.manager

/**
 * Web: no local-network or app permission to request.
 */
actual class PermissionManager {
	actual fun openPermissionsSettings() {}
	actual suspend fun requestLocalNetworkPermission(): Boolean = true
}
