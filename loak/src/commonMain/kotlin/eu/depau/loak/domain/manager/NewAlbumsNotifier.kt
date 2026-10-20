package eu.depau.loak.domain.manager

/**
 * Posts the "new albums" notification after a library sync finds newly added albums.
 * Android owns the only real implementation (a silent notification that opens the Recently
 * added list); the other platforms have OS notifications (iOS/desktop) or browsers (web)
 * the app can't reliably show from a background sync, so they stay no-ops for now.
 */
expect class NewAlbumsNotifier {
	/** Posts a silent "N new albums · M songs" notification; must not throw. */
	suspend fun notifyNewAlbums(albumCount: Int, songCount: Int)
}
