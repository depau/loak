package eu.depau.loak.domain.manager

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import eu.depau.loak.domain.parser.LogLine

/**
 * Desktop: no logcat; keeps the last ~200 lines in memory so the in-app log
 * screen works. Mirrors what Android's Logcat-backed LogManager reports.
 */
actual class LogManager {
	private val logLines = ArrayDeque<LogLine>()

	actual fun clearLogs() {
		logLines.clear()
	}

	actual fun logFlow(): Flow<LogLine> = flow {
		logLines.forEach { emit(it) }
	}
}
