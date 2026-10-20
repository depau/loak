package eu.depau.loak.domain.manager

/** iOS notification permission isn't a runtime toggle here; no-op for now. */
actual class NewAlbumsNotifier {
	actual suspend fun notifyNewAlbums(albumCount: Int, songCount: Int) = Unit
}
