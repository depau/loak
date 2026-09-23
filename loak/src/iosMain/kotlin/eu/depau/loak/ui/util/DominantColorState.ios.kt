package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.kmpalette.rememberDominantColorState

@Composable
actual fun rememberDominantColorState(cacheSize: Int): DominantColorState {
	val state = com.kmpalette.rememberDominantColorState(cacheSize = cacheSize)
	return object : DominantColorState {
		override val color: Color
			get() = state.color
		override suspend fun updateFrom(bitmap: ImageBitmap) {
			state.updateFrom(bitmap)
		}
	}
}
