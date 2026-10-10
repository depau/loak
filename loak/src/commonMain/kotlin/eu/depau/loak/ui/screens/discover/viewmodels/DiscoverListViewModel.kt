package eu.depau.loak.ui.screens.discover.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.depau.loak.domain.discover.DeezerClient
import eu.depau.loak.domain.discover.DeezerCountryChart
import eu.depau.loak.domain.discover.DiscoverAlbum
import eu.depau.loak.domain.discover.DiscoverArtist
import eu.depau.loak.domain.discover.DiscoverSong
import eu.depau.loak.domain.discover.LibraryMatcher
import eu.depau.loak.domain.discover.ListenBrainzClient
import eu.depau.loak.domain.discover.activeChart
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.ui.screens.discover.DiscoverListType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One Discover drill-down list; only the list its [DiscoverListType] asked for is filled. */
data class DiscoverListPage(
	val loading: Boolean = true,
	val songs: List<DiscoverSong> = emptyList(),
	val albums: List<DiscoverAlbum> = emptyList(),
	val artists: List<DiscoverArtist> = emptyList(),
	/** Which chart the songs come from, on the Top songs list. */
	val chart: DeezerCountryChart? = null,
	/** Ids of the songs the library already has, for "Hide songs I have". */
	val ownedIds: Set<String> = emptySet(),
	/** Ids whose ListenBrainz preview lookup is still running. */
	val previewLoadingIds: Set<String> = emptySet()
)

private const val SIMILAR_ARTISTS = 50

class DiscoverListViewModel(
	private val type: DiscoverListType,
	private val deezer: DeezerClient,
	private val listenBrainz: ListenBrainzClient,
	private val preferenceManager: PreferenceManager,
	private val matcher: LibraryMatcher
) : ViewModel() {
	val page: StateFlow<DiscoverListPage>
		field = MutableStateFlow(DiscoverListPage())

	/** Deezer's charts, for the country picker on the Top songs list. */
	val charts: StateFlow<List<DeezerCountryChart>>
		field = MutableStateFlow(emptyList())

	/** The chart country as the picker holds it: "" for automatic, else an ISO code. */
	val chartCountry get() = preferenceManager.discoverChartCountry

	init {
		viewModelScope.launch { load() }
	}

	/** Saves the picker's choice and reloads the chart under it. */
	fun setChartCountry(iso: String) {
		if (iso == preferenceManager.discoverChartCountry) return
		preferenceManager.discoverChartCountry = iso
		viewModelScope.launch { loadChart() }
	}

	private suspend fun load() {
		when (type) {
			DiscoverListType.TopSongs -> loadChart()
			DiscoverListType.NewReleases ->
				page.update { it.copy(loading = false, albums = listenBrainz.getFreshReleases()) }
			is DiscoverListType.SimilarArtists -> loadSimilar(type.artistMbid)
			is DiscoverListType.Genre ->
				page.update { it.copy(loading = false, songs = deezer.getGenreTracks(type.genreId)) }
			is DiscoverListType.MadeForYou -> loadPlaylist(type.playlistMbid)
		}
	}

	private suspend fun loadChart() {
		page.update { it.copy(loading = true, songs = emptyList()) }
		charts.value = deezer.getCountryCharts()
		val chart = deezer.activeChart(preferenceManager.discoverChartCountry)
		if (chart == null) {
			page.update { it.copy(loading = false) }
			return
		}
		page.update { it.copy(chart = chart) }
		val songs = deezer.getChartTracks(chart.playlistId)
		page.update { it.copy(loading = false, songs = songs) }
	}

	/**
	 * A "made for you" playlist: render its tracks as soon as they arrive, then resolve each
	 * preview independently so a slow Deezer lookup does not hold every preview button hostage.
	 */
	private suspend fun loadPlaylist(mbid: String) = coroutineScope {
		val tracks = listenBrainz.getPlaylistTracks(mbid)
		val resolvePreviews = preferenceManager.deezerEnabled
		page.update {
			it.copy(
				loading = false,
				songs = tracks,
				previewLoadingIds = if (resolvePreviews) tracks.mapTo(mutableSetOf()) { song -> song.id }
				else emptySet()
			)
		}
		val owned = tracks.filter { matcher.songInLibrary(it) != null }.mapTo(mutableSetOf()) { it.id }
		page.update { it.copy(ownedIds = owned) }
		if (!resolvePreviews) return@coroutineScope
		tracks.map { song ->
			async {
				val previewUrl = deezer.resolveSongPreview(song.title, song.artist)
				page.update { current ->
					current.copy(
						songs = current.songs.map {
							if (it.id == song.id) it.copy(previewUrl = previewUrl ?: it.previewUrl) else it
						},
						previewLoadingIds = current.previewLoadingIds - song.id
					)
				}
			}
		}.awaitAll()
	}

	private suspend fun loadSimilar(mbid: String) = coroutineScope {
		val artists = listenBrainz.getSimilarArtists(mbid)
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
		page.update { it.copy(loading = false, artists = artists) }
	}
}
