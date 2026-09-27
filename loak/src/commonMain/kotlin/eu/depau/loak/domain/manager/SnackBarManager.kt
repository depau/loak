package eu.depau.loak.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_undo
import org.jetbrains.compose.resources.StringResource
import eu.depau.loak.domain.models.snackbars.PlayerEvent

class SnackBarManager {
	private val _events = MutableSharedFlow<PlayerEvent>()
	val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	fun notify(resource: StringResource, vararg args: Any) {
		scope.launch {
			_events.emit(PlayerEvent(resource, args.toList()))
		}
	}

	fun notifyWithUndo(resource: StringResource, onUndo: () -> Unit) {
		scope.launch {
			_events.emit(PlayerEvent(resource, action = Res.string.action_undo, onAction = onUndo))
		}
	}
}
