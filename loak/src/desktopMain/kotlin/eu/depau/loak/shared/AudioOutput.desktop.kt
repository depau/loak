package eu.depau.loak.shared

import dev.nucleusframework.rodio.PlaybackCallback
import dev.nucleusframework.rodio.RodioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The rodio output, opened on first use and reopened after a failed open. Without an
 * audio device (a box with no sound card, a sound server that isn't running)
 * `RodioPlayer()` throws, which used to crash the app at startup. Until a device
 * opens, the output reads as idle and paused, and starting a source throws, which the
 * player reports like any source that failed to start.
 *
 * Every call into rodio blocks its caller: opening a device takes as long as the device
 * does, and a stream that stops feeding the sink parks the render callback inside the
 * library — and then parks the next call that needs the same lock, for tens of seconds.
 * Issued from the UI thread (a slider drag, a media key, the progress tick) one of those
 * froze the whole window; issued from the player's own loops, one parked long enough to
 * stop the app from noticing that playback had stopped. So every call hops onto
 * [dispatcher], and the reads and source starts the player steers by are bounded: a call
 * that doesn't come back in time reads as "no answer" ([null]) or fails the start, and the
 * caller carries on. The parked call keeps its thread until it returns — there is no way
 * to answer it — which is why nothing issues one of those in a loop.
 */
internal class AudioOutput(private val callback: PlaybackCallback) {
	/** The engine is driven from here; [Dispatchers.IO] so one parked call can't starve the UI. */
	private val dispatcher = Dispatchers.IO

	/** Holds the calls that ran past their timeout, so abandoning one doesn't cancel the rest. */
	private val calls = CoroutineScope(SupervisorJob() + dispatcher)

	/** The read that is still parked, if any (see [read]). */
	@Volatile
	private var pendingRead: Deferred<*>? = null

	@Volatile
	private var player: RodioPlayer? = null

	/** Remembered so an output opened later still has the volume the user set. */
	@Volatile
	private var volume = 1f

	/**
	 * Creates the player outside the lock: a `RodioPlayer()` that parks (a device being torn
	 * down makes it wait for the native lock, for tens of seconds) must not hold the monitor
	 * that [close] needs — the player drops a dead output from its watchdog, and the UI thread
	 * closes it on exit. Two starts racing can create two players; the loser is dropped.
	 */
	private fun open(): RodioPlayer {
		player?.let { return it }
		val created = try {
			RodioPlayer()
		} catch (e: LinkageError) {
			// the native library itself can't load (libasound missing): not fatal either
			throw IllegalStateException("No audio output", e)
		}
		created.setCallback(callback)
		created.setVolume(volume)
		synchronized(this) {
			val existing = player
			if (existing != null) {
				created.close()
				return existing
			}
			player = created
			return created
		}
	}

	/**
	 * Runs [block] on the engine, but returns [null] if it hasn't answered within [timeoutMs]
	 * instead of waiting for it. The call itself keeps running on its own coroutine and applies
	 * late (a queued pause still pauses once the lock it waited for is free) — only the caller's
	 * wait is bounded.
	 */
	private suspend fun <T> callWithin(timeoutMs: Long, block: () -> T): T? =
		withTimeoutOrNull(timeoutMs) { calls.async { block() }.await() }

	suspend fun playUrl(url: String, loop: Boolean) {
		startSource { open().playUrl(url, loop) }
	}

	suspend fun playFile(path: String, loop: Boolean) {
		startSource { open().playFile(path, loop) }
	}

	suspend fun playRadio(url: String) {
		startSource { open().playRadio(url) }
	}

	/**
	 * Hands [block] a source, failing the start if the output doesn't take it in time. A start
	 * parked in the native library does come back in the end, and then it takes its source: the
	 * player pauses the output right after failing, and that pause queues behind the parked call,
	 * so what it took is normally silenced before it can be heard. If the parking was in the
	 * player's own creation, there was nothing to pause yet — the source then plays late, under a
	 * UI that says paused, until the next play/pause press resyncs it.
	 *
	 * ponytail: that late start is left alone. If it ever shows up as "audio plays from nowhere",
	 * give the output a generation counter, bumped by [close], and drop sources from older ones.
	 */
	private suspend fun startSource(block: () -> Unit) {
		if (callWithin(START_TIMEOUT_MS, block) == null) {
			throw IllegalStateException("the audio output is not responding")
		}
	}

	suspend fun play() = withContext(dispatcher) { player?.play() }

	suspend fun pause() = withContext(dispatcher) { player?.pause() }

	suspend fun clear() = withContext(dispatcher) { player?.clear() }

	suspend fun seekToMs(ms: Long) = withContext(dispatcher) { player?.seekToMs(ms) }

	suspend fun setVolume(value: Float) {
		volume = value
		withContext(dispatcher) { player?.setVolume(value) }
	}

	suspend fun getPositionMs(): Long? = read { player?.getPositionMs() ?: 0L }

	suspend fun isSeekable(): Boolean = read { player?.isSeekable() ?: false } ?: false

	suspend fun isPaused(): Boolean? = read { player?.isPaused() ?: true }

	suspend fun isEmpty(): Boolean? = read { player?.isEmpty() ?: true }

	/**
	 * Reads the engine, at most one call at a time. A read that ran past its timeout is parked
	 * inside the library and will not come back until whatever holds the lock lets go, so the
	 * next one would only queue up behind it (enough of those and the app's other IO starves):
	 * report "no answer" instead until the parked call returns, which it does by itself.
	 */
	private suspend fun <T> read(block: () -> T): T? {
		pendingRead?.let { if (it.isActive) return null }
		val call = calls.async { block() }
		pendingRead = call
		return withTimeoutOrNull(READ_TIMEOUT_MS) { call.await() }
	}

	/** Drops the output. Doesn't wait for a wedged callback, so it is safe to call from anywhere. */
	@Synchronized
	fun close() {
		player?.close()
		player = null
	}

	private companion object {
		/** Reads take a lock; a healthy engine answers in microseconds. */
		const val READ_TIMEOUT_MS = 2_000L

		/** A start opens a file or a connection, but a healthy one never takes this long. */
		const val START_TIMEOUT_MS = 10_000L
	}
}
