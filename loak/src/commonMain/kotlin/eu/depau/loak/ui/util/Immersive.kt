package eu.depau.loak.ui.util

import androidx.compose.runtime.Composable

/** Hides the system bars (Android) or goes full screen (desktop) while in the composition. */
@Composable
expect fun Immersive()
