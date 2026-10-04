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

private fun sanitizeJvmEvent(event: SentryEvent): SentryEvent {
	event.message?.let { msg ->
		msg.message = msg.message?.sanitizeSentryText()
		msg.formatted = msg.formatted?.sanitizeSentryText()
		msg.params?.let { params -> msg.params = params.map { it.sanitizeSentryText() } }
	}
	event.exceptions?.forEach { ex ->
		ex.value = ex.value?.sanitizeSentryText()
	}
	event.transaction = event.transaction?.sanitizeSentryText()
	return event
}

private fun sanitizeJvmBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
	breadcrumb.message = breadcrumb.message?.sanitizeSentryText()
	breadcrumb.category = breadcrumb.category?.sanitizeSentryText()
	breadcrumb.data.forEach { (key, value) ->
		if (value is String) {
			breadcrumb.setData(key, value.sanitizeSentryText())
		}
	}
	return breadcrumb
}
