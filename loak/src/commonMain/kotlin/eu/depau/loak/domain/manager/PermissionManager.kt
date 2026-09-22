package eu.depau.loak.domain.manager

expect class PermissionManager {
	fun openPermissionsSettings()
	suspend fun requestLocalNetworkPermission(): Boolean
}
