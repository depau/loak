@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package eu.depau.loak.web

import org.w3c.dom.Worker

/**
 * Creates the [Worker] that backs
 * [androidx.sqlite.driver.web.WebWorkerSQLiteDriver]. `sqlite-worker.js` is a
 * static asset served next to the web app (see the webApp module).
 */
fun createSqliteWorker(): Worker = Worker("sqlite-worker.js")

/**
 * Interop with the browser [MediaSession API](https://developer.mozilla.org/en-US/docs/Web/API/MediaSession)
 * for OS media keys, browser media hubs, and headphone buttons.
 */
@JsFun("() => typeof navigator !== 'undefined' && typeof navigator.mediaSession !== 'undefined' && typeof MediaMetadata !== 'undefined'")
private external fun mediaSessionSupportedJs(): Boolean

@JsFun("(title, artist, album, art) => { try { const md = new MediaMetadata({ title: title, artist: artist, album: album }); if (art) { md.artwork = [{ src: art, sizes: '512x512', type: 'image/jpeg' }]; } navigator.mediaSession.metadata = md; } catch (e) {} }")
private external fun setMediaMetadataJs(title: String, artist: String, album: String, art: String?): Unit

@JsFun("(state) => { try { navigator.mediaSession.playbackState = state; } catch (e) {} }")
private external fun setPlaybackStateJs(state: String): Unit

@JsFun("(onPlay, onPause, onStop, onNext, onPrev, onSeek) => { try { const ms = navigator.mediaSession; ms.setActionHandler('play', onPlay); ms.setActionHandler('pause', onPause); ms.setActionHandler('stop', onStop); ms.setActionHandler('nexttrack', onNext); ms.setActionHandler('previoustrack', onPrev); ms.setActionHandler('seekto', onSeek ? (details) => { if (details.seekTime != null) onSeek(details.seekTime); } : null); } catch (e) {} }")
private external fun setActionHandlersJs(
	onPlay: () -> Unit,
	onPause: () -> Unit,
	onStop: () -> Unit,
	onNext: () -> Unit,
	onPrev: () -> Unit,
	onSeek: (Double) -> Unit
): Unit

@JsFun("() => { try { const ms = navigator.mediaSession; ['play', 'pause', 'stop', 'nexttrack', 'previoustrack', 'seekto'].forEach(a => ms.setActionHandler(a, null)); } catch (e) {} }")
private external fun clearActionHandlersJs(): Unit

object BrowserMediaSession {
	fun isSupported(): Boolean = mediaSessionSupportedJs()

	fun setMetadata(title: String, artist: String, album: String, artworkUrl: String? = null) {
		setMediaMetadataJs(title, artist, album, artworkUrl)
	}

	fun setPlaybackState(isPlaying: Boolean) {
		setPlaybackStateJs(if (isPlaying) "playing" else "paused")
	}

	fun setActionHandlers(
		onPlay: () -> Unit,
		onPause: () -> Unit,
		onStop: () -> Unit,
		onNext: () -> Unit,
		onPrevious: () -> Unit,
		onSeekTo: (Double) -> Unit
	) {
		setActionHandlersJs(onPlay, onPause, onStop, onNext, onPrevious, onSeekTo)
	}

	fun clear() {
		setMediaMetadataJs("", "", "", null)
		setPlaybackStateJs("none")
		clearActionHandlersJs()
	}
}
