package eu.depau.loak.di

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for the two-layer Sentry censoring.
 *
 * Layer 1 must redact every spelling Java/Ktor/OkHttp/Android print server
 * identifiers in (URLs, host/port, bare IPs and — the LOAK-8 regression — the
 * quoted `Unable to resolve host "…"` form). Layer 2 must catch the user's real
 * identifiers by exact match even when layer 1 misses a variant (censorSentryText
 * reports them as `breached`), so nothing leaks and a breach alert fires.
 */
class SentrySanitizerTest {

	@BeforeTest
	fun resetPii() = clearPiiForTests()

	// region layer 1: every printer spelling of a server identifier

	@Test
	fun redactsFullUrlWithSignedParams() {
		val input = "Connect timeout has expired [url=https://starrs.depau.eu/navidrome/rest/getGenres.view" +
			"?f=json&v=1.16.1&c=Lo%27ak&u=depau&t=4a5a122b2c7125ea9049f19445c60884&s=ll69kk, connect_timeout=unknown ms]"
		val out = input.sanitizeSentryText()
		assertEquals(
			"Connect timeout has expired [url=[REDACTED_URL], connect_timeout=unknown ms]",
			out
		)
	}

	@Test
	fun redactsBareCredentialAssignments() {
		val out = "token=deadbeef username=bob salt=abc".sanitizeSentryText()
		assertEquals("token=[REDACTED] username=[REDACTED] salt=[REDACTED]", out)
	}

	@Test
	fun redactsInetSocketAddressChain() {
		// `Failed to connect to navikek.puntokek.com/172.67.183.168:443`
		val out = "Failed to connect to navikek.puntokek.com/172.67.183.168:443".sanitizeSentryText()
		assertEquals("Failed to connect to [REDACTED_HOST]", out)
	}

	@Test
	fun redactsHostWithParenthesisedPort() {
		val out = "failed to connect to starrs.depau.eu (port 443)".sanitizeSentryText()
		assertEquals("failed to connect to [REDACTED_HOST]", out)
	}

	@Test
	fun redactsQuotedResolveHost() {
		// LOAK-8 regression: Android spellings of DNS failure leak the domain in quotes.
		val out =
			"""UnknownHostException: Unable to resolve host "starrs.depau.eu": No address associated with hostname"""
				.sanitizeSentryText()
		assertEquals(
			"""UnknownHostException: Unable to resolve host "[REDACTED_HOST]": No address associated with hostname""",
			out
		)
	}

	@Test
	fun redactsUnknownHostBarePattern() {
		val out = "UnknownHostException: navikek.puntokek.com".sanitizeSentryText()
		assertEquals("UnknownHostException: [REDACTED_HOST]", out)
	}

	@Test
	fun redactsBareIpv4() {
		val out = "10.0.0.254 refused".sanitizeSentryText()
		assertEquals("[REDACTED_HOST] refused", out)
	}

	@Test
	fun redactsFileUriAndLocalPath() {
		// LOAK-10/Z regression: the desktop audio store's file path leaks the home dir + user.
		val out = "URI is not absolute: file:/Users/ddepau/Library/Application Support/Loak/audio/ab12%40.mp3".sanitizeSentryText()
		assertEquals("URI is not absolute: [REDACTED_PATH]", out)
	}

	@Test
	fun redactsWindowsAndFileSlashVariants() {
		val out = "found at file:C:\\Users\\ddepau\\AppData\\Loak\\x.mp3\nand file:///tmp/a 1.mp3".sanitizeSentryText()
		assertEquals("found at [REDACTED_PATH]\nand [REDACTED_PATH]", out)
	}

	// endregion

	// region layer 2: exact-match safety net on the user's real identifiers

	@Test
	fun layer2ScrubsKnownPiiThatLayer1Misses() {
		recordPii("https://my-playserver.lan:4533/music")
		// a made-up spelling layer 1 doesn't know (bare twin-label host in prose)
		val raw = "unexpected marker my-playserver.lan in the stream"
		val c = censorSentryText(raw)
		assertEquals("unexpected marker [REDACTED_HOST] in the stream", c.text)
		assertEquals(listOf("my-playserver.lan"), c.breached.distinct())
	}

	@Test
	fun layer2IsSilentWhenLayer1CoversEverything() {
		recordPii("https://starrs.depau.eu")
		val c = censorSentryText("Unable to resolve host \"starrs.depau.eu\": No address")
		assertEquals(
			"Unable to resolve host \"[REDACTED_HOST]\": No address",
			c.text
		)
		assertTrue(c.breached.isEmpty())
	}

	@Test
	fun recordPiiExtractsHostFromUrl() {
		recordPii("https://starrs.depau.eu:4533/navidrome")
		val known = sentryPiiSnapshot()
		assertTrue(known.contains("starrs.depau.eu"))
		assertTrue(known.contains("https://starrs.depau.eu:4533/navidrome"))
	}

	@Test
	fun recordPiiIgnoresJunk() {
		recordPii("", "no dots", "https://")
		assertTrue(sentryPiiSnapshot().isEmpty())
	}

	@Test
	fun redactsLoginCredentialFailureMessage() {
		// LOAK-1J: SessionManager.login surfaces its user-facing "check your credentials"
		// message in the thrown exception; the layer-1 sanitizer must redact that exact
		// string from anything Sentry sends (the wrapped SubsonicException is what's useful).
		val msg = "Failed to connect to the instance. Please check your credentials and try again."
		val out = msg.sanitizeSentryText()
		assertEquals("[REDACTED_LOGIN_ERROR]", out)
		// also inside a longer event value, case-insensitively
		val chained = "subsonic call failed: ${msg.lowercase()} (wrapped)"
		assertEquals("subsonic call failed: [REDACTED_LOGIN_ERROR] (wrapped)", chained.sanitizeSentryText())
	}

	@Test
	fun recordPiiIdentifierSeedsAndScrubsUsername() {
		// account usernames shouldn't need a dot to be protected like server hosts do
		recordPiiIdentifier("demo-user")
		assertTrue("demo-user" in sentryPiiSnapshot())
		val c = censorSentryText("auth failed for DEMO-USER in the log")
		assertEquals("auth failed for [REDACTED_HOST] in the log", c.text)
		assertEquals(listOf("demo-user"), c.breached.distinct())
	}

	@Test
	fun recordPiiIdentifierIgnoresTooShort() {
		// matches layer-2's own >= 4 length floor; a 3-char username must be skipped
		// so it can't mangle unrelated words ("rob" vs "Robert")
		recordPiiIdentifier("rob")
		assertTrue(sentryPiiSnapshot().isEmpty())
	}

	// endregion
}
