package eu.depau.loak.ui.util

import androidx.compose.runtime.compositionLocalOf

/** A live update operation trailing the download on the desktop auto-updater. */
enum class UpdatePhase {
	/** No update check has run yet, or none is in progress. */
	Idle,

	/** Checking against the release metadata. */
	Checking,

	/** An update is available and can be downloaded. */
	Available,

	/** Downloading the installer / update artifact. */
	Downloading,

	/** The artifact is on disk and ready to install. */
	Downloaded,

	/** Installing and restarting. */
	Installing,

	/** No newer version than [currentVersion]. */
	UpToDate,

	/** The check failed (offline, no release, unsupported); the release still describes it. */
	Failed
}

interface UpdateController {
	val state: kotlinx.coroutines.flow.StateFlow<UpdateState>

	/** Starts the update check if [enabled] and the platform can self-update. */
	fun check()

	/** Downloads the available update, then reports file readiness via [state]. */
	fun download()

	/** Installs the downloaded artifact and (on desktop) restarts the app into it. */
	suspend fun installAndRestart()

	/** True when the platform can self-update (packaged desktop install). */
	val supported: Boolean
}

/**
 * Immutable snapshot of the auto-updater.
 */
interface UpdateState {
	val phase: UpdatePhase
	val currentVersion: String
	val newVersion: String?
	val progress: Float?
}

/**
 * The running app's update controller. Desktop provides the real Nucleus-backed one; every
 * other platform leaves the no-op default, so the shared UI degrades to doing nothing.
 */
val LocalUpdateController = compositionLocalOf<UpdateController?> { null }

