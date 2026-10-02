@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package eu.depau.loak.desktopapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import dev.nucleusframework.darkmodedetector.isSystemInDarkMode

/**
 * Compose's `isSystemInDarkTheme()` reads the internal [LocalSystemTheme], which only Compose's
 * own AWT window provides; Nucleus' Tao windows don't, so the app stayed light. Provide it from
 * Nucleus' live OS detector (as `nucleusApplication` does, which doesn't reach window content).
 */
@OptIn(InternalComposeUiApi::class)
@Composable
internal fun ProvideSystemTheme(content: @Composable () -> Unit) {
	val isDark = isSystemInDarkMode()
	CompositionLocalProvider(
		LocalSystemTheme provides if (isDark) SystemTheme.Dark else SystemTheme.Light,
		content = content,
	)
}
