package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Desktop: kmpalette runs on JVM, so real dominant-colour extraction is
 * possible — but the desktop build does not yet wire the network extension.
 * Neutral default; cover-art theming matches web for now.
 */
@Composable
actual fun rememberDominantColorState(cacheSize: Int): DominantColorState =
	object : DominantColorState {
		override val color: Color = Color(0xFF546E7A)
		override suspend fun updateFrom(bitmap: androidx.compose.ui.graphics.ImageBitmap) {}
	}
