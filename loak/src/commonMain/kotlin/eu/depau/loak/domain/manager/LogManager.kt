package eu.depau.loak.domain.manager

import kotlinx.coroutines.flow.Flow
import eu.depau.loak.domain.parser.LogLine

expect class LogManager {
	fun clearLogs()
	fun logFlow(): Flow<LogLine>
}
