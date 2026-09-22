package eu.depau.loak.ui.screens.queue.viewmodels

import androidx.compose.foundation.lazy.LazyListState
import androidx.lifecycle.ViewModel
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.DownloadManager

class QueueViewModel(
	connectivityManager: ConnectivityManager,
	downloadManager: DownloadManager
) : ViewModel() {
	val listState = LazyListState()
	val isOnline = connectivityManager.isOnline
	val downloadedSongs = downloadManager.downloadedSongs
}
