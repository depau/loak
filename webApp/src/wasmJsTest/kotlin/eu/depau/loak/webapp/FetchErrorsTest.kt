package eu.depau.loak.webapp

import eu.depau.loak.util.FetchException
import eu.depau.loak.util.LoakHttpClient
import io.ktor.client.engine.js.JsError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.request.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * A server the browser can't reach must fail the request with an [Exception]: the web
 * engine's own `Error`/`JsError` escaped every `catch (e: Exception)`, and from
 * composition it froze the whole page (LOAK-2V).
 */
class FetchErrorsTest {
	private suspend fun failWith(error: Throwable) =
		LoakHttpClient(MockEngine { throw error }).get("https://server.invalid/rest/ping.view")

	@Test
	fun fetchRejectionIsAnException() = runTest {
		assertFailsWith<FetchException> { failWith(Error("Fail to fetch", JsError("TypeError".toJsString()))) }
	}

	@Test
	fun bodyStreamFailureIsAnException() = runTest {
		assertFailsWith<FetchException> { failWith(JsError("TypeError".toJsString())) }
	}
}
