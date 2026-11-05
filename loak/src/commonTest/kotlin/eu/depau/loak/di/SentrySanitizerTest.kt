package eu.depau.loak.di

import eu.depau.loak.domain.history.InMemorySettings
import com.russhwolf.settings.set
import eu.depau.loak.domain.manager.PreferenceManager
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
	fun keepsTheApiTargetOfTheUsersServerOnly() {
		recordPiiServer("subsonic", "https://starrs.depau.eu:4533/navidrome")
		val input = "Connect timeout has expired [url=https://starrs.depau.eu:4533/navidrome/rest/getGenres.view" +
			"?f=json&v=1.16.1&c=Lo%27ak&u=depau&t=4a5a122b2c7125ea9049f19445c60884&s=ll69kk, connect_timeout=unknown ms]"
		val c = censorSentryText(input)
		// its own path prefix, the signed credentials and the host are gone; scheme, port and API path stay
		assertEquals(
			"Connect timeout has expired [url=https://subsonic.redacted:4533/rest/getGenres.view, connect_timeout=unknown ms]",
			c.text
		)
		assertTrue(c.breached.isEmpty())
	}

	@Test
	fun unknownHostsKeepNeitherNameNorQuery() {
		val out = "GET http://198.51.100.7:8000/live/stream?listener=jane failed".sanitizeSentryText()
		assertEquals("GET http://redacted:8000/live/stream failed", out)
	}

	@Test
	fun publicServicesStayReadableWithoutUserPathSegments() {
		recordPiiIdentifier("jane-doe")
		val c = censorSentryText("https://api.listenbrainz.org/1/user/jane-doe/listens?count=5")
		assertEquals("https://api.listenbrainz.org/1/user/[REDACTED]/listens", c.text)
		assertTrue(c.breached.isEmpty())
		// Last.fm pages are named after what the user listens to
		assertEquals(
			"https://www.last.fm/music",
			"https://www.last.fm/music/Radiohead/OK+Computer".sanitizeSentryText()
		)
	}

	@Test
	fun redactsBareCredentialAssignments() {
		val out = "token=deadbeef username=bob salt=abc".sanitizeSentryText()
		assertEquals("token=[REDACTED] username=[REDACTED] salt=[REDACTED]", out)
	}

	@Test
	fun redactsInetSocketAddressChainKeepingThePort() {
		recordPiiServer("lidarr", "http://lidarr.home.lan:8686")
		assertEquals(
			"Failed to connect to lidarr.redacted:8686",
			"Failed to connect to lidarr.home.lan/192.168.1.5:8686".sanitizeSentryText()
		)
		assertEquals(
			"Failed to connect to redacted:443",
			"Failed to connect to navikek.puntokek.com/172.67.183.168:443".sanitizeSentryText()
		)
	}

	@Test
	fun redactsHostWithParenthesisedPort() {
		val out = "failed to connect to starrs.depau.eu (port 443)".sanitizeSentryText()
		assertEquals("failed to connect to redacted:443", out)
	}

	@Test
	fun redactsQuotedResolveHost() {
		// LOAK-8 regression: Android spellings of DNS failure leak the domain in quotes.
		val out =
			"""UnknownHostException: Unable to resolve host "starrs.depau.eu": No address associated with hostname"""
				.sanitizeSentryText()
		assertEquals(
			"""UnknownHostException: Unable to resolve host "redacted": No address associated with hostname""",
			out
		)
	}

	@Test
	fun redactsUnknownHostBarePattern() {
		val out = "UnknownHostException: navikek.puntokek.com".sanitizeSentryText()
		assertEquals("UnknownHostException: redacted", out)
	}

	@Test
	fun redactsBareIpv4() {
		val out = "10.0.0.254 refused".sanitizeSentryText()
		assertEquals("redacted refused", out)
	}

	@Test
	fun redactsFileUriAndLocalPath() {
		// LOAK-10/Z regression: the desktop audio store's file path leaks the home dir + user.
		val out = "URI is not absolute: file:/Users/ddepau/Library/Application Support/Loak/audio/ab12%40.mp3".sanitizeSentryText()
		assertEquals("URI is not absolute: [REDACTED_PATH].mp3", out)
	}

	@Test
	fun redactsWindowsAndFileSlashVariants() {
		val out = "found at file:C:\\Users\\ddepau\\AppData\\Loak\\x.mp3\nand file:///tmp/a 1.mp3".sanitizeSentryText()
		assertEquals("found at [REDACTED_PATH].mp3\nand [REDACTED_PATH].mp3", out)
	}

	@Test
	fun songFileNamesGoTheirFormatStays() {
		assertEquals(
			"open failed: [REDACTED_PATH].flac (No such file or directory)",
			"open failed: /storage/emulated/0/Music/Some Artist/Some Album/01 - Some Song.flac (No such file or directory)"
				.sanitizeSentryText()
		)
		assertEquals(
			"export to C: failed: [REDACTED_PATH].opus",
			"export to C: failed: C:\\Users\\jane\\Music\\Some Song.opus".sanitizeSentryText()
		)
		assertEquals("can't decode \"[REDACTED_FILE].m4a\"", "can't decode \"Some Song (Live).m4a\"".sanitizeSentryText())
		assertEquals("[REDACTED_FILE].mp3 is too short", "01_Some_Song.MP3 is too short".sanitizeSentryText())
		assertEquals(
			"GET https://redacted/media/[REDACTED_FILE].ogg failed",
			"GET https://cdn.example.net/media/Some_Song.ogg failed".sanitizeSentryText()
		)
		// a home-dir path to anything else goes whole too
		assertEquals("cache at [REDACTED_PATH] full", "cache at /home/jane/.cache/loak full".sanitizeSentryText())
	}

	// endregion

	// region layer 2: exact-match safety net on the user's real identifiers

	@Test
	fun layer2ScrubsKnownPiiThatLayer1Misses() {
		recordPiiServer("subsonic", "https://my-playserver.lan:4533/music")
		// a made-up spelling layer 1 doesn't know (bare twin-label host in prose)
		val raw = "unexpected marker my-playserver.lan in the stream"
		val c = censorSentryText(raw)
		assertEquals("unexpected marker [REDACTED_HOST] in the stream", c.text)
		assertEquals(listOf("my-playserver.lan"), c.breached.distinct())
	}

	@Test
	fun layer2IsSilentWhenLayer1CoversEverything() {
		recordPiiServer("subsonic", "https://starrs.depau.eu")
		val c = censorSentryText("Unable to resolve host \"starrs.depau.eu\": No address")
		assertEquals(
			"Unable to resolve host \"subsonic.redacted\": No address",
			c.text
		)
		assertTrue(c.breached.isEmpty())
	}

	@Test
	fun recordPiiServerExtractsHostFromUrl() {
		recordPiiServer("subsonic", "https://starrs.depau.eu:4533/navidrome")
		val known = sentryPiiSnapshot()
		assertTrue(known.contains("starrs.depau.eu"))
		assertTrue(known.contains("https://starrs.depau.eu:4533/navidrome"))
	}

	@Test
	fun recordPiiServerIgnoresJunk() {
		listOf("", "no dots", "https://").forEach { recordPiiServer("subsonic", it) }
		assertTrue(sentryPiiSnapshot().isEmpty())
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

	@Test
	fun piiSettingsTeachTheCensorTheirStoredAndNewValues() {
		val settings = InMemorySettings()
		settings["lastFmUsername"] = "scrobbler-jane"
		val prefs = PreferenceManager(settings)
		// a value saved in an earlier run is known from startup, before any code reads it
		assertTrue("scrobbler-jane" in sentryPiiSnapshot())
		prefs.koitoUrl = "https://koito.home.example"
		assertTrue("koito.home.example" in sentryPiiSnapshot())
	}

	// endregion
}
