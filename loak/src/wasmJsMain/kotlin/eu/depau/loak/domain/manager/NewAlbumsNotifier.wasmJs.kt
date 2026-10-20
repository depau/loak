package eu.depau.loak.domain.manager

/** Web has no OS notification channel for a background sync; no-op. */
actual class NewAlbumsNotifier {
	actual suspend fun notifyNewAlbums(albumCount: Int, songCount: Int) = Unit
}
