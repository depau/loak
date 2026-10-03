package eu.depau.loak.util

import kotlinx.browser.window

actual val isApplePlatform = window.navigator.platform.let { it.startsWith("Mac") || it.startsWith("i") }
