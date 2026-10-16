package eu.depau.loak.shared

/**
 * Turns the player's progress-loop position samples into "the audio output isn't consuming the
 * source any more" decisions.
 *
 * rodio keeps reporting a playing, non-empty sink even when nothing comes out of the speakers:
 * a stalled HTTP read blocks the CoreAudio render callback (and the device then tears the stream
 * down), and a device that went away makes it never be called again. The position is the only
 * liveness signal, so a source that should be playing while the position stands still means the
 * output is gone — the app used to sit there showing a pause button over silence for ever.
 *
 * [onTick] must only be called while playback is supposed to progress (a paused, transitioning or
 * failed player legitimately stands still). A position of `null` is a tick on which the engine
 * didn't answer at all — the read itself is parked, i.e. definitely not progressing. Any movement
 * resets the episode, so the budget is spent per stall, not per output.
 */
internal class OutputStallDetector(
	/** How long the position may stand still before the output is presumed dead. */
	private val afterMs: Long = STALL_AFTER_MS,
	/** Rebuilds allowed for one stalled episode before giving up on the current track. */
	private val rebuildBudget: Int = REBUILD_BUDGET,
	private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {
	enum class Action {
		/** Nothing to do: the position moved, or it hasn't stood still long enough yet. */
		None,

		/** No progress for [afterMs]: drop the output and start the source on a fresh one. */
		Rebuild,

		/** A rebuild already failed to get the output going again: stop pretending to play. */
		GiveUp,
	}

	private var lastPositionMs = 0L
	private var lastMovedAtMs = now()
	private var rebuilds = 0

	/** A source is about to start (or playback is stopping): this episode starts over. */
	fun reset() {
		lastPositionMs = 0L
		lastMovedAtMs = now()
		rebuilds = 0
	}

	fun onTick(positionMs: Long?): Action {
		val nowMs = now()
		if (positionMs != null && positionMs != lastPositionMs) {
			lastPositionMs = positionMs
			lastMovedAtMs = nowMs
			rebuilds = 0
			return Action.None
		}
		if (nowMs - lastMovedAtMs < afterMs) return Action.None
		lastMovedAtMs = nowMs
		if (rebuilds >= rebuildBudget) return Action.GiveUp
		rebuilds++
		return Action.Rebuild
	}

	internal companion object {
		/** Long enough not to trip on a slow stream or a seek, short enough to notice quickly. */
		const val STALL_AFTER_MS = 5_000L
		const val REBUILD_BUDGET = 2
	}
}
