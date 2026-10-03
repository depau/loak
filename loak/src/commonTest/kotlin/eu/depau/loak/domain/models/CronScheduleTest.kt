package eu.depau.loak.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CronScheduleTest {
	@Test
	fun roundTrips() {
		listOf("0 2 * * *", "30 7 * * 1", "0 6 * * 0,6", "15 3 1 * *").forEach {
			assertEquals(it, CronSchedule.parse(it)!!.toCron())
		}
	}

	@Test
	fun readsRangesAndSunday7() {
		assertEquals(setOf(1, 2, 3, 4, 5), CronSchedule.parse("0 9 * * 1-5")!!.days)
		assertEquals(setOf(0), CronSchedule.parse("0 9 * * 7")!!.days)
	}

	@Test
	fun leavesTheRestCustom() {
		listOf("*/5 * * * *", "0 2 * 1 *", "0 2 1 * 1", "0 2 * *", "0 25 * * *").forEach {
			assertNull(CronSchedule.parse(it), it)
		}
	}
}
