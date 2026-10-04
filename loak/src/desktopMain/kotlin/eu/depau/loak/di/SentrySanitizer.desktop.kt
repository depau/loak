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

internal fun sanitizeJvmBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
	breadcrumb.message = breadcrumb.message?.sanitizeSentryText()
	breadcrumb.category = breadcrumb.category?.sanitizeSentryText()
	breadcrumb.data.forEach { (key, value) ->
		if (value is String) {
			breadcrumb.setData(key, value.sanitizeSentryText())
		}
	}
	return breadcrumb
}
