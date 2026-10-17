package eu.depau.loak.shared

import eu.depau.loak.shared.OutputStallDetector.Action.GiveUp
import eu.depau.loak.shared.OutputStallDetector.Action.None
import eu.depau.loak.shared.OutputStallDetector.Action.Rebuild
import kotlin.test.Test
import kotlin.test.assertEquals

class OutputStallDetectorTest {
	private var nowMs = 0L
	private val detector = OutputStallDetector(
		afterMs = OutputStallDetector.STALL_AFTER_MS,
		startGraceMs = OutputStallDetector.START_GRACE_MS,
		rebuildBudget = OutputStallDetector.REBUILD_BUDGET,
		now = { nowMs }
	)

	@Test
	fun `a position that keeps moving never trips`() {
		repeat(40) {
			nowMs += 250
			assertEquals(None, detector.onTick(it * 250L))
		}
	}

	@Test
	fun `a frozen position rebuilds the output, then gives up`() {
		detector.onTick(1_000) // anchored: the position is where it is
		nowMs += OutputStallDetector.STALL_AFTER_MS
		assertEquals(Rebuild, detector.onTick(1_000))
		// the rebuilt source never starts feeding the output either: it gets the grace, then
		// the detector gives up on the track rather than rebuilding for ever
		nowMs += OutputStallDetector.START_GRACE_MS
		assertEquals(Rebuild, detector.onTick(1_000))
		nowMs += OutputStallDetector.START_GRACE_MS
		assertEquals(GiveUp, detector.onTick(1_000))
		// and it stays given up: no rebuild loop while the output is dead
		nowMs += OutputStallDetector.START_GRACE_MS
		assertEquals(GiveUp, detector.onTick(1_000))
	}

	@Test
	fun `a read the engine never answered counts as no progress`() {
		detector.onTick(1_000) // the source was playing
		nowMs += OutputStallDetector.STALL_AFTER_MS
		assertEquals(Rebuild, detector.onTick(null))
	}

	@Test
	fun `a source that has not produced a sample yet gets the longer start grace`() {
		nowMs += OutputStallDetector.STALL_AFTER_MS
		assertEquals(None, detector.onTick(0)) // still connecting: not a stall
		assertEquals(None, detector.onTick(null)) // and the engine not answering is not one either
		nowMs += OutputStallDetector.START_GRACE_MS
		assertEquals(Rebuild, detector.onTick(0)) // ten seconds on and still nothing: broken
		// the source the rebuild started is given the grace again, not the stall window
		nowMs += OutputStallDetector.STALL_AFTER_MS
		assertEquals(None, detector.onTick(0))
	}

	@Test
	fun `movement after a rebuild refills the budget`() {
		detector.onTick(1_000)
		nowMs += OutputStallDetector.STALL_AFTER_MS
		assertEquals(Rebuild, detector.onTick(1_000))
		assertEquals(None, detector.onTick(1_250)) // moving: refills
		nowMs += OutputStallDetector.STALL_AFTER_MS
		assertEquals(Rebuild, detector.onTick(1_250))
	}

	@Test
	fun `reset gives a new source its own grace, window and budget`() {
		detector.onTick(1_000)
		nowMs += OutputStallDetector.STALL_AFTER_MS
		assertEquals(Rebuild, detector.onTick(1_000))
		nowMs += OutputStallDetector.START_GRACE_MS
		assertEquals(Rebuild, detector.onTick(1_000))
		nowMs += OutputStallDetector.START_GRACE_MS
		assertEquals(GiveUp, detector.onTick(1_000))

		detector.reset()
		// a fresh start is allowed its own rebuild even though the last episode gave up
		assertEquals(None, detector.onTick(0))
		nowMs += OutputStallDetector.START_GRACE_MS
		assertEquals(Rebuild, detector.onTick(0))
	}
}
