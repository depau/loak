package eu.depau.loak.domain.manager

expect class PermissionManager {
	fun openPermissionsSettings()
	suspend fun requestLocalNetworkPermission(): Boolean

	/** True if notification permission is granted or not needed (pre-Android 13 / other platforms). */
	suspend fun requestNotificationsPermission(): Boolean
}
