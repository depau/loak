package eu.depau.loak.ui.util

/** Shared no-op updater: nothing to update, nothing to check. Keeps UIs compiling everywhere. */
object NoOpUpdateController : UpdateController {
	override val state = kotlinx.coroutines.flow.MutableStateFlow<UpdateState>(NoOpUpdateState)
	override fun check() = Unit
	override fun download() = Unit
	override suspend fun installAndRestart() = Unit
	override val supported: Boolean = false
}

object NoOpUpdateState : UpdateState {
	override val phase: UpdatePhase = UpdatePhase.Idle
	override val currentVersion: String = ""
	override val newVersion: String? = null
	override val progress: Float? = null
}
