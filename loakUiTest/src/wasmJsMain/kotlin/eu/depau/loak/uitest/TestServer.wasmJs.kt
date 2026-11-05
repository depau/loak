@file:OptIn(ExperimentalWasmJsInterop::class)

package eu.depau.loak.uitest

// set by webApp/karma.config.d from the build
@JsFun("() => { const p = globalThis.__karma__?.config?.loakUitestPort; return p ? Number(p) : -1; }")
private external fun karmaPort(): Int

internal actual val testServerPort: Int? = karmaPort().takeIf { it > 0 }
