package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Extracts the dominant color from a bitmap, used for cover-art driven theming.
 *
 * Android/iOS back this with kmpalette; web (no wasm variant for kmpalette)
 * provides a no-op state that keeps a neutral default.
 */
interface DominantColorState {
	val color: Color
	suspend fun updateFrom(bitmap: ImageBitmap)
}

@Composable
expect fun rememberDominantColorState(cacheSize: Int): DominantColorState
