package eu.depau.loak.ui.screens.discover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.domain.discover.DeezerClient
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A Discover album page: the album and its tracklist, from Deezer. */
data class DiscoverAlbumPage(val album: DiscoverAlbum, val tracks: List<DiscoverSong>)

class DiscoverAlbumViewModel(
	private val key: Screen.DiscoverAlbum,
	private val deezer: DeezerClient
) : ViewModel() {
	/** Success(null) when Deezer doesn't have the album, or can't be reached. */
	val state: StateFlow<UiState<DiscoverAlbumPage?>>
		field = MutableStateFlow<UiState<DiscoverAlbumPage?>>(UiState.Loading())

	init {
		viewModelScope.launch {
			val id = key.deezerId ?: key.title?.let { deezer.findAlbum(it, key.artist.orEmpty()) }?.deezerId
			val found = id?.let { deezer.getAlbum(it) }
			state.value = UiState.Success(
				found?.let { (album, tracks) -> DiscoverAlbumPage(album.copy(mbid = key.mbid ?: album.mbid), tracks) }
			)
		}
	}
}
