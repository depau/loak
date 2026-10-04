package eu.depau.loak.di

/** wasmJs ships a no-op Sentry stub, so there is nothing to sanitize. */
internal actual fun registerJvmSentrySanitizer() {}

// ponytail: no tracing here yet; bridge the native SDK's spans when it's needed.
internal actual fun startSentrySpan(parent: Any?, op: String, name: String): Any? = null

internal actual fun finishSentrySpan(span: Any, ok: Boolean, data: Map<String, Any>) {}
