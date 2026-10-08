package eu.depau.loak.ui.screens.starred

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.title_starred
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainArtistListType
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.DomainSongListType
import eu.depau.loak.domain.models.settings.BottomBarVisibilityMode
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.PullToRefreshBox
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.PersistentViewModelStoreOwner
import eu.depau.loak.ui.screens.album.viewmodels.AlbumListViewModel
import eu.depau.loak.ui.screens.artist.viewmodels.ArtistListViewModel
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import eu.depau.loak.ui.screens.song.viewmodels.SongListViewModel
import eu.depau.loak.ui.screens.starred.components.StarredScreenContent
import kotlin.time.Duration

@Composable
fun StarredScreen() {
	val persistentViewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>()
	val preferenceManager = koinInject<PreferenceManager>()

	val songsViewModel = koinViewModel<SongListViewModel>(
		key = "starredSongs",
		parameters = {
			parametersOf(
				DomainSongListType.FrequentlyPlayed,
				setOf(DomainFilter.Starred)
			)
		},
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val songsState by songsViewModel.songsState.collectAsStateWithLifecycle()
	val allDownloads by songsViewModel.allDownloads.collectAsStateWithLifecycle()

	val albumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "starredAlbums",
		parameters = {
			parametersOf(
				DomainAlbumListType.AlphabeticalByArtist,
				setOf(DomainFilter.Starred)
			)
		},
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val albumsState by albumsViewModel.albumsState.collectAsStateWithLifecycle()
	val selectedAlbum by albumsViewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by albumsViewModel.starred.collectAsStateWithLifecycle()
	val selectedAlbumRating by albumsViewModel.rating.collectAsStateWithLifecycle()

	val artistsViewModel = koinViewModel<ArtistListViewModel>(
		key = "starredArtists",
		parameters = {
			parametersOf(
				DomainArtistListType.AlphabeticalByName,
				setOf(DomainFilter.Starred)
			)
		},
		viewModelStoreOwner = persistentViewModelStoreOwner
	)
	val artistsState by artistsViewModel.artistsState.collectAsStateWithLifecycle()
	val selectedArtist by artistsViewModel.selectedArtist.collectAsStateWithLifecycle()
	val selectedArtistAlbums by artistsViewModel.selectedArtistAlbums.collectAsStateWithLifecycle()
	val selectedArtistIsStarred by artistsViewModel.starred.collectAsStateWithLifecycle()

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	val player = koinInject<MediaPlayerViewModel>()


	Scaffold(
		topBar = { NestedTopBar({ Text(stringResource(Res.string.title_starred)) }) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible =
				preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (preferVisible) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		val isAnythingLoading = albumsState is UiState.Loading ||
			artistsState is UiState.Loading ||
			songsState is UiState.Loading
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = !isAnythingLoading,
			onRefresh = {
				albumsViewModel.refreshAlbums(true)
				artistsViewModel.refreshArtists(true)
				songsViewModel.refreshSongs(true)
			},
			key = listOf(albumsState, artistsState, songsState)
		) {
			StarredScreenContent(
				innerPadding = innerPadding,
				onSetShareId = { shareId = it },

				songsState = songsState,
				allDownloads = allDownloads,
				onPlaySong = { index ->
					player.playNow(songsState.data.orEmpty(), index)
				},
				onSongStarredChange = { songsViewModel.refreshSongs(false) },

				albumsState = albumsState,
				selectedAlbum = selectedAlbum,
				selectedAlbumIsStarred = selectedAlbumIsStarred,
				selectedAlbumRating = selectedAlbumRating,
				onSelectAlbum = { albumsViewModel.selectAlbum(it) },
				onClearAlbumSelection = { albumsViewModel.clearSelection() },
				onStarSelectedAlbum = { albumsViewModel.starAlbum(it) },
				onPlayAlbumNext = { if (selectedAlbum != null) player.playNext(selectedAlbum as DomainSongCollection) },
				onAddAlbumToQueue = { if (selectedAlbum != null) player.addToQueue(selectedAlbum as DomainSongCollection) },
				onRateSelectedAlbum = { albumsViewModel.setRating(it) },

				artistsState = artistsState,
				selectedArtist = selectedArtist,
				selectedArtistAlbums = selectedArtistAlbums,
				selectedArtistIsStarred = selectedArtistIsStarred,
				onSelectArtist = { artistsViewModel.selectArtist(it) },
				onClearArtistSelection = { artistsViewModel.clearSelection() },
				onStarSelectedArtist = { artistsViewModel.starArtist(it) },
				onPlayArtistNext = {
					if (selectedArtist != null) artistsViewModel.playArtistAlbumsNext(
						player
					)
				},
				onAddArtistToQueue = {
					if (selectedArtist != null) artistsViewModel.addArtistAlbumsToQueue(
						player
					)
				},
			)
		}
	}

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)
}
