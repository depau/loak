package eu.depau.loak.ui.screens.discover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.domain.discover.DeezerClient
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.MusicBrainzClient
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A Discover album page: the album and its tracklist, from Deezer or else MusicBrainz. */
data class DiscoverAlbumPage(val album: DiscoverAlbum, val tracks: List<DiscoverSong>)

class DiscoverAlbumViewModel(
	private val key: Screen.DiscoverAlbum,
	private val deezer: DeezerClient,
	private val musicBrainz: MusicBrainzClient,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	/** Success(null) when neither Deezer nor MusicBrainz have the album, or can be reached. */
	val state: StateFlow<UiState<DiscoverAlbumPage?>>
		field = MutableStateFlow<UiState<DiscoverAlbumPage?>>(UiState.Loading())

	init {
		viewModelScope.launch {
			val page = fromDeezer() ?: fromMusicBrainz()
			state.value = UiState.Success(page)
			// MusicBrainz tracks have no preview of their own: Deezer may still have one
			if (page != null && preferenceManager.deezerEnabled && page.tracks.none { it.deezerId != null }) {
				page.tracks.map { track ->
					async {
						val url = deezer.resolveSongPreview(track.title, track.artist) ?: return@async
						state.update { current ->
							val now = current.data ?: return@update current
							UiState.Success(now.copy(tracks = now.tracks.map {
								if (it.id == track.id) it.copy(previewUrl = url) else it
							}))
						}
					}
				}.awaitAll()
			}
		}
	}

	private suspend fun fromDeezer(): DiscoverAlbumPage? {
		if (key.deezerId == null && !preferenceManager.deezerEnabled) return null
		val id = key.deezerId ?: key.title?.let { deezer.findAlbum(it, key.artist.orEmpty()) }?.deezerId
		val (album, tracks) = id?.let { deezer.getAlbum(it) } ?: return null
		return DiscoverAlbumPage(album.copy(mbid = key.mbid ?: album.mbid), tracks)
	}

	private suspend fun fromMusicBrainz(): DiscoverAlbumPage? {
		val mbid = key.mbid
			?: key.title?.let { musicBrainz.searchReleaseGroup(it, key.artist.orEmpty())?.id?.takeIf(String::isNotEmpty) }
			?: return null
		val (album, tracks) = musicBrainz.getAlbum(mbid) ?: return null
		return DiscoverAlbumPage(album, tracks)
	}
}
