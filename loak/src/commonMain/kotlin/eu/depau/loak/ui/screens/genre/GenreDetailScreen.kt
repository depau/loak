package eu.depau.loak.ui.screens.genre

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
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainAlbumListType
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.DomainSongListType
import eu.depau.loak.domain.models.settings.BottomBarVisibilityMode
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.PullToRefreshBox
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.snackbars.ErrorSnackBar
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.screens.album.viewmodels.AlbumListViewModel
import eu.depau.loak.ui.screens.genre.components.GenreDetailScreenContent
import eu.depau.loak.ui.screens.share.dialogs.ShareDialog
import eu.depau.loak.ui.screens.song.viewmodels.SongListViewModel
import eu.depau.loak.di.isLandscape
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalPlatformContext
import kotlin.time.Duration

@Composable
fun GenreDetailScreen(
	genreName: String
) {
	val platformContext = LocalPlatformContext.current
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()

	val songsViewModel = koinViewModel<SongListViewModel>(
		key = "genre_detail_songs_$genreName",
		parameters = { parametersOf(DomainSongListType.ByGenre(genreName)) }
	)
	val songsState by songsViewModel.songsState.collectAsStateWithLifecycle()
	val selectedSong by songsViewModel.selectedSong.collectAsStateWithLifecycle()
	val selectedSongIsStarred by songsViewModel.starred.collectAsStateWithLifecycle()
	val selectedSongRating by songsViewModel.selectedSongRating.collectAsStateWithLifecycle()

	val albumsViewModel = koinViewModel<AlbumListViewModel>(
		key = "genre_detail_albums_$genreName",
		parameters = { parametersOf(DomainAlbumListType.ByGenre(genreName)) }
	)
	val albumsState by albumsViewModel.albumsState.collectAsStateWithLifecycle()
	val selectedAlbum by albumsViewModel.selectedAlbum.collectAsStateWithLifecycle()
	val selectedAlbumIsStarred by albumsViewModel.starred.collectAsStateWithLifecycle()
	val selectedAlbumRating by albumsViewModel.rating.collectAsStateWithLifecycle()

	val allDownloads by songsViewModel.allDownloads.collectAsStateWithLifecycle()
	val isOnline by songsViewModel.isOnline.collectAsStateWithLifecycle()

	var shareId by rememberSaveable { mutableStateOf<String?>(null) }
	var shareExpiry by remember { mutableStateOf<Duration?>(null) }

	Scaffold(
		topBar = { NestedTopBar({ Text(genreName) }) },
		bottomBar = {
			val scrollManager = LocalBottomBarScrollManager.current
			val preferVisible = preferenceManager.bottomBarVisibilityMode == BottomBarVisibilityMode.AllScreens
			if (!platformContext.isLandscape() && preferVisible) {
				RootBottomBar(scrolled = scrollManager.isTriggered)
			}
		}
	) { innerPadding ->
		PullToRefreshBox(
			modifier = Modifier
				.padding(top = innerPadding.calculateTopPadding())
				.background(MaterialTheme.colorScheme.surface),
			finished = albumsState !is UiState.Loading &&
				songsState !is UiState.Loading,
			onRefresh = {
				albumsViewModel.refreshAlbums(true)
				songsViewModel.refreshSongs(true)
			},
			key = listOf(albumsState, songsState)
		) {
			GenreDetailScreenContent(
				genreName = genreName,
				innerPadding = innerPadding,
				onSetShareId = { shareId = it },
				isOnline = isOnline,

				songsState = songsState,
				selectedSong = selectedSong,
				selectedSongIsStarred = selectedSongIsStarred,
				selectedSongRating = selectedSongRating,
				allDownloads = allDownloads,
				onSelectSong = { songsViewModel.selectSong(it) },
				onClearSongSelection = { songsViewModel.clearSelection() },
				onAddSongStar = { songsViewModel.starSong(true) },
				onRemoveSongStar = { songsViewModel.starSong(false) },
				onPlaySongNext = { song ->
					player.playNextSingle(song)
				},
				onAddSongToQueue = { song ->
					player.addToQueueSingle(song)
				},
				onPlaySong = { index ->
					player.playNow(songsState.data.orEmpty(), index)
				},
				onSetSongRating = { songsViewModel.rateSelectedSong(it) },
				onDownloadSong = { songsViewModel.downloadSong(it) },
				onCancelDownloadSong = { song ->
					songsViewModel.cancelDownload(song.id)
				},
				onDeleteDownloadSong = { song ->
					songsViewModel.deleteDownload(song.id)
				},

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
			)
		}
	}

	val flattenedErrors = listOf(
		(albumsState as? UiState.Error)?.error,
		(songsState as? UiState.Error)?.error
	).mapNotNull { it?.stackTraceToString() }.takeIf { it.isNotEmpty() }?.joinToString("\n\n")

	ErrorSnackBar(
		error = flattenedErrors?.let { Error(it) },
		onClearError = {
			albumsViewModel.clearError()
			songsViewModel.clearError()
		}
	)

	ShareDialog(
		id = shareId,
		onIdClear = { shareId = null },
		expiry = shareExpiry,
		onExpiryChange = { shareExpiry = it }
	)
}
