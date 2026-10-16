package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
actual fun Immersive(onFullscreenExit: () -> Unit) {
	val chrome = LocalWindowChrome.current ?: return
	val setFullscreen = chrome.setFullscreen ?: return
	var wasFullscreen by remember { mutableStateOf(chrome.fullscreen) }

	DisposableEffect(chrome.setImmersive) {
		chrome.setImmersive?.invoke(true)
		onDispose { chrome.setImmersive?.invoke(false) }
	}
	DisposableEffect(setFullscreen) {
		val restoreWindowed = !chrome.fullscreen
		if (restoreWindowed) setFullscreen(true)
		onDispose {
			if (restoreWindowed) setFullscreen(false)
		}
	}
	LaunchedEffect(chrome.fullscreen) {
		if (chrome.fullscreen) {
			wasFullscreen = true
		} else if (wasFullscreen) {
			onFullscreenExit()
		}
	}
}
