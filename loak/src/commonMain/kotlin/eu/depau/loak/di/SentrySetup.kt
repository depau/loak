package eu.depau.loak.di

import dev.zt64.subsonic.api.model.User as SubsonicUser
import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.protocol.User

/**
 * Sentry error reporting bootstrap. Call as early as possible in the native
 * host's startup (Android Application.onCreate, Swift App.init, Desktop main).
 */
private const val SENTRY_DSN =
	"https://37ad07c33d8dc6d5191b355a80f23945@o4512187976384512.ingest.de.sentry.io/4512187981889616"

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
		// BuildInfo (eu.depau.loak.generated) is an empty class — no version
		// fields to feed release/dist with. Enrich it if you want per-version
		// release groups on Sentry.
		// options.release = BuildInfo.version
		// options.dist = BuildInfo.versionCode
	}
}

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
