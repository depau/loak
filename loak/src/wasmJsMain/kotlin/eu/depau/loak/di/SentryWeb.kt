@file:OptIn(ExperimentalWasmJsInterop::class)

package eu.depau.loak.di

import kotlinx.browser.window
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.w3c.dom.Worker

/*
 * Web crash and hang reporting. The Sentry KMP SDK is a no-op stub on wasmJs, so
 * errors and breadcrumbs go to `@sentry/browser` directly, scrubbed by the same
 * censor as the native platforms.
 *
 * A frozen page can't report itself and the browser SDK has no hang detection,
 * so `hang-watchdog.js` runs in a worker: the page sends it a heartbeat every
 * second plus every breadcrumb, and the worker reports an "App Hanging" event
 * when the heartbeats stop.
 */

private var watchdog: Worker? = null

/**
 * Web counterpart of [initializeSentry]. Call after [initKoin], so the crash
 * reporting toggle is read from the app's own settings.
 */
fun initializeWebSentry() {
	if (!isSentryEnabled()) return
	sentryBrowserInit(webSentryOptions(SENTRY_DSN, WEB_RELEASE, SENTRY_BUILD_COMMIT, ::censorWebText))
	watchdog = Worker("hang-watchdog.js").also {
		startWatchdog(it, SENTRY_DSN, WEB_RELEASE, SENTRY_BUILD_COMMIT)
	}
}

private const val WEB_RELEASE = "web"

internal actual fun captureWebSentryError(throwable: Throwable) {
	if (watchdog == null) return
	val type = throwable::class.qualifiedName ?: throwable::class.simpleName ?: "Throwable"
	sentryBrowserCaptureException(jsError(type, throwable.message ?: "", throwable.stackTraceToString()))
}

internal actual fun addWebSentryBreadcrumb(category: String, message: String?, data: Map<String, Any>) {
	val worker = watchdog ?: return
	val json = JsonObject(data.mapValues { (_, v) ->
		when (v) {
			is Number -> JsonPrimitive(v)
			is Boolean -> JsonPrimitive(v)
			else -> JsonPrimitive(v.toString())
		}
	})
	val crumb = jsBreadcrumb(category.sanitizeSentryText(), message?.sanitizeSentryText(), json.toString())
	sentryBrowserAddBreadcrumb(crumb, appBreadcrumbHint())
	worker.postMessage(crumb)
}

internal actual fun fireBreachAlert(message: String) {
	// deferred: capturing from inside the running beforeSend would re-enter it
	window.setTimeout({
		sentryBrowserCaptureMessage(message, jsTagged(CENSOR_BREACH_TAG))
		null
	}, 0)
}

private fun censorWebText(raw: String): String =
	censorSentryText(raw).also { if (it.breached.isNotEmpty()) reportCensorshipBreach(it.breached) }.text

@JsFun(
	"""(dsn, release, dist, censor) => ({
	dsn, release, dist,
	environment: 'production',
	// No PII (v11's replacement for sendDefaultPii): no IP address or the city
	// derived from it, no cookies, headers or URL query params.
	dataCollection: { userInfo: false, cookies: false, httpHeaders: false, urlQueryParams: false },
	// Only addSentryBreadcrumb's crumbs: the automatic ones record console
	// lines, fetch URLs (server address, signed auth params) and page URLs.
	beforeBreadcrumb: (crumb, hint) => (hint && hint.loak ? crumb : null),
	beforeSend: (event) => {
		delete event.request; // the page URL
		if (typeof event.message === 'string') event.message = censor(event.message);
		for (const e of (event.exception && event.exception.values) || []) {
			if (e.value) e.value = censor(e.value);
		}
		// a self-hosted copy's address shows up in stack frame file names
		return JSON.parse(JSON.stringify(event).split(location.origin).join(''));
	},
})"""
)
private external fun webSentryOptions(
	dsn: String,
	release: String,
	dist: String,
	censor: (String) -> String,
): JsAny

@JsFun(
	"""(worker, dsn, release, dist) => {
	worker.postMessage({ config: { dsn, release, dist, environment: 'production' } });
	setInterval(() => worker.postMessage({ beat: document.visibilityState === 'visible' }), 1000);
}"""
)
private external fun startWatchdog(worker: Worker, dsn: String, release: String, dist: String)

@JsFun("(name, message, stack) => { const e = new Error(message); e.name = name; e.stack = stack; return e; }")
private external fun jsError(name: String, message: String, stack: String): JsAny

@JsFun(
	"""(category, message, data) => ({
	type: 'default', level: 'info', category,
	message: message == null ? undefined : message,
	data: JSON.parse(data),
	timestamp: Date.now() / 1000,
})"""
)
private external fun jsBreadcrumb(category: String, message: String?, data: String): JsAny

@JsFun("(tag) => ({ tags: { [tag]: '1' } })")
private external fun jsTagged(tag: String): JsAny

@JsFun("() => ({ loak: true })")
private external fun appBreadcrumbHint(): JsAny
