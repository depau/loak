package eu.depau.loak.domain.manager

/** Desktop sends no new-albums notification (no background OS sync channel to hook). */
actual class NewAlbumsNotifier {
	actual suspend fun notifyNewAlbums(albumCount: Int, songCount: Int) = Unit
}
