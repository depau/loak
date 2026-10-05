@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package eu.depau.loak.di

import kotlin.concurrent.atomics.AtomicReference

/**
 * Sentry censorship: two layers.
 *
 * Layer 1 — regex redaction of anything that *looks* like an identifier: URLs,
 * bare IPv4/IPv6, host/ip:port chains, quoted hostnames in Java's
 * `Unable to resolve host "…"` message, and credential-style key=value pairs.
 * It runs on every string that leaves the app at the send boundary.
 *
 * Layer 2 — exact-match safety net on the user's *actual* identifiers. Any
 * server URL/hostname the app talks to this session ([recordPii], seeded from
 * [eu.depau.loak.domain.manager.SessionManager]) that survives layer 1 is
 * redacted verbatim, the shipping event is tagged `sentry.censor-breach=1`,
 * and a one-flight alert is fired so a censor bug can't leak silently again.
 * Layer 1's bare-IP regex already scrubs resolved addresses in every known
 * printer format, so only hostnames are inventory-matched here.
 *
 * Shared by the KMP beforeSend and the native JVM sanitizers (Android +
 * desktop); iOS ships through the KMP path.
 */

/**
 * Matches any URL (http/https). Stops at whitespace or an error-string
 * terminator (`,`, `)`, `"`, `'`, `]`) so the punctuation Ktor prints around
 * the URL stays intact: `[url=<URL>, connect_timeout=…]` becomes
 * `[url=[REDACTED_URL], …]`. The Subsonic API's signed query params (`u=`,
 * `t=`, `s=`) ride along inside the match and are gone with it.
 */
private val URL_REGEX = Regex("""https?://[^\s,"'()]+""", RegexOption.IGNORE_CASE)

/**
 * Matches bare hostnames, host:port and InetSocketAddress-style `host/ip:port`
 * chains, plus bare IPv4/IPv6 and `host.tld:port`, as Java/Ktor print them in
 * connect errors: `failed to connect to starrs.depau.eu/192.168.3.204 (port 443)`
 * or `Failed to connect to navikek.puntokek.com/172.67.183.168:443`. URLs are
 * handled first by [URL_REGEX]; this catches the host/IP when it leaks without
 * an `https?://` prefix.
 */
private val HOST_PORT_REGEX = Regex(
	"""[^\s(),]+/\d{1,3}(?:\.\d{1,3}){3}(?::\d{1,5})?""" +
		// bare IPv4[:port]
		"""|(?<!\d)(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(?:\.(?:25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}(?!\d)(?::\d{1,5})?""" +
		// bracketed IPv6 (InetSocketAddress form) with optional :port
		"""|\[[0-9a-fA-F:.%]+\](?::\d{1,5})?""" +
		// compressed IPv6 (contains ::)
		"""|[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{0,4}){0,3}::[0-9a-fA-F]{0,4}""" +
		// hostname followed by " (port N)" — Java's ConnectException toString;
		// consume the port too so it doesn't linger as a bare number
		"""|(?<![A-Za-z0-9])(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}\s*\(\s*port\s+\d{1,5}\s*\)""" +
		// bare host.tld:port without an IP or parenthesis (Ktor prints these)
		"""|(?<![A-Za-z0-9])(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}\s*:\s*\d{1,5}(?!\d)""",
	RegexOption.IGNORE_CASE
)

/**
 * Matches credential-style `key=value` / `key: value` assignments anywhere in
 * error text, in case a token or username surfaces without an enclosing URL.
 */
private const val SENSITIVE_PARAM_KEYS =
	"(?:u|user|username|t|token|s|salt|passw(?:or)?d|pw|api[_-]?key|auth(?:orization)?|signature|secret)"
private val SENSITIVE_PARAM_REGEX =
	Regex("""\b($SENSITIVE_PARAM_KEYS\s*[=:]\s*)[^&\s,"']+""", RegexOption.IGNORE_CASE)

/**
 * `UnknownHostException: navikek.puntokek.com` — the bare host is the whole
 * message. Require a full dotted hostname so `Unable` (no dot) can't match.
 */
private val UNKNOWN_HOST_REGEX =
	Regex("""UnknownHostException[: ]{2}(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}""", RegexOption.IGNORE_CASE)

/**
 * Android prints DNS failures as `Unable to resolve host "starrs.depau.eu": No
 * address associated with hostname`. Layer 1 must catch the quoted name here;
 * it is the exact spelling LOAK-8 shipped unredacted. Group 1 is the quote char
 * (or none); the replacement re-emits the caught `resolve host` prefix and the
 * quote so the message stays readable.
 */
private val QUOTED_HOST_REGEX =
	Regex("""\bresolve(?:d)?\s+host\s+(["'`]?)[^\s"'`()]+\1""", RegexOption.IGNORE_CASE)

// The PII registry is written once per login (single writer), read on every
// send (concurrent readers on Sentry's threads). An atomic reference to an
// immutable snapshot keeps concurrent reads safe on every KMP target without
// platform APIs.
private val piiSnapshot = AtomicReference<Set<String>>(emptySet())

private val reportedSnapshot = AtomicReference<Set<String>>(emptySet())

/**
 * Seeds layer 2 with the server addresses the app talks to. Call once per
 * server/URL the app may print in an error (Subsonic instance, AudioMuse…).
 * Parses the host out of URLs; blank or dot-less values are ignored.
 */
internal fun recordPii(vararg values: String) {
	val next = piiSnapshot.load().toMutableSet()
	for (value in values) {
		val url = value.trim()
		if (url.isBlank() || url.length < 4 || !url.contains(".")) continue
		next += url.lowercase()
		val host = url.substringAfter("://", url).substringBefore('/').substringBefore(':').lowercase()
		if (host.length >= 4 && host.contains(".")) next += host
	}
	piiSnapshot.store(next)
}

internal fun clearPiiForTests() {
	piiSnapshot.store(emptySet())
	reportedSnapshot.store(emptySet())
}

/** The known server identifiers, for inspecting PII state in tests. */
internal fun sentryPiiSnapshot(): Set<String> = piiSnapshot.load()

/** Censored text plus which known PII values layer 2 had to scrub (empty = clean). */
internal data class Censored(val text: String, val breached: List<String>)

/**
 * Full censorship pass: layer 1 regex redaction, then layer 2 exact-match on
 * the known server identifiers. Use this when a breach flag matters (event
 * sanitizers); the plain [sanitizeSentryText] wrapper keeps callers that only
 * need a censored string short.
 */
internal fun censorSentryText(raw: String): Censored {
	val l1 = URL_REGEX.replace(raw, "[REDACTED_URL]")
		.let { HOST_PORT_REGEX.replace(it, "[REDACTED_HOST]") }
		.let { UNKNOWN_HOST_REGEX.replace(it, "UnknownHostException: [REDACTED_HOST]") }
		.let { QUOTED_HOST_REGEX.replace(it) { m ->
			"resolve host " + m.groupValues[1] + "[REDACTED_HOST]" + m.groupValues[1]
		} }
		.let { SENSITIVE_PARAM_REGEX.replace(it, "$1[REDACTED]") }
	val breached = piiSnapshot.load().filter { p -> p.length >= 4 && l1.contains(p, ignoreCase = true) }
	if (breached.isEmpty()) return Censored(l1, emptyList())
	var censored = l1
	for (p in breached) censored = censored.replace(p, "[REDACTED_HOST]", ignoreCase = true)
	return Censored(censored, breached)
}

/** Redact URLs, hostnames, IPs and credential assignments from a string. */
internal fun String.sanitizeSentryText(): String = censorSentryText(this).text

/** Sentinel tag on any shipping event that layer 2 had to scrub. Alerts can key on it. */
internal const val CENSOR_BREACH_TAG = "sentry.censor-breach"

/**
 * Layer-2 fired only when layer 1 missed one of the user's real identifiers.
 * One alert per distinct value per session; the alert body is fixed and PII-free
 * so it can never re-trigger through its own beforeSend.
 */
internal fun reportCensorshipBreach(breached: List<String>) {
	val reported = reportedSnapshot.load()
	val fresh = breached.filter { it !in reported }
	if (fresh.isEmpty()) return
	reportedSnapshot.store(reported + fresh)
	fireBreachAlert(
		"Censorship breach: known server identifiers bypassed the sanitizer (${fresh.size} value(s)). " +
			"File an issue with this event; it must not happen."
	)
}

/**
 * Sends the layer-2 breach alert. Actuals: JVM spawns a throwaway thread (never
 * capture from inside the running beforeSend); Apple/web have no capture path
 * wired, so they no-op.
 */
internal expect fun fireBreachAlert(message: String)
