package eu.depau.loak.ui.screens.discover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.domain.discover.DeezerClient
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.ListenBrainzClient
import eu.depau.loak.domain.discover.MusicBrainzClient
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A Discover artist page: who they are (Deezer, MusicBrainz's [genre] and [area]), their popular
 * songs and discography (Deezer) and similar artists (ListenBrainz, pictured from Deezer).
 */
data class DiscoverArtistPage(
	val artist: DiscoverArtist,
	val genre: String? = null,
	val area: String? = null,
	val topTracks: List<DiscoverSong> = emptyList(),
	val albums: List<DiscoverAlbum> = emptyList(),
	val similar: List<DiscoverArtist> = emptyList()
)

private const val TOP_TRACKS = 5
private const val SIMILAR_ARTISTS = 10

class DiscoverArtistViewModel(
	private val key: Screen.DiscoverArtist,
	private val deezer: DeezerClient,
	private val musicBrainz: MusicBrainzClient,
	private val listenBrainz: ListenBrainzClient
) : ViewModel() {
	/** Success(null) when neither Deezer nor the page's key name the artist. */
	val state: StateFlow<UiState<DiscoverArtistPage?>>
		field = MutableStateFlow<UiState<DiscoverArtistPage?>>(UiState.Loading())

	init {
		viewModelScope.launch { load() }
	}

	private suspend fun load() = coroutineScope {
		val found = key.deezerId?.let { deezer.getArtist(it) } ?: key.name?.let { deezer.findArtist(it) }
		val name = found?.name ?: key.name
		if (name == null) {
			state.value = UiState.Success(null)
			return@coroutineScope
		}
		val deezerId = found?.deezerId
		val top = async { deezerId?.let { deezer.getArtistTopTracks(it, TOP_TRACKS) }.orEmpty() }
		val albums = async {
			// the artist's own album list leaves the artist out
			deezerId?.let { deezer.getArtistAlbums(it) }.orEmpty().map { it.copy(artist = name, artistDeezerId = deezerId) }
		}
		var page = DiscoverArtistPage(
			artist = DiscoverArtist(found?.id ?: key.mbid.orEmpty(), name, found?.pictureUrl, deezerId, key.mbid),
			topTracks = top.await(),
			albums = albums.await()
		)
		state.value = UiState.Success(page)

		// MusicBrainz is rate-limited and ListenBrainz slow: their parts fill in afterwards
		val about = musicBrainz.searchArtist(name)?.takeIf { key.mbid == null || it.id == key.mbid }
		val mbid = key.mbid ?: about?.id?.takeIf { it.isNotEmpty() }
		page = page.copy(artist = page.artist.copy(mbid = mbid), genre = about?.genre, area = about?.area)
		state.value = UiState.Success(page)
		if (mbid == null) return@coroutineScope

		val similar = listenBrainz.getSimilarArtists(mbid).take(SIMILAR_ARTISTS).map { artist ->
			async {
				deezer.findArtist(artist.name)
					?.let { artist.copy(pictureUrl = it.pictureUrl, deezerId = it.deezerId) }
					?: artist
			}
		}.awaitAll()
		state.value = UiState.Success(page.copy(similar = similar))
	}
}
