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

fun initializeSentry(release: String? = null) {
	if (!isSentryEnabled()) return
	Sentry.init { options ->
		options.dsn = SENTRY_DSN
		options.environment = "production"
		// Attribute issues to the exact build: release = human version (nightlies
		// already embed `…-nightly.<sha>`; release builds the tag), dist = the
		// short git commit baked in at compile time. Every event, including
		// nightlies and desktop dev builds, is traceable back to a commit.
		options.release = release ?: "unknown"
		options.dist = SENTRY_BUILD_COMMIT
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
	isNoiseEvent(ex?.let { it::class.qualifiedName }, ex?.message)

private fun sanitizeSentryEvent(event: SentryEvent): SentryEvent {
	val breached = mutableListOf<String>()
	fun scrub(s: String?): String? {
		if (s == null) return null
		val c = censorSentryText(s)
		breached += c.breached
		return c.text
	}
	event.message?.let { m ->
		event.message = m.copy(
			message = scrub(m.message),
			params = m.params?.mapTo(mutableListOf()) { scrub(it) ?: it },
			formatted = scrub(m.formatted),
		)
	}
	event.exceptions = event.exceptions.mapNotNull { ex ->
		scrub(ex.value)?.let { ex.copy(value = it) }
	}.toMutableList()
	if (breached.isNotEmpty()) {
		event.setTag(CENSOR_BREACH_TAG, "1")
		reportCensorshipBreach(breached.distinct())
	}
	return event
}

private fun sanitizeBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
	breadcrumb.message = breadcrumb.message?.sanitizeSentryText()
	breadcrumb.category = breadcrumb.category?.sanitizeSentryText()
	return breadcrumb
}

/**
 * Attach the logged-in user to Sentry events. With multiple accounts managed
 * from one app install this makes issues attributable; `sendDefaultPii` is on,
 * so only a username is sent (no PII collection beyond what the user already
 * exposes as their Subsonic login). Also seeds the layer-2 censor with the
 * server this account talks to, so the user's real identifiers are redacted
 * verbatim even if the regexes ever miss a spelling.
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
