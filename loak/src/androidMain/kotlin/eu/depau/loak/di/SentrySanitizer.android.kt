package eu.depau.loak.di

import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryEvent

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
