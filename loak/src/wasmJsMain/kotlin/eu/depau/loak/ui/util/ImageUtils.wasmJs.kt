package eu.depau.loak.ui.util

import androidx.compose.ui.graphics.ImageBitmap
import coil3.Image

/**
 * Web: converting a Coil [Image] to a shared [ImageBitmap] has no stable wasm
 * API yet, so this returns null. Consumers already use `?.let`, so cover
 * screens/blur fall back to non-bitmap rendering.
 */
actual fun Image.toImageBitmap(): ImageBitmap? = null
