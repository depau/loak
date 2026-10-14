@file:JsModule("@sentry/browser")
@file:OptIn(ExperimentalWasmJsInterop::class)

package eu.depau.loak.di

// The few `@sentry/browser` exports the web reporter uses (see SentryWeb.kt).

@JsName("init")
internal external fun sentryBrowserInit(options: JsAny)

@JsName("captureException")
internal external fun sentryBrowserCaptureException(exception: JsAny): String

@JsName("captureMessage")
internal external fun sentryBrowserCaptureMessage(message: String, context: JsAny): String

@JsName("addBreadcrumb")
internal external fun sentryBrowserAddBreadcrumb(breadcrumb: JsAny, hint: JsAny)
