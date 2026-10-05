package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.DomainSong
import kotlinx.io.Sink

/** Web: no file-system API; the action is hidden there anyway, so this is a no-op. */
actual class ExportManager {
	actual suspend fun prepareTarget(song: DomainSong, fileName: String): ExportTarget? = null
	actual suspend fun openSink(target: ExportTarget): Sink = error("unreachable")
	actual suspend fun commit(target: ExportTarget, sink: Sink, success: Boolean) {}
}

actual class ExportTarget
