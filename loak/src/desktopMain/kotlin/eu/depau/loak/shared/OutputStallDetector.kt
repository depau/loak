package eu.depau.loak.shared

/**
 * Turns the player's progress-loop position samples into "the audio output isn't consuming the
 * source any more" decisions.
 *
 * rodio keeps reporting a playing, non-empty sink even when nothing comes out of the speakers:
 * a stalled HTTP read starves the source (the output plays silence meanwhile, which the patched
 * native leaves out of the position), and a device that went away makes it never be called again.
 * The position is the only liveness signal, so a source that should be playing while the position
 * stands still means the output is gone — the app used to sit there showing a pause button over
 * silence for ever.
 *
 * [onTick] must only be called while playback is supposed to progress (a paused, transitioning or
 * failed player legitimately stands still). A position of `null` is a tick on which the engine
 * didn't answer at all — the read itself is parked, i.e. definitely not progressing. Any movement
 * resets the episode, so the budget is spent per stall, not per output.
 *
 * A source that has not moved at all yet is only "connecting" (a transcode on the server and the
 * first HTTP read take seconds, and the engine doesn't answer while it is fetching them), so it
 * gets [startGraceMs] instead of [afterMs]: the short window is only applied once the output has
 * proven it is consuming the source.
 */
internal class OutputStallDetector(
	/** How long the position may stand still, having moved already, before the output is presumed dead. */
	private val afterMs: Long = STALL_AFTER_MS,
	/** How long a source may take to produce its first movement before that in turn is a stall. */
	private val startGraceMs: Long = START_GRACE_MS,
	/** Rebuilds allowed for one stalled episode before giving up on the current track. */
	private val rebuildBudget: Int = REBUILD_BUDGET,
	private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {
	enum class Action {
		/** Nothing to do: the position moved, or it hasn't stood still long enough yet. */
		None,

		/** No progress for [afterMs] (or [startGraceMs] before the first): drop the output and
		 *  start the source on a fresh one. */
		Rebuild,

		/** A rebuild already failed to get the output going again: stop pretending to play. */
		GiveUp,
	}

	private var lastPositionMs = 0L
	private var lastMovedAtMs = now()
	private var rebuilds = 0

	/** Whether the current source has moved at all: until it has, a standstill is a slow start. */
	private var hasMoved = false

	/** A source is about to start (or playback is stopping): this episode starts over. */
	fun reset() {
		lastPositionMs = 0L
		lastMovedAtMs = now()
		rebuilds = 0
		hasMoved = false
	}

	fun onTick(positionMs: Long?): Action {
		val nowMs = now()
		if (positionMs != null && positionMs != lastPositionMs) {
			lastPositionMs = positionMs
			lastMovedAtMs = nowMs
			rebuilds = 0
			hasMoved = true
			return Action.None
		}
		if (nowMs - lastMovedAtMs < if (hasMoved) afterMs else startGraceMs) return Action.None
		lastMovedAtMs = nowMs
		hasMoved = false
		if (rebuilds >= rebuildBudget) return Action.GiveUp
		rebuilds++
		return Action.Rebuild
	}

	internal companion object {
		/** The output is presumed dead after this much silence. Kept short: every one of these
		 *  milliseconds is a millisecond of silence the listener hears before the rebuild starts. */
		const val STALL_AFTER_MS = 1_500L

		/** A source that never produced its first sample is broken too, just slower to prove it:
		 *  this is as long as a stream has ever been seen taking to come up, so a rebuild's own
		 *  source (and a slow connect in general) isn't torn down while it is still connecting. */
		const val START_GRACE_MS = 5_000L

		const val REBUILD_BUDGET = 2
	}
}
