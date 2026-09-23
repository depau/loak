package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Web: kmpalette has no wasm variant, so dominant-color extraction is a no-op
 * that keeps a neutral default. Cover-art theming degrades gracefully.
 */
@Composable
actual fun rememberDominantColorState(cacheSize: Int): DominantColorState =
	object : DominantColorState {
		override val color: Color = Color(0xFF546E7A)
		override suspend fun updateFrom(bitmap: ImageBitmap) {}
	}
