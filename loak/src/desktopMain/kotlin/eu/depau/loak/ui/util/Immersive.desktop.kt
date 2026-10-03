package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
actual fun Immersive() {
	val setFullscreen = LocalWindowChrome.current?.setFullscreen ?: return
	DisposableEffect(setFullscreen) {
		setFullscreen(true)
		onDispose { setFullscreen(false) }
	}
}
