package eu.depau.loak.ui.screens.collection.viewmodels

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_deleted_download
import eu.depau.loak.generated.resources.notice_download_started
import eu.depau.loak.generated.resources.notice_removed_from_playlist
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainAlbumInfo
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.repositories.AlbumRepository
import eu.depau.loak.domain.repositories.CollectionRepository
import eu.depau.loak.domain.repositories.PlaylistRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.core.inBackground
import eu.depau.loak.util.Logger

class CollectionDetailViewModel(
	private val collectionId: String,
	private val repository: CollectionRepository,
	private val songRepository: SongRepository,
	private val albumRepository: AlbumRepository,
	private val downloadManager: DownloadManager,
	private val sessionManager: SessionManager,
	private val snackBarManager: SnackBarManager,
	private val syncManager: SyncManager,
	private val playlistRepository: PlaylistRepository,
	connectivityManager: ConnectivityManager
) : ViewModel() {

	val collectionState: StateFlow<UiState<DomainSongCollection>>
		field = MutableStateFlow<UiState<DomainSongCollection>>(UiState.Loading())

	val starred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedSong: StateFlow<DomainSong?>
		field = MutableStateFlow(null)

	val albumInfoState: StateFlow<UiState<DomainAlbumInfo>>
		field = MutableStateFlow<UiState<DomainAlbumInfo>>(UiState.Loading())

	val selectedSongIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	private val _selectedSongRating = MutableStateFlow(0)
	val selectedSongRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val selectedAlbum: StateFlow<DomainAlbum?>
		field = MutableStateFlow(null)

	val selectedAlbumIsStarred: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val selectedAlbumRating: StateFlow<Int>
		field = MutableStateFlow(0)

	val rating: StateFlow<Int>
		field = MutableStateFlow(0)

	val listState = LazyListState()

	val isOnline = connectivityManager.isOnline

	val allDownloads = downloadManager.allDownloads
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptyList()
		)

	val otherAlbums: StateFlow<List<DomainAlbum>> = collectionState
		.map { it.data as? DomainAlbum }
		.distinctUntilChanged()
		.flatMapLatest { album ->
			if (album != null) {
				repository.getOtherAlbums(album.artistId, album.id)
			} else {
				flowOf(emptyList())
			}
		}
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.Lazily,
			initialValue = emptyList()
		)

	init {
		viewModelScope.launch {
			sessionManager.isLoggedIn.collect {
				// one flow, in order: the cache first, then (once) the server's answer as the
				// last word, without a spinner or an error over the cached songs
				if (it) loadCollection(fullRefresh = true, background = true)
			}
		}
	}

	fun refreshCollection(fullRefresh: Boolean, background: Boolean = false) {
		viewModelScope.launch { loadCollection(fullRefresh, background) }
	}

	/** Offline, [fullRefresh] is dropped: the songs as last seen, no failing server call. */
	private suspend fun loadCollection(fullRefresh: Boolean, background: Boolean) {
		run {
			repository.getCollectionFlow(fullRefresh && isOnline.value, collectionId)
				.let { if (background) it.inBackground { c -> c.songs.isEmpty() } else it }
				.collect {
					collectionState.value = it
					if (it.data is DomainAlbum) {
						starred.value = albumRepository.isAlbumStarred(it.data as DomainAlbum)
						rating.value = albumRepository.getAlbumRating(it.data as DomainAlbum)
						if (albumInfoState.value is UiState.Success || !isOnline.value) return@collect
						try {
							val albumInfo = repository.getAlbumInfo(collectionId)
							albumInfoState.value = UiState.Success(albumInfo.toDomainModel())
						} catch (e: Exception) {
							albumInfoState.value = UiState.Error(e)
						}
					}
				}
		}
	}

	fun selectSong(song: DomainSong) {
		viewModelScope.launch {
			selectedSong.value = song
			selectedSongIsStarred.value = songRepository.isSongStarred(song)
			selectedSongRating.value = songRepository.getSongRating(song)
		}
	}

	fun selectAlbum(album: DomainAlbum) {
		viewModelScope.launch {
			selectedAlbum.value = album
			selectedAlbumIsStarred.value = albumRepository.isAlbumStarred(album)
			selectedAlbumRating.value = albumRepository.getAlbumRating(album)
		}
	}

	fun clearSelection() {
		selectedSong.value = null
		selectedAlbum.value = null
	}

	fun clearError() {
		collectionState.value.data?.let {
			collectionState.value = UiState.Success(it)
		}
	}

	/** Hides the song at once; the server update waits until the Undo snackbar is gone. */
	fun removeFromPlaylist() {
		val song = selectedSong.value ?: return
		val before = collectionState.value.data as? DomainPlaylist ?: return
		val index = before.songs.indexOf(song)
		if (index == -1) return
		clearSelection()

		collectionState.value = UiState.Success(
			before.copy(
				songs = before.songs.filterIndexed { i, _ -> i != index },
				songCount = before.songCount - 1,
				duration = before.duration - song.duration
			)
		)
		viewModelScope.launch {
			val actionId = playlistRepository.removeSong(before.id, index) ?: return@launch
			snackBarManager.notifyWithDeferredCommit(
				Res.string.notice_removed_from_playlist,
				onUndo = {
					playlistRepository.undoRemove(actionId, before.id, index, song.id)
					collectionState.value = UiState.Success(before)
				},
				commit = { syncManager.release(actionId) }
			)
		}
	}

	fun starSelectedSong() {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				songRepository.starSong(selection)
				selectedSongIsStarred.value = true
				refreshCollection(false)
			}
		}
	}

	fun unstarSelectedSong() {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				songRepository.unstarSong(selection)
				selectedSongIsStarred.value = false
				refreshCollection(false)
			}
		}
	}

	fun rateSelectedSong(rating: Int) {
		viewModelScope.launch {
			val selection = selectedSong.value ?: return@launch
			runCatching {
				songRepository.rateSong(selection, rating)
				_selectedSongRating.value = rating
			}
		}
	}

	fun rateAlbum(newRating: Int) {
		viewModelScope.launch {
			(collectionState.value.data as? DomainAlbum)?.let { album ->
				albumRepository.rateAlbum(album, newRating)
				rating.value = newRating
			}
		}
	}

	fun starAlbum(starred: Boolean) {
		viewModelScope.launch {
			runCatching {
				val collection = collectionState.value.data ?: return@launch
				if (collection !is DomainAlbum) return@launch
				if (starred) {
					albumRepository.starAlbum(collection)
				} else {
					albumRepository.unstarAlbum(collection)
				}
				refreshCollection(false)
			}
		}
	}

	fun rateSelectedAlbum(rating: Int) {
		viewModelScope.launch {
			selectedAlbum.value?.let { album ->
				albumRepository.rateAlbum(album, rating)
				selectedAlbumRating.value = rating
			}
		}
	}

	fun starSelectedAlbum(starred: Boolean) {
		viewModelScope.launch {
			runCatching {
				val collection = selectedAlbum.value ?: return@launch
				if (starred) {
					albumRepository.starAlbum(collection)
				} else {
					albumRepository.unstarAlbum(collection)
				}
				selectedAlbumIsStarred.value = starred
			}
		}
	}

	fun downloadSong(song: DomainSong) {
		downloadManager.downloadSong(song)
		snackBarManager.notify(Res.string.notice_download_started)
	}

	fun cancelDownload(songId: String) {
		downloadManager.cancelDownload(songId)
	}

	fun deleteDownload(songId: String) {
		downloadManager.deleteDownload(songId)
		snackBarManager.notify(Res.string.notice_deleted_download)
	}

	fun downloadAll() {
		val collection = collectionState.value.data ?: return
		viewModelScope.launch {
			downloadManager.downloadCollection(collection)
			snackBarManager.notify(Res.string.notice_download_started)
		}
	}

	fun cancelDownloadAll() {
		collectionState.value.data?.songs?.forEach {
			downloadManager.cancelDownload(it.id)
		}
	}

	fun collectionDownloadStatus(): Flow<DownloadStatus> {
		val songs = collectionState.value.data?.songs.orEmpty()
		return downloadManager.getCollectionDownloadStatus(songs.map { it.id })
	}
}
