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
	if (isSentryEnabled()) setWebSentryEnabled(true)
}

internal actual fun setWebSentryEnabled(enabled: Boolean) {
	val running = watchdog
	if (enabled && running == null) {
		val env = webSentryEnv()
		sentryBrowserInit(webSentryOptions(SENTRY_DSN, WEB_RELEASE, SENTRY_BUILD_COMMIT, ::censorWebText, env))
		watchdog = Worker("hang-watchdog.js").also {
			startWatchdog(it, SENTRY_DSN, WEB_RELEASE, SENTRY_BUILD_COMMIT, env)
		}
	} else if (!enabled && running != null) {
		sentryBrowserClose()
		stopWatchdog(running)
		watchdog = null
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

/**
 * A function returning what the browser can tell about itself without identifying the user:
 * the user agent (Sentry derives browser, OS and device from it), the screen and the hardware.
 */
@JsFun(
	"""() => {
	// Chromium freezes the OS and browser versions in the user agent string; its
	// client hints carry the real ones, which Sentry reads like the header.
	let hints = {};
	const q = (s) => '"' + s + '"';
	if (navigator.userAgentData) {
		navigator.userAgentData.getHighEntropyValues(['platformVersion', 'fullVersionList', 'model'])
			.then((v) => {
				hints = {
					'Sec-CH-UA': v.fullVersionList.map((b) => q(b.brand) + ';v=' + q(b.version)).join(', '),
					'Sec-CH-UA-Platform': q(v.platform),
					'Sec-CH-UA-Platform-Version': q(v.platformVersion),
					'Sec-CH-UA-Model': q(v.model),
				};
			}, () => {});
	}
	return () => ({
		request: { headers: Object.assign({ 'User-Agent': navigator.userAgent }, hints) },
		contexts: {
			device: {
				screen_width_pixels: Math.round(screen.width * devicePixelRatio),
				screen_height_pixels: Math.round(screen.height * devicePixelRatio),
				screen_density: devicePixelRatio,
				processor_count: navigator.hardwareConcurrency,
				// GiB, coarsened by the browser; Chromium only
				memory_size: navigator.deviceMemory && navigator.deviceMemory * 2 ** 30,
			},
			viewport: { width: innerWidth, height: innerHeight },
		},
	});
}"""
)
private external fun webSentryEnv(): JsAny

@JsFun(
	"""(dsn, release, dist, censor, env) => ({
	dsn, release, dist,
	environment: 'production',
	// No PII (v11's replacement for sendDefaultPii): no IP address, cookies,
	// headers or URL query params.
	dataCollection: { userInfo: false, cookies: false, httpHeaders: false, urlQueryParams: false },
	// The app's own crumbs, and the SDK's request ones with their URL censored down to
	// its target (method, status and sizes stay): an error then shows what the page was
	// talking to, the failed request included. Console and navigation crumbs never pass.
	beforeBreadcrumb: (crumb, hint) => {
		if (hint && hint.loak) return crumb;
		if ((crumb.category !== 'fetch' && crumb.category !== 'xhr') || !crumb.data) return null;
		const d = crumb.data;
		// only these: other keys (http.query…) would carry what the censored URL drops
		const data = { method: d.method, url: censor(String(d.url)), status_code: d.status_code,
			request_body_size: d.request_body_size, response_body_size: d.response_body_size };
		return Object.assign({}, crumb, { data });
	},
	beforeSend: (event) => {
		// the user agent and client hints replace the request, which holds the page URL
		const e = env();
		event.request = e.request;
		event.contexts = Object.assign({}, event.contexts, e.contexts);
		// Sentry geolocates every event from the sender's IP address unless it
		// already has a location: an empty one keeps the city out.
		event.user = Object.assign(event.user || {}, { geo: {} });
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
	env: JsAny,
): JsAny

@JsFun(
	"""(worker, dsn, release, dist, env) => {
	worker.postMessage({ config: { dsn, release, dist, environment: 'production' } });
	worker.beat = setInterval(() => worker.postMessage({ beat: document.visibilityState === 'visible', env: env() }), 1000);
}"""
)
private external fun startWatchdog(worker: Worker, dsn: String, release: String, dist: String, env: JsAny)

@JsFun("(worker) => { clearInterval(worker.beat); worker.terminate(); }")
private external fun stopWatchdog(worker: Worker)

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
