package eu.depau.loak.webapp

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import eu.depau.loak.App
import eu.depau.loak.di.initKoin

/**
 * Web (Kotlin/Wasm) entry point. Boots Koin (context-free: no android context)
 * and mounts the shared Compose [App] into the #root viewport (rendered on the
 * HTML canvas managed by [ComposeViewport]).
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
	initKoin()
	ComposeViewport(viewportContainerId = "root") {
		App()
	}
}
