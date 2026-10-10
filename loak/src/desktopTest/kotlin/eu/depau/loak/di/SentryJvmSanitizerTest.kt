package eu.depau.loak.di

import io.sentry.SentryEvent
import io.sentry.protocol.SentryThread
import kotlin.test.Test
import kotlin.test.assertEquals

/** JVM-side checks that thread names and tags (hosts/IPs) are scrubbed before send. */
class SentryJvmSanitizerTest {

	@Test
	fun scrubsHostFromThreadNamesAndTags() {
		val event = SentryEvent().apply {
			threads = listOf(
				SentryThread().apply { name = "OkHttp starrs.depau.eu" },
				SentryThread().apply { name = "main" }
			)
			setTag("server_name", "192.168.3.130")
			setTag("device.state", "clean")
		}
		sanitizeJvmEvent(event)
		assertEquals("OkHttp [REDACTED_HOST]", event.threads?.get(0)?.name)
		assertEquals("main", event.threads?.get(1)?.name)
		assertEquals("[REDACTED_HOST]", event.tags?.get("server_name"))
		assertEquals("clean", event.tags?.get("device.state"))
	}
}
