package eu.depau.loak.ui.components.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_delete
import eu.depau.loak.generated.resources.info_action_is_permanent
import eu.depau.loak.generated.resources.info_error
import eu.depau.loak.generated.resources.notice_deleted_playlist
import eu.depau.loak.generated.resources.notice_deleted_share
import eu.depau.loak.generated.resources.title_delete_playlist
import eu.depau.loak.generated.resources.title_delete_share
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.entities.SyncActionType
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Delete
import eu.depau.loak.ui.components.common.SegmentedListButton
import eu.depau.loak.ui.components.common.SegmentedListButtonDefaults
import eu.depau.loak.ui.core.UiState

enum class DeletionEndpoint(
	val questionText: StringResource,
	val deletedText: StringResource
) {
	PLAYLIST(Res.string.title_delete_playlist, Res.string.notice_deleted_playlist),
	SHARE(Res.string.title_delete_share, Res.string.notice_deleted_share)
}

class DeletionViewModel(
	private val syncManager: SyncManager,
	private val playlistDao: PlaylistDao,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager
) : ViewModel() {
	val state: StateFlow<UiState<Nothing?>>
		field = MutableStateFlow<UiState<Nothing?>>(UiState.Success(null))

	private val _events = Channel<Event>()
	val events = _events.receiveAsFlow()

	fun delete(
		endpoint: DeletionEndpoint,
		id: String
	) {
		viewModelScope.launch {
			state.value = UiState.Loading()
			try {
				if (endpoint == DeletionEndpoint.SHARE) {
					sessionManager.api.deleteShare(id)
				} else {
					syncManager.enqueueAction(
						actionType = SyncActionType.DELETE_PLAYLIST,
						itemId = id
					)
					playlistDao.deletePlaylist(id)
				}
				state.value = UiState.Success(null)
				snackBarManager.notify(endpoint.deletedText)
				_events.send(Event.Dismiss)
			} catch (error: Exception) {
				state.value = UiState.Error(error = error)
			}
		}
	}

	sealed class Event {
		object Dismiss : Event()
	}
}

@Composable
fun DeletionDialog(
	endpoint: DeletionEndpoint,
	id: String?,
	onIdClear: () -> Unit,
	onRefresh: () -> Unit
) {
	val viewModel = koinViewModel<DeletionViewModel>()
	val state by viewModel.state.collectAsState()

	LaunchedEffect(Unit) {
		viewModel.events.collect { event ->
			when (event) {
				is DeletionViewModel.Event.Dismiss -> {
					onIdClear()
					onRefresh()
				}
			}
		}
	}

	id?.let {
		FormDialog(
			onDismissRequest = {
				if (state !is UiState.Loading) {
					onIdClear()
				}
			},
			icon = { Icon(Icons.Outlined.Delete, null) },
			title = { Text(stringResource(endpoint.questionText)) },
			buttons = {
				SegmentedListButton(
					modifier = Modifier.fillMaxWidth(),
					onClick = { viewModel.delete(endpoint, id) },
					enabled = state !is UiState.Loading,
					shapes = SegmentedListButtonDefaults.shapes(index = 0, count = 2),
					colors = SegmentedListButtonDefaults.errorColors()
				) {
					if (state is UiState.Loading) {
						CircularProgressIndicator(Modifier.size(20.dp))
					}
					Text(stringResource(Res.string.action_delete))
				}
				SegmentedListButton(
					modifier = Modifier.fillMaxWidth(),
					onClick = onIdClear,
					shapes = SegmentedListButtonDefaults.shapes(index = 1, count = 2)
				) {
					Text(stringResource(Res.string.action_cancel))
				}
			},
			content = {
				(state as? UiState.Error)?.error?.let {
					SelectionContainer {
						Text("$it")
					}
				}
				Text(
					stringResource(
						if (state !is UiState.Error)
							Res.string.info_action_is_permanent
						else Res.string.info_error
					)
				)
			}
		)
	}
}
