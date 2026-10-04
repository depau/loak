package eu.depau.loak.di

/**
 * iOS uses the KMP beforeSend (its Cocoa bridge is a separate pipeline from the
 * JVM one that drops exception scrubbing), so no extra native sanitizer is
 * needed here.
 */
internal actual fun registerJvmSentrySanitizer() {}

// ponytail: no tracing here yet; bridge the native SDK's spans when it's needed.
internal actual fun startSentrySpan(parent: Any?, op: String, name: String): Any? = null

internal actual fun finishSentrySpan(span: Any, ok: Boolean, data: Map<String, Any>) {}
