package eu.depau.loak.di

import dev.zt64.subsonic.api.model.User as SubsonicUser
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryEvent
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import io.sentry.kotlin.multiplatform.SentryLevel
import eu.depau.loak.ui.screens.artist.viewmodels.ArtistNotFoundException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatformTools
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Sentry error reporting bootstrap. Call as early as possible in the native
 * host's startup (Android Application.onCreate, Swift App.init, Desktop main).
 */
private const val SENTRY_DSN =
	"https://37ad07c33d8dc6d5191b355a80f23945@o4512187976384512.ingest.de.sentry.io/4512187981889616"

/**
 * Settings key backing the "crash reporting" toggle. Read with the same
 * [com.russhwolf.settings.Settings] store [eu.depau.loak.domain.manager.PreferenceManager]
 * uses (the Koin one, when it exists), so the in-app switch and the boot-time
 * gate always agree. Defaults to true (opt-out).
 */
const val SENTRY_ENABLED_KEY = "crashReportingEnabled"

/**
 * Whether crash reporting to Sentry is active. Consults the Koin-registered
 * [Settings] singleton when Koin is up; before that (Sentry boots before Koin
 * on Android) it falls back to the platform's no-arg [Settings] store, which
 * is the exact store [eu.depau.loak.domain.manager.PreferenceManager] reads on
 * Android/iOS — so the toggle is honored at startup everywhere except desktop,
 * where [initializeSentry] runs after [initKoin] anyway.
 */
fun isSentryEnabled(): Boolean {
	val settings = KoinPlatformTools.defaultContext().getOrNull()?.getOrNull(Settings::class)
		?: Settings()
	return settings.get(SENTRY_ENABLED_KEY, true)
}

/**
 * Matches any URL (http/https) up to whitespace or a closing bracket/quote.
 * Ktor error messages embed the full request URL here, and the Subsonic API's
 * signed query params (`username`, `token`, `salt`) ride along in it.
 */
private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

/**
 * Matches bare hostnames, host:port and InetSocketAddress-style `host/ip:port`
 * chains, plus bare IPv4/IPv6, as Java/Ktor print them in connect errors:
 * `failed to connect to starrs.depau.eu/192.168.3.204 (port 443)` or
 * `Failed to connect to navikek.puntokek.com/172.67.183.168:443`. URLs are
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
		// hostname followed by " (port N)" — Java's ConnectException toString
		"""|(?<![A-Za-z0-9])(?:[A-Za-z0-9-]+\.)+[A-Za-z]{2,}(?=\s*\(\s*port\b)""",
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

fun initializeSentry() {
	if (!isSentryEnabled()) return
	Sentry.init { options ->
		options.dsn = SENTRY_DSN
		options.environment = "production"
		options.attachStackTrace = true
		options.attachThreads = true
		// Compose Multiplatform on Apple targets: unhandled Kotlin exceptions can
		// otherwise surface as generic C++ crashes instead of useful stack traces.
		options.enableUnhandledCppExceptionMonitoring = false
		options.sendDefaultPii = true
		// Performance tracing for the few spans we create (see [traced]). Span names and data
		// are fixed strings/ids/counts we set ourselves: never URLs, usernames or servers.
		// ANR (Android) and app-hang (iOS) detection stay at their defaults (on).
		options.tracesSampleRate = 0.2
		// Never let user data ride out on error reports: Ktor timeout/IO errors
		// embed the full request URL (with the signed auth query params and the
		// user's server address) in their message. Scrub URLs and credential
		// parameters from every event and breadcrumb at the send boundary.
		// KMP beforeSend: on JVM targets (Android/desktop) the SDK's applyKmpEvent
		// never writes exceptions back to the JVM event, so scrubbing there would
		// be dropped; register a native sanitizer instead (see the actuals of
		// registerJvmSentrySanitizer). This one still covers the Apple message path.
		options.beforeSend = ::sanitizeSentryEvent
		options.beforeBreadcrumb = ::sanitizeBreadcrumb
	}
	// JVM-targeted platforms let the raw io.sentry Java/Kotlin event reach the
	// send boundary, so redact there too (covers uncaught-exception and other
	// native-captured events that bypass the KMP wrapper).
	registerJvmSentrySanitizer()
}

/**
 * Scrub URLs and credential parameters from raw JVM `io.sentry` events at the
 * send boundary. No-op expect; Android and desktop JVM register a real
 * `SentryOptions.beforeSend`/`beforeBreadcrumb` in their actuals.
 */
internal expect fun registerJvmSentrySanitizer()

/**
 * True for exceptions that are expected at runtime and pollute Sentry: offline /
 * transient network failures, cancelled coroutines, unsupported media the player
 * already handles, the desktop hot-reload stale-continuation NoClassDefFoundError,
 * and the artist-missing-in-DB sentinel. Each one is already handled or logged
 * locally; there is nothing actionable in reporting it.
 *
 * [type] is the exception class name as Sentry records it (fully-qualified for
 * JVM, e.g. `java.net.ConnectException` / `kotlinx.coroutines.JobCancellationException`);
 * [value] is its message, when the exception type alone isn't specific enough
 * (a generic IOException / NoClassDefFoundError is only noise for one message).
 *
 * The JVM actuals drop events whose exceptions all match this; the KMP Apple
 * beforeSend can't drop (returns a non-null event), but the connectivity text is
 * still scrubbed by [sanitizeSentryText] there.
 */
internal fun isNoiseEvent(type: String?, value: String?): Boolean {
	if (type == null) return false
	when (type.substringAfterLast('.')) {
		"JobCancellationException",
		"CancellationException",
		"TimeoutCancellationException",
		"ConnectException",
		"SocketTimeoutException",
		"UnknownHostException",
		"SocketException",
		"UnsupportedAudioFileException",
		"ArtistNotFoundException" -> return true
	}
	val v = value ?: return false
	return (type == "java.io.IOException" && v.contains("SETTINGS preface")) ||
		// desktop dev hot-reload only: a stale $...$N continuation class, never a real one
		(type == "java.lang.NoClassDefFoundError" && v.contains("\$"))
}

/** For raw [Throwable] sources: adapt the class name into [isNoiseEvent]. */
internal fun isNoiseException(ex: Throwable?): Boolean =
	isNoiseEvent(ex?.javaClass?.name, ex?.message)

private fun sanitizeSentryEvent(event: SentryEvent): SentryEvent {
	event.message?.let { m ->
		event.message = m.copy(
			message = m.message?.sanitizeSentryText(),
			params = m.params?.mapTo(mutableListOf()) { it.sanitizeSentryText() },
			formatted = m.formatted?.sanitizeSentryText(),
		)
	}
	event.exceptions = event.exceptions.mapNotNull { ex ->
		ex.value?.sanitizeSentryText()?.let { ex.copy(value = it) }
	}.toMutableList()
	return event
}

private fun sanitizeBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
	breadcrumb.message = breadcrumb.message?.sanitizeSentryText()
	breadcrumb.category = breadcrumb.category?.sanitizeSentryText()
	return breadcrumb
}

/**
 * Redact URLs and credential assignments from a string. Shared by the KMP
 * beforeSend and the JVM-native sentry sanitizers (Android and desktop).
 */
private val UNKNOWN_HOST_REGEX =
	Regex("""UnknownHostException[: ]{2}[A-Za-z0-9.\-]+""", RegexOption.IGNORE_CASE)

internal fun String.sanitizeSentryText(): String =
	URL_REGEX.replace(this, "[REDACTED_URL]")
		.let { HOST_PORT_REGEX.replace(it, "[REDACTED_HOST]") }
		// `UnknownHostException: navikek.puntokek.com` — the bare host is the whole message
		.let { UNKNOWN_HOST_REGEX.replace(it, "UnknownHostException: [REDACTED_HOST]") }
		.let { SENSITIVE_PARAM_REGEX.replace(it, "$1[REDACTED]") }

/**
 * Attach the logged-in user to Sentry events. With multiple accounts managed
 * from one app install this makes issues attributable; `sendDefaultPii` is on,
 * so only a username is sent (no PII collection beyond what the user already
 * exposes as their Subsonic login).
 */
fun setSentryUser(user: SubsonicUser?) {
	if (!isSentryEnabled()) return
	if (!Sentry.isEnabled()) return
	if (user == null) {
		Sentry.setUser(null)
		return
	}
	Sentry.setUser(
		io.sentry.kotlin.multiplatform.protocol.User(
			id = user.name,
			username = user.name,
		)
	)
}

/**
 * Starts a Sentry span: a child of [parent] when given, else a new transaction.
 * Returns null where tracing isn't wired up (iOS, web).
 */
internal expect fun startSentrySpan(parent: Any?, op: String, name: String): Any?

internal expect fun finishSentrySpan(span: Any, ok: Boolean, data: Map<String, Any>)

private class CurrentSpan(val span: Any) : AbstractCoroutineContextElement(Key) {
	companion object Key : CoroutineContext.Key<CurrentSpan>
}

/**
 * Runs [block] inside a Sentry span, nested under the caller's [traced] span if any.
 * [block] may put counts/ids into the span data map (never URLs or user data).
 * A failed [Result] or an exception marks the span as failed.
 */
suspend fun <T> traced(
	op: String,
	name: String,
	block: suspend (data: MutableMap<String, Any>) -> T
): T {
	val data = mutableMapOf<String, Any>()
	val span = if (Sentry.isEnabled()) {
		startSentrySpan(currentCoroutineContext()[CurrentSpan]?.span, op, name)
	} else null
	if (span == null) return block(data)
	var ok = false
	try {
		return withContext(CurrentSpan(span)) { block(data) }
			.also { ok = (it as? Result<*>)?.isSuccess ?: true }
	} finally {
		finishSentrySpan(span, ok, data)
	}
}

/** Breadcrumb for an audio transfer; [data] must hold no URLs or user data. */
fun addTransferBreadcrumb(data: Map<String, Any>) {
	if (!Sentry.isEnabled()) return
	Sentry.addBreadcrumb(
		Breadcrumb(level = SentryLevel.INFO, category = "transfer", data = data.toMutableMap())
	)
}
