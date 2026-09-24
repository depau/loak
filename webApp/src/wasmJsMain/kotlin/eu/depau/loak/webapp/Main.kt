@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package eu.depau.loak.webapp

import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.useSnapshotCache
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
	// ponytail: Disable RenderNode snapshot cache to work around SKIKO-1183 / CMP-10751
	// (RuntimeError: table index is out of bounds at RenderNode.drawInto on Wasm).
	ComposeUiFlags.useSnapshotCache = false

	initKoin()
	ComposeViewport(viewportContainerId = "root") {
		App()
	}
}
