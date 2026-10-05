package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_export_failed
import eu.depau.loak.generated.resources.notice_export_progress
import eu.depau.loak.generated.resources.notice_export_saved
import eu.depau.loak.generated.resources.notice_export_started
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.io.IOException
import kotlinx.io.Sink

/**
 * Exports a song's original raw audio file to user storage, straight from the server: Android
 * lets the user pick a destination through the system save dialog (SAF), iOS shares the file
 * through the share sheet ("Save to Files"), desktop writes to `~/Downloads` and reveals the
 * folder, web can't write files so it's a no-op (and the action is hidden there anyway).
 *
 * This is a distinct path from the in-app pin/download feature: nothing is kept in the audio
 * store, the file is re-downloaded on demand and streamed directly to the platform sink.
 */
expect class ExportManager {
	/**
	 * Shows any destination picker (SAF save dialog on Android) and returns where to write.
	 * Null means the user backed out before picking anything.
	 */
	suspend fun prepareTarget(song: DomainSong, fileName: String): ExportTarget?

	/** Opens a sink writing to [target]; called on the IO dispatcher. */
	suspend fun openSink(target: ExportTarget): Sink

	/**
	 * Closes [sink] and finalizes the export: opens the share sheet (iOS), reveals the
	 * Downloads folder (desktop). With [success] false the target is cleaned up instead
	 * (no share sheet, partial file removed where possible).
	 */
	suspend fun commit(target: ExportTarget, sink: Sink, success: Boolean)
}

/**
 * The destination of an exported original, opaque per platform: Android holds the SAF content
 * URI, iOS and desktop the file path to write; web never produces one.
 */
expect class ExportTarget

private const val EXPORT_EXTENSION_DEFAULT = "mp3"

private val EXPORT_MIME_TO_EXT = mapOf(
	"audio/mpeg" to "mp3",
	"audio/mp3" to "mp3",
	"audio/flac" to "flac",
	"audio/x-flac" to "flac",
	"audio/ogg" to "ogg",
	"audio/opus" to "opus",
	"audio/mp4" to "m4a",
	"audio/x-m4a" to "m4a",
	"audio/mp4a-latm" to "m4a",
	"audio/aac" to "aac",
	"audio/wav" to "wav",
	"audio/x-wav" to "wav",
	"audio/webm" to "webm",
	"audio/aiff" to "aiff",
	"audio/x-aiff" to "aiff",
	"audio/ape" to "ape",
	"audio/wma" to "wma",
)

/**
 * The file name for an exported original: `<track> - <title> - <artist>.<ext>` where [ext] is
 * the server-provided [DomainSong.fileExtension], else derived from the mime type, else mp3.
 */
fun originalExportFileName(song: DomainSong): String {
	val artist = song.artistName?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
	val ext = song.fileExtension?.takeIf { it.isNotBlank() }?.let { it }
		?: EXPORT_MIME_TO_EXT[song.mimeType?.lowercase()]
		?: EXPORT_EXTENSION_DEFAULT
	val base = buildString {
		song.trackNumber?.let { append(it.toString().padStart(2, '0')).append(" - ") }
		append(song.title)
		append(" - ").append(artist)
	}
	return sanitizeFileName(base) + "." + sanitizeFileName(ext).lowercase()
}

/** Removes characters that aren't valid in a file name on any supported platform. */
internal fun sanitizeFileName(name: String): String =
	name.replace(Regex("""[/\\:*?"<>|]"""), " ").trim(' ', '.', '\t', '\n')

/** Streams the song's original from the server into [sink], reporting [onProgress] along the way. */
suspend fun ExportManager.exportOriginal(
	song: DomainSong,
	sessionManager: SessionManager,
	snackBarManager: SnackBarManager,
	fileName: String = originalExportFileName(song),
) {
	val target = try {
		prepareTarget(song, fileName)
	} catch (e: CancellationException) {
		throw e
	} catch (e: Throwable) {
		Logger.w(TAG, "opening an export target failed", e)
		snackBarManager.notify(Res.string.notice_export_failed)
		return
	} ?: return

	snackBarManager.notify(Res.string.notice_export_started)

	val sink = try {
		withContext(IoDispatcher) { openSink(target) }
	} catch (e: CancellationException) {
		throw e
	} catch (e: Throwable) {
		Logger.w(TAG, "opening the export sink failed", e)
		snackBarManager.notify(Res.string.notice_export_failed)
		return
	}

	var lastBand = -1
	try {
		withContext(IoDispatcher) {
			// no maxBitRate, no format: the server sends the original file
			val url = sessionManager.api.getStreamUrl(id = song.id)
			streamInto(sessionManager, url, sink) { bytes, total ->
				if (total != null && total > 0) {
					val percent = (bytes * 100 / total).toInt().coerceIn(0, 99)
					val band = percent / 10
					if (percent > 0 && band != lastBand) {
						lastBand = band
						snackBarManager.notify(Res.string.notice_export_progress, percent)
					}
				}
			}
			commit(target, sink, success = true)
		}
		snackBarManager.notify(Res.string.notice_export_saved)
	} catch (e: CancellationException) {
		throw e
	} catch (e: Throwable) {
		Logger.w(TAG, "exporting the original of ${song.title} failed", e)
		try {
			withContext(NonCancellable) { commit(target, sink, success = false) }
		} catch (e2: CancellationException) {
			// already cancelling, nothing else to clean
		} catch (e2: Throwable) {
			Logger.w(TAG, "cleaning up a failed export failed", e2)
		}
		snackBarManager.notify(Res.string.notice_export_failed)
	}
}

private suspend fun streamInto(
	sessionManager: SessionManager,
	url: String,
	sink: Sink,
	onProgress: (bytes: Long, total: Long?) -> Unit,
) {
	sessionManager.api.httpClient.prepareGet(url).execute { response ->
		if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}")
		val total = response.contentLength()
		onProgress(0, total)
		val body = response.bodyAsChannel()
		val buffer = ByteArray(64 * 1024)
		var bytes = 0L
		while (true) {
			val n = body.readAvailable(buffer)
			if (n == -1) break
			sink.write(buffer, 0, n)
			bytes += n
			onProgress(bytes, total)
		}
		if (total != null && bytes != total) throw IOException("got $bytes of $total bytes")
	}
	sink.flush()
}

private const val TAG = "ExportManager"
