package eu.depau.loak.domain.manager

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import eu.depau.loak.di.ActivityProvider
import eu.depau.loak.domain.models.DomainSong
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.asSink
import kotlinx.io.buffered
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Android: the system "Save as" dialog (SAF `ACTION_CREATE_DOCUMENT`) with the song-derived
 * file name as the suggested name. The picked document gets a content URI which we stream into;
 * nothing is cached in the app first.
 */
actual class ExportManager(
	private val context: Context,
	private val activities: ActivityProvider
) {
	private val launcher = run {
		val activity = activities.get<ComponentActivity>()
		activity.activityResultRegistry.register(
			key = UUID.randomUUID().toString(),
			contract = ActivityResultContracts.CreateDocument("audio/*"),
			callback = { uri ->
				pendingContinuation?.resume(uri?.let { ExportTarget(it) })
				pendingContinuation = null
			},
		)
	}

	private var pendingContinuation: CancellableContinuation<ExportTarget?>? = null

	actual suspend fun prepareTarget(song: DomainSong, fileName: String): ExportTarget? =
		suspendCancellableCoroutine { continuation ->
			pendingContinuation = continuation
			continuation.invokeOnCancellation {
				pendingContinuation = null
			}
			launcher.launch(fileName)
		}

	actual suspend fun openSink(target: ExportTarget): Sink {
		val out = context.contentResolver.openOutputStream(target.uri)
			?: throw IOException("Couldn't open ${target.uri}")
		return out.asSink().buffered()
	}

	actual suspend fun commit(target: ExportTarget, sink: Sink, success: Boolean) {
		// the buffered wrap flushes and closes the resolver's stream on close
		sink.close()
	}
}

actual class ExportTarget(val uri: Uri)
