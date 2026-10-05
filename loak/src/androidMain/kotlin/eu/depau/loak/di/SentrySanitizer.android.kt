package eu.depau.loak.di

import io.sentry.Breadcrumb
import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SpanStatus

/**
 * Android Sentry sanitizer.
 *
 * Same rationale as `SentrySanitizer.desktop.kt`: the KMP `beforeSend` drops
 * exception scrubbing on JVM targets, so uncaught-exception crashes (captured
 * by the native Android `UncaughtExceptionHandler`, e.g. the LOAK-5
 * ConnectTimeoutException) ship the raw Ktor URL + signed auth params.
 *
 * Register a native `SentryAndroidOptions.beforeSend`/`beforeBreadcrumb` that
 * scrub the raw JVM event in place. Runs after `Sentry.init`; the options list
 * is not locked at that point.
 */
internal actual fun fireBreachAlert(message: String) {
	// Sentry.captureMessage from a throwaway thread: capturing from inside the
	// running beforeSend would re-enter it (and its sibling callbacks).
	Thread {
		io.sentry.Sentry.setTag(CENSOR_BREACH_TAG, "1")
		io.sentry.Sentry.captureMessage(message)
	}.start()
}

internal actual fun registerJvmSentrySanitizer() {
	val options = Sentry.getCurrentHub().options
	options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
		sanitizeJvmEvent(event)
	}
	options.beforeBreadcrumb = io.sentry.SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
		sanitizeJvmBreadcrumb(breadcrumb)
	}
	// our spans carry no URLs, but drop any auto-instrumented one that does
	options.beforeSendTransaction = io.sentry.SentryOptions.BeforeSendTransactionCallback { tx, _ ->
		tx.spans.removeAll { it.description?.contains("://") == true }
		tx
	}
}

internal actual fun startSentrySpan(parent: Any?, op: String, name: String): Any? =
	(parent as? ISpan)?.startChild(op, name) ?: Sentry.startTransaction(name, op)

internal actual fun finishSentrySpan(span: Any, ok: Boolean, data: Map<String, Any>) {
	span as ISpan
	data.forEach(span::setData)
	span.finish(if (ok) SpanStatus.OK else SpanStatus.INTERNAL_ERROR)
}

private fun sanitizeJvmEvent(event: SentryEvent): SentryEvent? {
	// expected, already-handled failures (offline, cancelled, unsupported media,
	// stale desktop continuation): drop the event outright so Sentry stays clean
	val exceptions = event.exceptions
	if (exceptions != null && exceptions.isNotEmpty() &&
		exceptions.all { isNoiseEvent(it.type, it.value) }
	) {
		return null
	}
	val breached = mutableListOf<String>()
	fun scrub(s: String?): String? {
		if (s == null) return null
		val c = censorSentryText(s)
		breached += c.breached
		return c.text
	}
	event.message?.let { msg ->
		msg.message = scrub(msg.message)
		msg.formatted = scrub(msg.formatted)
		msg.params?.let { params -> msg.params = params.map { scrub(it) } }
	}
	event.exceptions?.forEach { ex ->
		ex.value = scrub(ex.value)
	}
	event.transaction = scrub(event.transaction)
	if (breached.isNotEmpty()) {
		// layer 2 had to scrub a real identifier: flag the shipping event and alert
		event.setTag(CENSOR_BREACH_TAG, "1")
		reportCensorshipBreach(breached.distinct())
	}
	return event
}

private fun sanitizeJvmBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
	breadcrumb.message = breadcrumb.message?.sanitizeSentryText()
	breadcrumb.category = breadcrumb.category?.sanitizeSentryText()
	val breached = mutableListOf<String>()
	breadcrumb.data.forEach { (key, value) ->
		if (value is String) {
			val c = censorSentryText(value)
			breached += c.breached
			breadcrumb.setData(key, c.text)
		}
	}
	if (breached.isNotEmpty()) reportCensorshipBreach(breached.distinct())
	return breadcrumb
}
