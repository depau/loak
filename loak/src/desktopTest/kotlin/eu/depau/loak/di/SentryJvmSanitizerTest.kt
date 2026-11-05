package eu.depau.loak.di

import io.sentry.SentryEvent
import io.sentry.protocol.SentryException
import io.sentry.protocol.SentryThread
import io.sentry.protocol.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.BeforeTest

/** JVM-side checks that thread names and tags (hosts/IPs) are scrubbed before send. */
class SentryJvmSanitizerTest {

	// other tests fill the global censor registry (any SessionManager or PreferenceManager does)
	@BeforeTest
	fun resetPii() = clearPiiForTests()

	@Test
	fun scrubsHostFromThreadNamesAndTags() {
		recordPiiServer("subsonic", "https://music.home.example")
		val event = SentryEvent().apply {
			threads = listOf(
				SentryThread().apply { name = "OkHttp starrs.depau.eu" },
				SentryThread().apply { name = "OkHttp music.home.example" },
				SentryThread().apply { name = "main" }
			)
			setTag("server_name", "192.168.3.130")
			setTag("device.state", "clean")
		}
		sanitizeJvmEvent(event)
		assertEquals("OkHttp redacted", event.threads?.get(0)?.name)
		assertEquals("OkHttp subsonic.redacted", event.threads?.get(1)?.name)
		assertEquals("main", event.threads?.get(2)?.name)
		assertEquals("redacted", event.tags?.get("server_name"))
		assertEquals("clean", event.tags?.get("device.state"))
	}

	@Test
	fun blanksLocationSoSentryCantGeolocateTheSender() {
		val event = SentryEvent().apply { user = User().apply { id = "install-id" } }
		sanitizeJvmEvent(event)
		// a non-null geo stops Sentry deriving the sender's city from its IP address
		assertNotNull(event.user?.geo)
		assertEquals("install-id", event.user?.id)
		// events without a user get one just for the blank location
		assertNotNull(SentryEvent().also(::sanitizeJvmEvent).user?.geo)
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
}
