package eu.depau.loak.web

import org.w3c.dom.Worker

/**
 * Creates the [Worker] that backs
 * [androidx.sqlite.driver.web.WebWorkerSQLiteDriver]. `sqlite-worker.js` is a
 * static asset served next to the web app (see the webApp module).
 */
fun createSqliteWorker(): Worker = Worker("sqlite-worker.js")

/**
 * Minimal interop with the browser [MediaSession API](https://developer.mozilla.org/en-US/docs/Web/API/MediaSession).
 *
 * Only metadata is set (title/artist/album/artwork); richer features
 * (seek/playback-state action handlers) are a follow-up.
 */
@JsFun("() => typeof navigator !== 'undefined' && typeof navigator.mediaSession !== 'undefined' && typeof MediaMetadata !== 'undefined'")
private external fun mediaSessionSupportedJs(): Boolean

@JsFun("(title, artist, album, art) => { try { const md = new MediaMetadata({ title: title, artist: artist, album: album }); if (art) { md.artwork = [{ src: art, sizes: '512x512', type: 'image/jpeg' }]; } navigator.mediaSession.metadata = md; } catch (e) {} }")
private external fun setMediaMetadataJs(title: String, artist: String, album: String, art: String?): Unit

object BrowserMediaSession {
	fun isSupported(): Boolean = mediaSessionSupportedJs()

	fun setMetadata(title: String, artist: String, album: String, artworkUrl: String? = null) {
		setMediaMetadataJs(title, artist, album, artworkUrl)
	}

	fun clear() {
		setMediaMetadataJs("", "", "", null)
	}
}
