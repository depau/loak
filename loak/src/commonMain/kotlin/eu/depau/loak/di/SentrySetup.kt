package eu.depau.loak.di

import dev.zt64.subsonic.api.model.User as SubsonicUser
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryEvent
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User
import com.russhwolf.settings.Settings
import com.russhwolf.settings.get
import org.koin.core.context.GlobalContext

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
	val settings = GlobalContext.getOrNull()?.getOrNull(Settings::class)
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
internal fun String.sanitizeSentryText(): String =
	URL_REGEX.replace(this, "[REDACTED_URL]")
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
