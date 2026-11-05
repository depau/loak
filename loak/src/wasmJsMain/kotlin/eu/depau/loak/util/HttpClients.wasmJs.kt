package eu.depau.loak.util

import io.ktor.client.engine.js.JsError

// fetch() rejecting: `Error("Fail to fetch", JsError)`; the body stream failing: a bare JsError
internal actual fun isFetchError(cause: Throwable) = cause is JsError || cause.cause is JsError
