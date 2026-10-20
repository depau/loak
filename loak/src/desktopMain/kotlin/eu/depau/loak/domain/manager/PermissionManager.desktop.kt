package eu.depau.loak.domain.manager

/**
 * Desktop: no runtime permission prompts on desktop.
 */
actual class PermissionManager {
	actual fun openPermissionsSettings() {}
	actual suspend fun requestLocalNetworkPermission(): Boolean = true
	actual suspend fun requestNotificationsPermission(): Boolean = true
}
