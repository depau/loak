package eu.depau.loak.di

import dev.zt64.subsonic.api.model.User as SubsonicUser
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryEvent
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User

/**
 * Sentry error reporting bootstrap. Call as early as possible in the native
 * host's startup (Android Application.onCreate, Swift App.init, Desktop main).
 */
private const val SENTRY_DSN =
	"https://37ad07c33d8dc6d5191b355a80f23945@o4512187976384512.ingest.de.sentry.io/4512187981889616"

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
		options.beforeSend = ::sanitizeSentryEvent
		options.beforeBreadcrumb = ::sanitizeBreadcrumb
		// BuildInfo (eu.depau.loak.generated) is an empty class — no version
		// fields to feed release/dist with. Enrich it if you want per-version
		// release groups on Sentry.
		// options.release = BuildInfo.version
		// options.dist = BuildInfo.versionCode
	}
}

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

private fun String.sanitizeSentryText(): String =
	URL_REGEX.replace(this, "[REDACTED_URL]")
		.let { SENSITIVE_PARAM_REGEX.replace(it, "$1[REDACTED]") }

/**
 * Attach the logged-in user to Sentry events. With multiple accounts managed
 * from one app install this makes issues attributable; `sendDefaultPii` is on,
 * so only a username is sent (no PII collection beyond what the user already
 * exposes as their Subsonic login).
 */
fun setSentryUser(user: SubsonicUser?) {
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
