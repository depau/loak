package eu.depau.loak.di

/**
 * iOS uses the KMP beforeSend (its Cocoa bridge is a separate pipeline from the
 * JVM one that drops exception scrubbing), so no extra native sanitizer is
 * needed here.
 */
internal actual fun registerJvmSentrySanitizer() {}
