package eu.depau.loak.ui.screens.playlist.viewmodels

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_created_playlist
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.data.database.mappers.toEntity
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.ui.core.UiState

class PlaylistCreateDialogViewModel(
	private val songs: List<DomainSong>,
	private val playlistDao: PlaylistDao,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager
) : ViewModel() {
	val creationState: StateFlow<UiState<Nothing?>>
		field = MutableStateFlow<UiState<Nothing?>>(UiState.Success(null))

	private val _events = Channel<Event>()
	val events = _events.receiveAsFlow()

	val name = TextFieldState()

	fun create() {
		viewModelScope.launch {
			creationState.value = UiState.Loading()
			try {
				val playlist = sessionManager.api.createPlaylist(
					name = name.text.toString(),
					songIds = songs.map { it.id }
				)
				playlistDao.insertPlaylist(playlist.toEntity())
				_events.send(
					Event.Dismiss(
						playlistDao.getPlaylistById(playlist.id)!!.toDomainModel()
					)
				)
				creationState.value = UiState.Success(null)
				snackBarManager.notify(Res.string.notice_created_playlist, playlist.name)
			} catch (e: Exception) {
				creationState.value = UiState.Error(e)
			}
		}
	}

	sealed class Event {
		data class Dismiss(val playlist: DomainPlaylist) : Event()
	}
}
