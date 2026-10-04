package eu.depau.loak.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_not_available_offline
import org.koin.compose.koinInject

/**
 * What plays right now: everything while online; offline, the songs in the audio store
 * (downloaded or cached) and the albums, artists and playlists holding any of them.
 */
@Immutable
data class Availability(
	val online: Boolean = true,
	private val songs: Set<String> = emptySet(),
	private val collections: Set<String> = emptySet()
) {
	fun song(id: String) = online || id in songs
	fun collection(id: String) = online || id in collections
}

val LocalAvailability = compositionLocalOf { Availability() }

/** The app's [Availability], to provide as [LocalAvailability]. */
@Composable
fun availability(): Availability {
	val online by koinInject<ConnectivityManager>().isOnline.collectAsStateWithLifecycle()
	val store = koinInject<AudioStore>()
	val songs by store.storedSongs.collectAsStateWithLifecycle()
	val collections by store.storedCollections.collectAsStateWithLifecycle()
	// online, the sets don't matter: don't recompose every row when the cache changes
	return if (online) Availability() else Availability(false, songs, collections)
}

/** Dims what isn't available offline, like disabled content. */
fun Modifier.unavailable(unavailable: Boolean) = if (unavailable) alpha(0.38f) else this

/** [onClick], or for a song that can't play offline, a snackbar saying so. */
@Composable
fun playOrExplain(songId: String, onClick: () -> Unit): () -> Unit {
	if (LocalAvailability.current.song(songId)) return onClick
	val snackBarManager = koinInject<SnackBarManager>()
	return { snackBarManager.notify(Res.string.info_not_available_offline) }
}
