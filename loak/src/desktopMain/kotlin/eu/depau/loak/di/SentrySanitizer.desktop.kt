package eu.depau.loak.di

import io.sentry.Breadcrumb
import io.sentry.ISpan
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SpanStatus
import io.sentry.protocol.Message

/**
 * Desktop (JVM) Sentry sanitizer.
 *
 * The KMP `beforeSend` is broken on JVM targets: its adapter copies the event
 * into a Kotlin Multiplatform wrapper, runs the KMP beforeSend, then writes the
 * result back with
 * [io.sentry.kotlin.multiplatform.extensions.SentryEventExtensions_jvmKt.applyKmpEvent]
 * — which never writes `exceptions` back. So exception messages (where Ktor
 * embeds the full request URL + signed auth params) are never redacted and ship
 * to Sentry verbatim (this is how the seed issue leaked `…?u=depau&t=TOKEN…`).
 *
 * Register a native `SentryOptions.beforeSend`/`beforeBreadcrumb` that scrub the
 * raw JVM event in place instead. This covers every capture path on JVM,
 * including uncaught-exception crashes captured by the native Android handler.
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

internal fun sanitizeJvmEvent(event: SentryEvent): SentryEvent? {
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

internal fun sanitizeJvmBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
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
