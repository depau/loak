package eu.depau.loak.domain.manager

import kotlinx.coroutines.flow.Flow
import eu.depau.loak.domain.parser.LogLine

actual class LogManager {
	actual fun clearLogs() { TODO() }
	actual fun logFlow(): Flow<LogLine> = TODO()
}
