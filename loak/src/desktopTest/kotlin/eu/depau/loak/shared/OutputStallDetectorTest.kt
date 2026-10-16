package eu.depau.loak.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class OutputStallDetectorTest {
	private var nowMs = 0L
	private val detector = OutputStallDetector(
		afterMs = 5_000,
		rebuildBudget = 2,
		now = { nowMs }
	)

	@Test
	fun `a position that keeps moving never trips`() {
		repeat(40) {
			nowMs += 250
			assertEquals(
				OutputStallDetector.Action.None,
				detector.onTick(it * 250L)
			)
		}
	}

	@Test
	fun `a frozen position rebuilds the output, then gives up`() {
		detector.onTick(1_000) // anchored: the position is where it is
		nowMs += 5_000
		assertEquals(OutputStallDetector.Action.Rebuild, detector.onTick(1_000))
		nowMs += 5_000
		assertEquals(OutputStallDetector.Action.Rebuild, detector.onTick(1_000))
		nowMs += 5_000
		assertEquals(OutputStallDetector.Action.GiveUp, detector.onTick(1_000))
		// and it stays given up: no rebuild loop while the output is dead
		nowMs += 5_000
		assertEquals(OutputStallDetector.Action.GiveUp, detector.onTick(1_000))
	}

	@Test
	fun `a read the engine never answered counts as no progress`() {
		nowMs += 5_000
		assertEquals(OutputStallDetector.Action.Rebuild, detector.onTick(null))
	}

	@Test
	fun `movement after a rebuild refills the budget`() {
		detector.onTick(1_000)
		nowMs += 5_000
		assertEquals(OutputStallDetector.Action.Rebuild, detector.onTick(1_000))
		assertEquals(OutputStallDetector.Action.None, detector.onTick(1_250)) // moving: refills
		nowMs += 5_000
		assertEquals(OutputStallDetector.Action.Rebuild, detector.onTick(1_250))
	}

	@Test
	fun `reset gives a new source its own window and budget`() {
		detector.onTick(1_000)
		nowMs += 5_000
		assertEquals(OutputStallDetector.Action.Rebuild, detector.onTick(1_000))
		nowMs += 5_000
		assertEquals(OutputStallDetector.Action.Rebuild, detector.onTick(1_000))
		nowMs += 5_000
		assertEquals(OutputStallDetector.Action.GiveUp, detector.onTick(1_000))

		detector.reset()
		// a fresh start is allowed its own rebuild even though the last episode gave up
		assertEquals(OutputStallDetector.Action.None, detector.onTick(0))
		nowMs += 4_000
		assertEquals(OutputStallDetector.Action.None, detector.onTick(0))
		nowMs += 1_000
		assertEquals(OutputStallDetector.Action.Rebuild, detector.onTick(0))
	}
}
