package eu.depau.loak.domain.manager

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import eu.depau.loak.domain.parser.LogLine

/**
 * Web: there's no `logcat`-like system log; the console log screen stays empty.
 */
actual class LogManager {
	actual fun clearLogs() {}
	actual fun logFlow(): Flow<LogLine> = flowOf()
}
