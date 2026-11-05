package eu.depau.loak.util

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpResponseValidator
import kotlinx.io.IOException

/**
 * Every Ktor client the app makes; [engine] null is Ktor's platform default (tests pass a
 * MockEngine). Clients made elsewhere (the Subsonic one) install [fetchErrorsAsExceptions].
 */
fun LoakHttpClient(
	engine: HttpClientEngine? = null,
	block: HttpClientConfig<*>.() -> Unit = {}
): HttpClient {
	val config: HttpClientConfig<*>.() -> Unit = {
		fetchErrorsAsExceptions()
		block()
	}
	return if (engine != null) HttpClient(engine, config) else HttpClient(config)
}

/** A request that couldn't reach the server, as the web engine reports it (see [fetchErrorsAsExceptions]). */
class FetchException(cause: Throwable) : IOException(cause.message, cause)

/**
 * On web a request that can't reach the server fails with a `kotlin.Error` (or a
 * `JsError`), not an [Exception]: it slips past the app's `catch (e: Exception)` and
 * crashes its coroutine, and one started from composition (`LaunchedEffect`,
 * `produceState`) takes Compose's recomposer down with it, freezing the whole page.
 * This rethrows it as a [FetchException], the IOException other engines throw.
 * No-op on the other platforms.
 */
fun HttpClientConfig<*>.fetchErrorsAsExceptions() = HttpResponseValidator {
	handleResponseExceptionWithRequest { cause, _ ->
		if (cause !is Exception && isFetchError(cause)) throw FetchException(cause)
	}
}

internal expect fun isFetchError(cause: Throwable): Boolean
