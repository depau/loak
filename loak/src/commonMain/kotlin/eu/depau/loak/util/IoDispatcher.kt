package eu.depau.loak.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher for blocking-style IO work (DB, file, network).
 *
 * JVM/native back it with `Dispatchers.IO`. Kotlin/Wasm has no IO dispatcher
 * (single-threaded runtime), so its actual maps to `Dispatchers.Default`.
 */
internal expect val IoDispatcher: CoroutineDispatcher
