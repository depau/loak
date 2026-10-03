package eu.depau.loak.domain.models

/**
 * The cron expressions people pick from a schedule sheet: every day, some weekdays or one day
 * of the month, at a time. Anything else stays a custom expression ([parse] returns null).
 */
data class CronSchedule(
	val every: Every,
	val hour: Int,
	val minute: Int,
	/** Cron weekdays, 0 = Sunday; used when [every] is [Every.Week]. */
	val days: Set<Int> = setOf(1),
	val dayOfMonth: Int = 1
) {
	enum class Every { Day, Week, Month }

	fun toCron(): String = when (every) {
		Every.Day -> "$minute $hour * * *"
		Every.Week -> "$minute $hour * * ${days.sorted().joinToString(",")}"
		Every.Month -> "$minute $hour $dayOfMonth * *"
	}

	companion object {
		fun parse(expr: String): CronSchedule? {
			val f = expr.trim().split(Regex("\\s+"))
			if (f.size != 5 || f[3] != "*") return null
			val minute = f[0].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
			val hour = f[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
			return when {
				f[2] == "*" && f[4] == "*" -> CronSchedule(Every.Day, hour, minute)
				f[2] == "*" -> CronSchedule(Every.Week, hour, minute, days = weekdays(f[4]) ?: return null)
				f[4] == "*" -> CronSchedule(
					Every.Month, hour, minute,
					dayOfMonth = f[2].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
				)
				else -> null
			}
		}

		/** "1,3-5" -> {1, 3, 4, 5}; 7 is Sunday too. */
		private fun weekdays(field: String): Set<Int>? = field.split(",").flatMap { part ->
			val ends = part.split("-").map { it.toIntOrNull()?.takeIf { d -> d in 0..7 } ?: return null }
			when (ends.size) {
				1 -> ends
				2 -> (ends[0]..ends[1]).toList()
				else -> return null
			}
		}.map { it % 7 }.toSet().takeIf { it.isNotEmpty() }
	}
}
