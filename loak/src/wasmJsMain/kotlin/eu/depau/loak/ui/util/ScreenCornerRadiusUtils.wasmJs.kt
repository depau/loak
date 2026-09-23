package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp

/**
 * Web: browser windows have no device corner radius, so the default is kept.
 */
@Composable
actual fun rememberScreenCornerRadius(defaultRadius: Dp): Dp =
	remember(defaultRadius) { defaultRadius }
