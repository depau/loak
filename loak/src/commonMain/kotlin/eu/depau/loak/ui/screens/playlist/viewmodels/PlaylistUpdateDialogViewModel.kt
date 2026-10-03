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
 * Saving songs to a playlist: straight to the last-used one when there is one, otherwise
 * (or on "Change") through the save-to-playlist sheet.
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

	val creating: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val filter = TextFieldState()
	val newPlaylistName = TextFieldState()

	private val _events = Channel<Event>()
	val events = _events.receiveAsFlow()

	/** Saves to the last-used playlist, or asks the sheet to open. */
	fun start() {
		viewModelScope.launch {
			val last = preferenceManager.lastPlaylistId
			val playlist = if (last.isNotEmpty() && last != playlistToExclude) {
				// it may have turned read-only (smart, or no longer shared) since
				runCatching { sessionManager.api.getPlaylist(last) }.getOrNull()?.takeIf { it.readOnly != true }
			} else null
			if (playlist == null) {
				_events.send(Event.ShowSheet)
				loadPlaylists()
			} else {
				save(playlist, offerChange = true)
			}
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

	fun pick(playlist: ApiPlaylist) {
		viewModelScope.launch {
			_events.send(Event.HideSheet)
			save(playlist, offerChange = false)
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

	/**
	 * Adds the songs unless they are all already there: then the snackbar offers
	 * "Add anyway" instead. Only [playlist] is fetched, one request.
	 */
	private suspend fun save(playlist: ApiPlaylist, offerChange: Boolean) {
		// the host stays composed until its snackbar is resolved, so "Change" can reopen the sheet
		try {
			val existing = sessionManager.api.getPlaylist(playlist.id).songs.mapTo(HashSet()) { it.id }
			preferenceManager.lastPlaylistId = playlist.id
			if (songs.all { it.id in existing }) {
				_events.send(Event.Dismiss)
				snackBarManager.notify(
					PlayerEvent(
						Res.string.notice_already_in_playlist, listOf(shownName(playlist)),
						action = Res.string.action_add_anyway,
						onAction = {
							// the sheet's scope may be gone by now
							snackBarManager.launch {
								add(playlist)
								snackBarManager.notify(Res.string.notice_saved_to_playlist, shownName(playlist))
							}
						}
					)
				)
				return
			}
			add(playlist, songs.filterNot { it.id in existing })
			if (!offerChange) {
				_events.send(Event.Dismiss)
				snackBarManager.notify(Res.string.notice_saved_to_playlist, shownName(playlist))
				return
			}
			// "Change" moves the songs: they come out of this playlist when another is picked
			snackBarManager.notify(
				PlayerEvent(
					Res.string.notice_saved_to_playlist, listOf(shownName(playlist)),
					action = Res.string.action_change,
					onAction = {
						viewModelScope.launch {
							undoAdd(playlist, songs.filterNot { it.id in existing })
							_events.send(Event.ShowSheet)
							loadPlaylists()
						}
					},
					onDismiss = { viewModelScope.launch { _events.send(Event.Dismiss) } }
				)
			)
		} catch (e: Exception) {
			Logger.e("PlaylistUpdateDialogViewModel", "Failed to add to playlist", e)
			_events.send(Event.Dismiss)
		}
	}

	private suspend fun add(playlist: ApiPlaylist, toAdd: List<DomainSong> = songs) {
		sessionManager.api.updatePlaylist(playlist.id, songIdsToAdd = toAdd.map { it.id })
	}

	/** Takes back [added], which were just appended to the end of [playlist]. */
	private suspend fun undoAdd(playlist: ApiPlaylist, added: List<DomainSong>) {
		if (added.isEmpty()) return
		val size = sessionManager.api.getPlaylist(playlist.id).songs.size
		sessionManager.api.updatePlaylist(
			playlist.id,
			songIndicesToRemove = (size - added.size until size).toList()
		)
	}

	enum class Event {
		ShowSheet, HideSheet, Dismiss
	}
}
