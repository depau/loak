@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package eu.depau.loak.di

import kotlin.concurrent.atomics.AtomicReference
import io.ktor.http.DEFAULT_PORT
import io.ktor.http.decodeURLPart
import io.ktor.http.parseUrl

/**
 * Sentry censorship: two layers.
 *
 * Layer 1 — regex redaction of anything that *looks* like an identifier: URLs,
 * bare IPv4/IPv6, host/ip:port chains, quoted hostnames in Java's
 * `Unable to resolve host "…"` message, and credential-style key=value pairs.
 * It runs on every string that leaves the app at the send boundary.
 *
 * Layer 2 — exact-match safety net on the user's *actual* identifiers. Every
 * server address, account, secret or header the user gave the app ([recordPiiServer],
 * [recordPiiIdentifier], seeded by the settings holding them — `piiPreference` —
 * and by the code using one before it's saved) that survives layer 1 is
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
 * terminator (`,`, `)`, `"`, `'`) so the punctuation Ktor prints around
 * the URL stays intact: `[url=<URL>, connect_timeout=…]`. [censorUrl] keeps
 * what the request was for and drops whose it was.
 */
private val URL_REGEX = Regex("""https?://[^\s,"'()]+""", RegexOption.IGNORE_CASE)

/**
 * Public services the app calls itself: their hosts carry no user data, so they stay readable.
 * A host here with a number keeps only that many leading path segments (Last.fm's `/music/…`
 * pages are named after artists and albums). Any other host the user didn't configure (a
 * radio stream, an image CDN) shows as `redacted`.
 */
private val PUBLIC_HOSTS: Map<String, Int?> = mapOf(
	"api.deezer.com" to null, "www.deezer.com" to null, "deezer.com" to null,
	"musicbrainz.org" to null, "coverartarchive.org" to null,
	"api.listenbrainz.org" to null, "labs.api.listenbrainz.org" to null, "listenbrainz.org" to null,
	"ws.audioscrobbler.com" to null, "www.last.fm" to 1, "last.fm" to 1,
	"lrclib.net" to null, "apic.musixmatch.com" to null,
	"lyricsplus.atomix.one" to null, "lyricsplus.binimum.org" to null,
	"lyricsplus.prjktla.my.id" to null, "lyrics-plus-backend.vercel.app" to null,
	"lyricsplus-seven.vercel.app" to null, "lyricsplus.prjktla.workers.dev" to null,
	"api.github.com" to null, "github.com" to null,
)

/** A server the user configured: [service] names it, [basePath] is its address's own path. */
private class PiiServer(val service: String, val host: String, val basePath: String)

private val piiServers = AtomicReference<List<PiiServer>>(emptyList())

/**
 * The host as reports may show it: `<service>.redacted` for the user's own servers, public
 * services as they are, `redacted` for anything else (other hosts, IP addresses). Idempotent:
 * censored text can pass through again unchanged.
 */
private fun shownHost(rawHost: String): String {
	val host = rawHost.lowercase().removeSurrounding("[", "]")
	if (host == "redacted" || host.endsWith(".redacted")) return host
	piiServers.load().firstOrNull { it.host == host }?.let { return "${it.service}.redacted" }
	return if (host in PUBLIC_HOSTS) host else "redacted"
}

/**
 * What a request went to, without whose it was: scheme, host as [shownHost] shows it, port,
 * and the API path the app built. The user's own path prefix (a server behind `/navidrome`),
 * path segments the user typed (a ListenBrainz username), the query (Subsonic's signed
 * credentials, search terms) and the fragment are dropped.
 * `https://music.example.org:4533/navidrome/rest/getPlaylist.view?u=…&t=…` becomes
 * `https://subsonic.redacted:4533/rest/getPlaylist.view`.
 */
private fun censorUrl(raw: String): String {
	val url = parseUrl(raw) ?: return "[REDACTED_URL]"
	val host = url.host.lowercase()
	val server = piiServers.load()
		.filter { it.host == host && (url.encodedPath == it.basePath || url.encodedPath.startsWith(it.basePath + "/")) }
		.maxByOrNull { it.basePath.length }
	val segments = (server?.let { url.encodedPath.removePrefix(it.basePath) } ?: url.encodedPath)
		.split('/').filter { it.isNotEmpty() }
		.let { all -> PUBLIC_HOSTS[host]?.let { all.take(it) } ?: all }
	val known = piiSnapshot.load()
	val path = segments.joinToString("") { segment ->
		val decoded = runCatching { segment.decodeURLPart() }.getOrDefault(segment).lowercase()
		"/" + if (decoded in known) "[REDACTED]" else segment
	}
	val port = if (url.specifiedPort == DEFAULT_PORT) "" else ":${url.specifiedPort}"
	val shown = server?.let { "${it.service}.redacted" } ?: shownHost(host)
	return "${url.protocol.name}://$shown$port$path"
}

/** A host:port's trailing port, as `:443` or Java's ` (port 443)`. */
private val TRAILING_PORT = Regex("""(?::|\s*\(\s*port\s+)(\d{1,5})\s*\)?$""", RegexOption.IGNORE_CASE)

/** A [HOST_PORT_REGEX] match as `<shown host>:<port>`. */
private fun censorHostPort(text: String): String {
	// compressed IPv6 has no port, its last group would read as one
	val port = if (!text.startsWith("[") && text.count { it == ':' } > 1) null else TRAILING_PORT.find(text)
	val host = (port?.let { text.substring(0, it.range.first) } ?: text).substringBefore('/')
	return shownHost(host) + (port?.let { ":" + it.groupValues[1] } ?: "")
}

/**
 * Matches bare hostnames, host:port and InetSocketAddress-style `host/ip:port`
 * chains, plus bare IPv4/IPv6 and `host.tld:port`, as Java/Ktor print them in
 * connect errors: `failed to connect to starrs.depau.eu/192.168.3.204 (port 443)`
 * or `Failed to connect to navikek.puntokek.com/172.67.183.168:443`. URLs are
 * handled first by [URL_REGEX]; this catches the host/IP when it leaks without
 * an `https?://` prefix. [censorHostPort] keeps the port.
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
 * Matches `file:` URIs — the local filesystem path of a downloaded/streamed
 * file, which would leak the user's home dir and username (LOAK-10/Z shipped
 * `file:/Users/<user>/Library/…`) and, for a song, what they listen to.
 * `file:` alone is dropped as a bare scheme; require the scheme plus a path.
 * The path runs to the end of the line: names have spaces.
 */
private val FILE_PATH_REGEX = Regex(
	"""file:(?:/{1,3})?(?:[A-Za-z]:)?[/\\][^\n"'<>]{0,512}\S""",
	RegexOption.IGNORE_CASE
)

/** Audio and media file extensions: a song's format may leave, its name may not. */
private const val AUDIO_EXTS =
	"mp3|flac|ogg|oga|opus|m4a|m4b|mp4|aac|alac|wav|wave|aif|aiff|wma|ape|wv|dsf|dff|mka|mpc|webm"

private val AUDIO_EXT_REGEX = Regex("""\.($AUDIO_EXTS)(?!\w)""", RegexOption.IGNORE_CASE)

/**
 * Bare absolute paths under the roots that hold user data (home dirs, Android storage, mounts)
 * and Windows drive paths. One that reaches an audio file runs to its extension, spaces
 * included; any other stops at the first space.
 */
private val ROOTED_PATH_REGEX = Regex(
	"""(?<![^\s"'(\[=,])(?:/(?:home|Users|storage|sdcard|data|mnt|media|tmp|var|private|Volumes|run|root|opt|srv)/|[A-Za-z]:\\)""" +
		"""(?:[^\n"'<>]*?\.($AUDIO_EXTS)(?!\w)|[^\s"'<>]*)""",
	RegexOption.IGNORE_CASE
)

/** A quoted name of an audio file, whose spaces would otherwise cut the match short. */
private val QUOTED_AUDIO_FILE_REGEX = Regex("""(["'])[^"'\n]*?\.($AUDIO_EXTS)\1""", RegexOption.IGNORE_CASE)

/**
 * A bare audio file name (`03-Some_Song.flac`), on its own or ending a relative path or a URL.
 * ponytail: an unquoted, relative name with spaces only loses its last word; such names reach
 * events through absolute paths (above) in every case seen so far.
 */
private val AUDIO_FILE_REGEX =
	Regex("""(?<![^\s"'(\[=,/\\:])[^\s/\\"'<>()\[\]:]+\.($AUDIO_EXTS)(?!\w)""", RegexOption.IGNORE_CASE)

/** [token], plus the audio format [path] ends in, if any: `[REDACTED_PATH].flac`. */
private fun keepingFormat(token: String, path: String) =
	token + AUDIO_EXT_REGEX.findAll(path).lastOrNull()?.let { "." + it.groupValues[1].lowercase() }.orEmpty()

/**
 * Thread names carry the target as a bare label: OkHttp names its worker
 * threads after the connection (`OkHttp starrs.depau.eu`, `OkHttp dn7.ca.…`,
 * sometimes with a URL). Layer-1 host regexes require a port/IP, so a bare
 * hostname in a thread name would slip through; [sanitizeThreadName] shows the
 * whole `label` tail as its [shownHost].
 */
private val THREAD_HOST_REGEX =
	Regex("""(?:^|[\s])((?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,})(?:[:/][^\s]*)?(?=\s*$)""")

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
	Regex("""UnknownHostException[: ]{2}((?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,})""", RegexOption.IGNORE_CASE)

/**
 * Android prints DNS failures as `Unable to resolve host "starrs.depau.eu": No
 * address associated with hostname`. Layer 1 must catch the quoted name here;
 * it is the exact spelling LOAK-8 shipped unredacted. Group 1 is the quote char
 * (or none), group 2 the host; the replacement re-emits the caught `resolve
 * host` prefix and the quote so the message stays readable.
 */
private val QUOTED_HOST_REGEX =
	Regex("""\bresolve(?:d)?\s+host\s+(["'`]?)([^\s"'`()]+)\1""", RegexOption.IGNORE_CASE)

// The PII registry is written once per login (single writer), read on every
// send (concurrent readers on Sentry's threads). An atomic reference to an
// immutable snapshot keeps concurrent reads safe on every KMP target without
// platform APIs.
private val piiSnapshot = AtomicReference<Set<String>>(emptySet())

private val reportedSnapshot = AtomicReference<Set<String>>(emptySet())

/** Adds [values] to the PII snapshot; a no-op (no copy) when it already holds them all. */
private fun addPii(values: Collection<String>) {
	while (true) {
		val current = piiSnapshot.load()
		if (current.containsAll(values)) return
		if (piiSnapshot.compareAndSet(current, current + values)) return
	}
}

/** Registers [server] for [censorUrl]; a no-op when already known. */
private fun addServer(server: PiiServer) {
	while (true) {
		val current = piiServers.load()
		if (current.any { it.service == server.service && it.host == server.host && it.basePath == server.basePath }) return
		if (piiServers.compareAndSet(current, current + server)) return
	}
}

/**
 * Teaches the censor a server address the user configured, [service] naming what it is
 * ("subsonic", "audiomuse", "lidarr", "koito"): its URLs leave as
 * `<scheme>://<service>.redacted[:port]/<API path>`, without the address's own path, and layer 2
 * exact-matches the address. Blank or unparsable values are ignored. Cheap when already known:
 * settings call it on every value they hold (see `piiPreference`).
 */
internal fun recordPiiServer(service: String, value: String) {
	val url = value.trim().trimEnd('/')
	if (url.isEmpty() || url.any { it.isWhitespace() }) return
	val parsed = parseUrl(if ("://" in url) url else "https://$url")?.takeIf { it.host.isNotEmpty() } ?: return
	val host = parsed.host.lowercase()
	addServer(PiiServer(service, host, parsed.encodedPath.trimEnd('/')))
	addPii(listOfNotNull(url.lowercase(), host).filter { it.length >= 4 && it.contains(".") })
}

/**
 * Seeds layer 2 with an arbitrary user identifier (username, account name, password, token…)
 * that must never leave in an event. Unlike [recordPiiServer] (server addresses)
 * identifiers don't have to look like hosts, so the dot check is skipped —
 * a short, dot-less username shouldn't be sendable just because it has no
 * dot. The `>= 4` floor matches layer 2's own length gate below, which exists
 * to stop a short substring from mangling unrelated words ("rob" vs "Robert").
 */
internal fun recordPiiIdentifier(value: String) {
	val trimmed = value.trim().lowercase()
	if (trimmed.length >= 4) addPii(listOf(trimmed))
}

/** Seeds layer 2 with the values of custom HTTP headers, one `Key:Value` per line (auth tokens…). */
internal fun recordPiiHeaders(lines: String) {
	lines.lineSequence().forEach { recordPiiIdentifier(it.substringAfter(':', "")) }
}

internal fun clearPiiForTests() {
	piiSnapshot.store(emptySet())
	piiServers.store(emptyList())
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
	val l1 = URL_REGEX.replace(raw) { censorUrl(it.value) }
		.let { HOST_PORT_REGEX.replace(it) { m -> censorHostPort(m.value) } }
		.let { FILE_PATH_REGEX.replace(it) { m -> keepingFormat("[REDACTED_PATH]", m.value) } }
		.let { ROOTED_PATH_REGEX.replace(it) { m -> keepingFormat("[REDACTED_PATH]", m.value) } }
		.let { QUOTED_AUDIO_FILE_REGEX.replace(it) { m ->
			val quote = m.groupValues[1]
			quote + keepingFormat("[REDACTED_FILE]", m.value) + quote
		} }
		.let { AUDIO_FILE_REGEX.replace(it) { m -> keepingFormat("[REDACTED_FILE]", m.value) } }
		.let { UNKNOWN_HOST_REGEX.replace(it) { m -> "UnknownHostException: " + shownHost(m.groupValues[1]) } }
		.let { QUOTED_HOST_REGEX.replace(it) { m ->
			val quote = m.groupValues[1]
			"resolve host $quote${shownHost(m.groupValues[2])}$quote"
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

/**
 * Redact the connection target from a thread name: the bare-hostname tail OkHttp
 * prints (`OkHttp starrs.depau.eu`, no port/IP to trigger [HOST_PORT_REGEX])
 * becomes its [shownHost], then the usual censor pass.
 */
internal fun String.sanitizeThreadName(): String =
	censorSentryText(THREAD_HOST_REGEX.replace(this) { " " + shownHost(it.groupValues[1]) }).text

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
