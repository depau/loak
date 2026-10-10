package eu.depau.loak.ui.screens.discover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.domain.discover.DeezerClient
import eu.depau.loak.domain.discover.DeezerCountryChart
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverGenre
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.domain.discover.ListenBrainzClient
import eu.depau.loak.domain.discover.ListenBrainzPlaylist
import eu.depau.loak.domain.discover.activeChart
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.util.toSqlQuery
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Discover home shelves. Every part starts empty and fills in as its source answers; a
 * part that stays empty hides its shelf, which is also how an unavailable source degrades.
 */
data class DiscoverHome(
	/** The ListenBrainz "made for you" playlists; empty without a username. */
	val madeForYou: List<ListenBrainzPlaylist> = emptyList(),
	val newReleases: List<DiscoverAlbum> = emptyList(),
	val chart: DeezerCountryChart? = null,
	val topSongs: List<DiscoverSong> = emptyList(),
	/** The library artist "Because you listen to…" is seeded from. */
	val seed: DiscoverArtist? = null,
	val similar: List<DiscoverArtist> = emptyList(),
	val genres: List<DiscoverGenre> = emptyList(),
	val loadingMadeForYou: Boolean = false,
	val loadingNewReleases: Boolean = false,
	val loadingChart: Boolean = false,
	val loadingSimilar: Boolean = false,
	val loadingGenres: Boolean = false
)

/** How much of each source a shelf holds; the drill-down lists show the rest. */
private const val NEW_RELEASES = 20
private const val SHELF_SONGS = 5
private const val SIMILAR_ARTISTS = 12
private const val GENRES = 12

class DiscoverHomeViewModel(
	private val deezer: DeezerClient,
	private val listenBrainz: ListenBrainzClient,
	private val preferenceManager: PreferenceManager,
	private val matcher: LibraryMatcher,
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao
) : ViewModel() {
	val home: StateFlow<DiscoverHome>
		field = MutableStateFlow(DiscoverHome())

	/** The chart country as the picker holds it: "" for automatic, else an ISO code. */
	val chartCountry get() = preferenceManager.discoverChartCountry

	/** Deezer's charts, for the picker's list; empty until the shelf has loaded. */
	val charts: StateFlow<List<DeezerCountryChart>>
		field = MutableStateFlow(emptyList())

	init {
		val deezerEnabled = preferenceManager.deezerEnabled
		home.value = DiscoverHome(
			loadingMadeForYou = preferenceManager.listenBrainzUsername.isNotBlank(),
			loadingNewReleases = true,
			loadingChart = deezerEnabled,
			loadingSimilar = true,
			loadingGenres = deezerEnabled
		)
		viewModelScope.launch { load() }
	}

	/** Saves the picker's choice and swaps the chart shelf to it. */
	fun setChartCountry(iso: String) {
		if (iso == preferenceManager.discoverChartCountry) return
		preferenceManager.discoverChartCountry = iso
		viewModelScope.launch { loadChart() }
	}

	private suspend fun load() = coroutineScope {
		launch {
			home.update {
				it.copy(
					newReleases = listenBrainz.getFreshReleases().take(NEW_RELEASES),
					loadingNewReleases = false
				)
			}
		}
		launch {
			val user = preferenceManager.listenBrainzUsername
			if (user.isNotBlank()) {
				home.update {
					it.copy(
						madeForYou = listenBrainz.getCreatedForPlaylists(user),
						loadingMadeForYou = false
					)
				}
			}
		}
		launch { loadSimilar() }
		if (!preferenceManager.deezerEnabled) return@coroutineScope
		launch { loadChart() }
		launch {
			// genre 0 is Deezer's "all genres" catch-all: nothing to browse
			home.update {
				it.copy(
					genres = deezer.getGenres().filter { genre -> genre.id != 0L }.take(GENRES),
					loadingGenres = false
				)
			}
		}
	}

	private suspend fun loadChart() {
		home.update { it.copy(loadingChart = true, chart = null, topSongs = emptyList()) }
		charts.value = deezer.getCountryCharts()
		val chart = deezer.activeChart(preferenceManager.discoverChartCountry)
		if (chart == null) {
			home.update { it.copy(loadingChart = false) }
			return
		}
		val songs = deezer.getChartTracks(chart.playlistId).take(SHELF_SONGS)
		home.update { it.copy(loadingChart = false, chart = chart, topSongs = songs) }
	}

	/**
	 * "Because you listen to…": the most played library artist MusicBrainz knows (ListenBrainz
	 * needs the MBID), then the artists it says sound like them that the library doesn't have.
	 */
	private suspend fun loadSimilar() = coroutineScope {
		val frequent = albumDao.getAlbumsByQuery(DomainAlbumListType.Frequent.toSqlQuery())
		val seed = frequent.firstNotNullOfOrNull {
			artistDao.getArtistById(it.album.artistId)?.takeIf { artist -> !artist.musicBrainzId.isNullOrBlank() }
		}
		if (seed == null) {
			home.update { it.copy(loadingSimilar = false) }
			return@coroutineScope
		}
		val mbid = seed.musicBrainzId!!
		val similar = listenBrainz.getSimilarArtists(mbid)
			.filter { matcher.artistInLibrary(it) == null }
			.take(SIMILAR_ARTISTS)
			.map { artist ->
				async {
					if (!preferenceManager.deezerEnabled) artist
					else deezer.findArtist(artist.name)
						?.let { artist.copy(pictureUrl = it.pictureUrl, deezerId = it.deezerId) }
						?: artist
				}
			}
			.awaitAll()
		home.update {
			it.copy(
				seed = if (similar.isNotEmpty()) DiscoverArtist(id = mbid, name = seed.name, mbid = mbid) else null,
				similar = similar,
				loadingSimilar = false
			)
		}
	}
}
