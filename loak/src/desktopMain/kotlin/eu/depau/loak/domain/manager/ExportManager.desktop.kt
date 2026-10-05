package eu.depau.loak.domain.manager

import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.asSink
import kotlinx.io.buffered
import java.awt.Desktop
import java.io.File

/**
 * Desktop: writes the original straight to `~/Downloads` and reveals the folder once done.
 * There is no share sheet on desktop, so "share" is implemented as save-to-Downloads + open.
 */
actual class ExportManager {
	actual suspend fun prepareTarget(song: DomainSong, fileName: String): ExportTarget {
		val downloadsDir = File(System.getProperty("user.home"), "Downloads")
		downloadsDir.mkdirs()
		return ExportTarget(File(downloadsDir, fileName))
	}

	actual suspend fun openSink(target: ExportTarget): Sink {
		val file = target.file
		val parent = file.parentFile
		if (parent != null) parent.mkdirs()
		val out = file.outputStream()
		return out.asSink().buffered()
	}

	actual suspend fun commit(target: ExportTarget, sink: Sink, success: Boolean) {
		sink.close()
		if (success && Desktop.isDesktopSupported()) {
			target.file.parentFile?.takeIf { it.exists() }?.let { Desktop.getDesktop().open(it) }
		} else if (!success) {
			target.file.delete()
		}
	}
}

actual class ExportTarget(val file: File)
