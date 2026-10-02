package eu.depau.loak.util

/*
 * Web (Kotlin/Wasm) logger: mirrors android.util.Log onto the browser console.
 */
@JsFun("(s) => console.log(s)")
private external fun jsConsoleLog(s: String): Unit

@JsFun("(s) => console.warn(s)")
private external fun jsConsoleWarn(s: String): Unit

@JsFun("(s) => console.error(s)")
private external fun jsConsoleError(s: String): Unit

actual object Logger {
	actual fun e(tag: String, msg: String, tr: Throwable?) {
		jsConsoleError("[$tag] $msg${tr?.let { "\n${it.stackTraceToString()}" } ?: ""}")
		captureSentryError(tr)
	}

	actual fun i(tag: String, msg: String, tr: Throwable?) {
		jsConsoleLog("[$tag] $msg${tr?.let { "\n${it.stackTraceToString()}" } ?: ""}")
	}

	actual fun w(tag: String, msg: String, tr: Throwable?) {
		jsConsoleWarn("[$tag] $msg${tr?.let { "\n${it.stackTraceToString()}" } ?: ""}")
	}
}
