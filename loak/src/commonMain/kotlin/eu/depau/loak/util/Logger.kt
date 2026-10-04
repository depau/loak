package eu.depau.loak.util

import eu.depau.loak.di.isSentryEnabled
import eu.depau.loak.di.isNoiseException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Report an error to Sentry (no-op when Sentry isn't initialized / DSN unset).
 * Kept in [Logger] so the four platform implementations can all forward caught
 * errors to Crash Reporting from one place.
 */
fun captureSentryError(throwable: Throwable?) {
	if (throwable == null) return
	if (!isSentryEnabled()) return
	// cancellation is control flow, not a bug — never report it
	if (throwable is CancellationException) return
	// expected, already-handled failures (offline, cancelled, unsupported media…):
	// drop at the source so they never reach the send boundary
	if (isNoiseException(throwable)) return
	io.sentry.kotlin.multiplatform.Sentry.captureException(throwable)
}

expect object Logger {
	fun e(tag: String, msg: String, tr: Throwable? = null)
	fun i(tag: String, msg: String, tr: Throwable? = null)
	fun w(tag: String, msg: String, tr: Throwable? = null)
}
