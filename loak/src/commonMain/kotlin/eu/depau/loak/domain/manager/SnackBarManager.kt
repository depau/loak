package eu.depau.loak.domain.manager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_undo
import org.jetbrains.compose.resources.StringResource
import eu.depau.loak.domain.models.snackbars.PlayerEvent

class SnackBarManager {
	private val _events = MutableSharedFlow<PlayerEvent>()
	val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()

	// outlives screens, so deferred commits still happen after their screen is gone
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
	private val commitMutex = Mutex()

	fun notify(resource: StringResource, vararg args: Any) {
		scope.launch {
			_events.emit(PlayerEvent(resource, args.toList()))
		}
	}

	fun notifyWithUndo(resource: StringResource, vararg args: Any, onUndo: () -> Unit) {
		scope.launch {
			_events.emit(PlayerEvent(resource, args.toList(), Res.string.action_undo, onUndo))
		}
	}

	/**
	 * For changes the server can't undo: [commit] only runs once the snackbar goes away
	 * without Undo being pressed.
	 */
	fun notifyWithDeferredCommit(
		resource: StringResource,
		vararg args: Any,
		onUndo: suspend () -> Unit,
		commit: suspend () -> Unit
	) {
		scope.launch {
			_events.emit(
				PlayerEvent(
					resource, args.toList(), Res.string.action_undo,
					onAction = { scope.launch { onUndo() } },
					// commits run in order: a later one may depend on an earlier one (e.g. indices)
					onDismiss = { scope.launch { commitMutex.withLock { commit() } } },
					dismissible = true
				)
			)
		}
	}
}
