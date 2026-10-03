package eu.depau.loak.domain.manager

import dev.nucleusframework.core.runtime.NucleusApp
import dev.nucleusframework.updater.provider.GitHubProvider
import dev.nucleusframework.updater.NucleusUpdater
import dev.nucleusframework.updater.UpdateInfo
import dev.nucleusframework.updater.UpdateResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import eu.depau.loak.ui.util.UpdateController
import eu.depau.loak.ui.util.UpdatePhase
import eu.depau.loak.ui.util.UpdateState
import eu.depau.loak.util.Logger
import java.io.File

/**
 * Desktop auto-update via Nucleus. Feeds the shared [UpdateState] surface so the UI can ask
 * for a check, show download progress, and trigger install-and-restart.
 *
 * The [NucleusUpdater] reads the version from the packaged `nucleus.app.properties`
 * (`NucleusApp.version`), so identical code is safe under `./gradlew run` (dev mode answers
 * NotAvailable). It talks to the `alpha` channel, matching the prerelease tags Lo'ak publishes.
 */
class DesktopUpdaterManager : UpdateController {
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	private val updater: NucleusUpdater = NucleusUpdater {
		provider = GitHubProvider(owner = "depau", repo = "loak")
		// Lo'ak ships only v1.0.0-alpha* prereleases; the "latest" channel 404s on them.
		channel = "alpha"
	}

	/** The UpdateInfo chosen by the last check; kept for [download]. */
	private var pendingInfo: UpdateInfo? = null

	/** The fully downloaded artifact, ready for [installAndRestart]. */
	private var downloadedFile: File? = null

	private val _state = MutableStateFlow<DesktopUpdateState>(
		DesktopUpdateState(
			currentVersion = NucleusApp.version ?: "",
		)
	)

	override val state: StateFlow<UpdateState> = _state.asStateFlow()

	/**
	 * Installers are only self-updatable in the packaged formats the plugin injects the
	 * executable type for; under `./gradlew run` this is false and the UI never offers to update.
	 */
	override val supported: Boolean
		get() = updater.isUpdateSupported() && updater.currentVersion.isNotBlank()

	override fun check() {
		if (!supported || _state.value.phase in BUSY_STATES) return
		scope.launch {
			_state.value = _state.value.copy(phase = UpdatePhase.Checking)
			val result = try {
				updater.checkForUpdates()
			} catch (e: Exception) {
				// checkForUpdates wraps almost everything into UpdateResult.Error, but keep this
				// defensive so a host failure can't crash the manager scope.
				Logger.i("Updater", "update check aborted", e)
				_state.value = _state.value.copy(phase = UpdatePhase.Failed)
				return@launch
			}
			when (result) {
				is UpdateResult.Available -> {
					pendingInfo = result.info
					_state.value = _state.value.copy(
						phase = UpdatePhase.Available,
						newVersion = result.info.version,
					)
				}

				is UpdateResult.NotAvailable ->
					// dev mode, unsupported format, or nothing newer
					_state.value = _state.value.copy(phase = UpdatePhase.UpToDate)

				is UpdateResult.Error -> {
					Logger.i("Updater", "update check failed: ${result.exception.message}")
					_state.value = _state.value.copy(phase = UpdatePhase.Failed)
				}
			}
		}
	}

	override fun download() {
		val info = pendingInfo
		if (info == null || _state.value.phase != UpdatePhase.Available) return
		scope.launch {
			_state.value = _state.value.copy(phase = UpdatePhase.Downloading, progress = 0f)
			try {
				var lastFile: File? = null
				updater.downloadUpdate(info).collect { progress ->
					lastFile = progress.file
					_state.value = _state.value.copy(
						phase = UpdatePhase.Downloading,
						progress = progress.percent.toFloat() / 100f,
					)
				}
				val file = lastFile ?: return@launch
				downloadedFile = file
				_state.value = _state.value.copy(
					phase = UpdatePhase.Downloaded,
					progress = 1f,
				)
			} catch (e: kotlin.coroutines.cancellation.CancellationException) {
				throw e
			} catch (e: Exception) {
				Logger.i("Updater", "download failed", e)
				_state.value = _state.value.copy(phase = UpdatePhase.Failed)
			}
		}
	}

	override suspend fun installAndRestart() {
		val file = downloadedFile ?: return
		_state.value = _state.value.copy(phase = UpdatePhase.Installing)
		withContext(Dispatchers.IO) { updater.installAndRestart(file) }
	}

	private data class DesktopUpdateState(
		override val phase: UpdatePhase = UpdatePhase.Idle,
		override val currentVersion: String,
		override val newVersion: String? = null,
		override val progress: Float? = null,
	) : UpdateState

	private companion object {
		val BUSY_STATES = setOf(UpdatePhase.Checking, UpdatePhase.Downloading, UpdatePhase.Installing)
	}
}
