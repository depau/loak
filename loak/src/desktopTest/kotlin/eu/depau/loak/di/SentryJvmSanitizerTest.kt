package eu.depau.loak.di

import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryThread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

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

	@Test
	fun dropsStaleDesktopContinuationChain() {
		val event = SentryEvent().apply {
			exceptions = mutableListOf(
				SentryException().apply {
					type = "kotlinx.coroutines.internal.DiagnosticCoroutineContextException"
				},
				SentryException().apply {
					type = "java.lang.NoClassDefFoundError"
					value = "eu.depau.loak.shared.MediaPlayerViewModel\$checkAndAutoFillQueue\$1"
				}
			)
		}

		assertNull(sanitizeJvmEvent(event))
	}

	@Test
	fun dropsHandledAudioTimeoutOnly() {
		val timeout = SentryEvent().apply {
			exceptions = mutableListOf(
				SentryException().apply {
					type = "java.lang.IllegalStateException"
					value = "the audio output is not responding"
				}
			)
		}
		val unexpected = SentryEvent().apply {
			exceptions = mutableListOf(
				SentryException().apply {
					type = "java.lang.IllegalStateException"
					value = "database is corrupt"
				}
			)
		}

		assertNull(sanitizeJvmEvent(timeout))
		assertSame(unexpected, sanitizeJvmEvent(unexpected))
	}
}
