package eu.depau.loak.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsStalledTest {
	private val zero = PlaybackProgress(positionMs = 0, bufferedMs = 0)
	private val posAt = { ms: Long -> PlaybackProgress(positionMs = ms, bufferedMs = ms) }

	/** Buffering at 0:00 with nothing coming in on a slow/transcoded stream. */
	@Test
	fun freezesInBufferingAtZero() {
		assertTrue(isStalled(zero, zero, ended = false))
	}

	/** READY and reporting progress but frozen (the pause-icon-stuck case). */
	@Test
	fun freezesReadyWhilePlaying() {
		val at = posAt(12_000)
		assertTrue(isStalled(at, at, ended = false))
	}

	/** Position advancing is progress, not a stall. */
	@Test
	fun advancingPositionIsNotStalled() {
		assertFalse(isStalled(zero, posAt(1_000), ended = false))
	}

	/** New data buffered ahead is progress even if the position hasn't moved. */
	@Test
	fun bufferingInNewDataIsNotStalled() {
		assertFalse(isStalled(zero, PlaybackProgress(positionMs = 0, bufferedMs = 5_000), ended = false))
	}

	/** A track that ended naturally is never a stall. */
	@Test
	fun endedIsNeverStalled() {
		assertFalse(isStalled(posAt(200_000), posAt(200_000), ended = true))
	}
}
