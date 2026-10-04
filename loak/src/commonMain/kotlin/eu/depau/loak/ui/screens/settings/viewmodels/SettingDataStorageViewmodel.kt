package eu.depau.loak.ui.screens.settings.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import eu.depau.loak.data.database.dao.SyncActionDao
import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.domain.manager.AudioStoreUsage
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.repositories.DbRepository
import eu.depau.loak.domain.repositories.SongRepository
import eu.depau.loak.util.IoDispatcher

class SettingsDataStorageViewModel(
	private val syncManager: SyncManager,
	private val dbRepository: DbRepository,
	private val syncDao: SyncActionDao,
	private val downloadManager: DownloadManager,
	private val songRepository: SongRepository,
	private val audioStore: AudioStore,
	connectivityManager: ConnectivityManager
) : ViewModel() {

	val syncState = syncManager.syncState
		.stateIn(
			scope = viewModelScope,
			started = SharingStarted.WhileSubscribed(5000),
			initialValue = syncManager.syncState.value
		)

	val pendingActionCount: StateFlow<Int>
		field = MutableStateFlow(0)

	val downloadCount = downloadManager.downloadCount.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), 0
	)
	val downloadSize = downloadManager.downloadSize.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), 0L
	)

	val isDownloadingLibrary = downloadManager.isDownloadingLibrary
	val libraryDownloadProgress = downloadManager.libraryDownloadProgress
	val isOnline = connectivityManager.isOnline

	val audioStoreAvailable = audioStore.available
	val audioStoreUsage = audioStore.usage.stateIn(
		viewModelScope, SharingStarted.WhileSubscribed(5000), AudioStoreUsage()
	)

	init {
		loadPendingActions()
	}

	private fun loadPendingActions() {
		viewModelScope.launch(Dispatchers.Default) {
			pendingActionCount.value = syncDao.getPendingActions().size
		}
	}

	fun triggerManualSync() {
		syncManager.triggerManualSync()
	}

	fun rebuildDatabase() {
		viewModelScope.launch(Dispatchers.Default) {
			dbRepository.removeEverything()
			syncManager.stopPeriodicSync()
			pendingActionCount.value = 0
		}
		triggerManualSync()
	}

	fun removeAllActions() {
		viewModelScope.launch(Dispatchers.Default) {
			syncDao.clearAllActions()
			pendingActionCount.value = 0
		}
	}

	fun clearAllDownloads() {
		downloadManager.clearAllDownloads()
	}

	/** Applies changed cache settings. */
	fun trimAudioCache() {
		viewModelScope.launch(IoDispatcher) { audioStore.trim() }
	}

	fun clearAudioCache() {
		viewModelScope.launch(IoDispatcher) { audioStore.clearCache() }
	}

	fun downloadEntireLibrary() {
		viewModelScope.launch(Dispatchers.Default) {
			val allSongs = songRepository.getAllSongs()
			downloadManager.downloadEntireLibrary(allSongs)
		}
	}

	fun cancelLibraryDownload() {
		downloadManager.cancelAllActiveDownloads()
	}
}
