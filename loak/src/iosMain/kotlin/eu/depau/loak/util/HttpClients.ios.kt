package eu.depau.loak.util

// only the web engine fails with a non-Exception; here that's a VM error, never a fetch
internal actual fun isFetchError(cause: Throwable) = false
