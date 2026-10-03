package eu.depau.loak.ui.screens.playlist.viewmodels

import eu.depau.loak.domain.models.parsePlaylistName
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_add_anyway
import eu.depau.loak.generated.resources.action_change
import eu.depau.loak.generated.resources.notice_already_in_playlist
import eu.depau.loak.generated.resources.notice_saved_to_playlist
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.snackbars.PlayerEvent
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.util.Logger
import dev.zt64.subsonic.api.model.Playlist as ApiPlaylist

/**
 * Saving songs to playlists: opens the sheet where playlists are selected with checkboxes
 * and saved via confirmation button.
 */
class PlaylistUpdateDialogViewModel(
	private val songs: List<DomainSong>,
	private val playlistToExclude: String?,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	val playlistsState: StateFlow<UiState<List<ApiPlaylist>>>
		field = MutableStateFlow<UiState<List<ApiPlaylist>>>(UiState.Loading())

	val selectedPlaylistIds: StateFlow<Set<String>>
		field = MutableStateFlow(emptySet())

	val creating: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val saving: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val filter = TextFieldState()
	val newPlaylistName = TextFieldState()

	private val _events = Channel<Event>(Channel.BUFFERED)
	val events = _events.receiveAsFlow()

	fun start() {
		viewModelScope.launch {
			_events.send(Event.ShowSheet)
			loadPlaylists()
		}
	}

	fun togglePlaylist(playlistId: String) {
		val current = selectedPlaylistIds.value
		selectedPlaylistIds.value = if (playlistId in current) {
			current - playlistId
		} else {
			current + playlistId
		}
	}

	private fun shownName(playlist: ApiPlaylist) =
		parsePlaylistName(playlist.name, playlist.validUntil != null, preferenceManager.audioMuseIntegration).display

	fun loadPlaylists() {
		viewModelScope.launch {
			playlistsState.value = UiState.Loading()
			try {
				val last = preferenceManager.lastPlaylistId
				val results = sessionManager.api.getPlaylists()
					.filter { it.id != playlistToExclude && it.readOnly != true }
					// ponytail: "recent" is just the last-used playlist first; a full MRU list if asked
					.sortedByDescending { it.id == last }
				playlistsState.value = UiState.Success(results)
			} catch (e: Exception) {
				playlistsState.value = UiState.Error(e)
			}
		}
	}

	fun createAndSave() {
		val name = newPlaylistName.text.toString().trim()
		if (name.isEmpty()) return
		viewModelScope.launch {
			creating.value = true
			try {
				val playlist = sessionManager.api.createPlaylist(name = name, songIds = songs.map { it.id })
				preferenceManager.lastPlaylistId = playlist.id
				snackBarManager.notify(Res.string.notice_saved_to_playlist, shownName(playlist))
				_events.send(Event.Dismiss)
			} catch (e: Exception) {
				Logger.e("PlaylistUpdateDialogViewModel", "Failed to create playlist", e)
			} finally {
				creating.value = false
			}
		}
	}

	fun saveSelected() {
		val selected = selectedPlaylistIds.value
		if (selected.isEmpty()) return
		val allPlaylists = (playlistsState.value as? UiState.Success)?.data ?: return
		val targets = allPlaylists.filter { it.id in selected }
		if (targets.isEmpty()) return

		viewModelScope.launch {
			saving.value = true
			try {
				var addedAnywhere = false
				for (playlist in targets) {
					try {
						val existing = sessionManager.api.getPlaylist(playlist.id).songs.mapTo(HashSet()) { it.id }
						val toAdd = songs.filterNot { it.id in existing }
						if (toAdd.isNotEmpty()) {
							sessionManager.api.updatePlaylist(playlist.id, songIdsToAdd = toAdd.map { it.id })
							addedAnywhere = true
						}
					} catch (e: Exception) {
						Logger.e("PlaylistUpdateDialogViewModel", "Failed to add songs to playlist ${playlist.id}", e)
					}
				}
				preferenceManager.lastPlaylistId = targets.last().id

				if (addedAnywhere) {
					if (targets.size == 1) {
						snackBarManager.notify(Res.string.notice_saved_to_playlist, shownName(targets.first()))
					} else {
						snackBarManager.notify(Res.string.notice_saved_to_playlist, "${targets.size} playlists")
					}
				} else {
					if (targets.size == 1) {
						snackBarManager.notify(Res.string.notice_already_in_playlist, shownName(targets.first()))
					} else {
						snackBarManager.notify(Res.string.notice_already_in_playlist, "${targets.size} playlists")
					}
				}
				_events.send(Event.Dismiss)
			} finally {
				saving.value = false
			}
		}
	}

	enum class Event {
		ShowSheet, HideSheet, Dismiss
	}
}
