package eu.depau.loak.di

/** Web events are scrubbed by `@sentry/browser`'s beforeSend (see SentryWeb.kt). */
internal actual fun registerJvmSentrySanitizer() {}

// ponytail: no tracing here yet; bridge the native SDK's spans when it's needed.
internal actual fun startSentrySpan(parent: Any?, op: String, name: String): Any? = null

internal actual fun finishSentrySpan(span: Any, ok: Boolean, data: Map<String, Any>) {}
