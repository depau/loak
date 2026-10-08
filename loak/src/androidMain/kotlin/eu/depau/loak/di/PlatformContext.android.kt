package eu.depau.loak.di

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.ThemeMode

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
actual fun rememberPlatformContext(): PlatformContext {
	val view = LocalView.current
	val context = LocalContext.current
	val activity = LocalActivity.current!!
	val inDarkTheme = isSystemInDarkTheme()
	val preferenceManager = koinInject<PreferenceManager>()
	val isDark = remember(preferenceManager.themeMode, inDarkTheme) {
		when (preferenceManager.themeMode) {
			ThemeMode.System -> inDarkTheme
			ThemeMode.Dark -> true
			ThemeMode.Light -> false
		}
	}
	val sizeClass = calculateWindowSizeClass(activity)
	SideEffect {
		activity.window?.let { window ->
			WindowCompat.getInsetsController(window, view)
				.isAppearanceLightStatusBars = !isDark
		}
	}
	return remember(sizeClass) {
		object : PlatformContext {
			override val platformType = PlatformType.Android
			override val name = "Android ${Build.VERSION.SDK_INT}"
			override val appVersion: String =
				context.packageManager
					.getPackageInfo(context.packageName, 0)
					.versionName.toString()
			override val sizeClass = sizeClass

			// Material You's wallpaper colors exist from Android 12
			@Composable
			override fun systemColorScheme(isDark: Boolean): ColorScheme? = when {
				Build.VERSION.SDK_INT < 31 -> null
				isDark -> dynamicDarkColorScheme(context)
				else -> dynamicLightColorScheme(context)
			}
		}
	}
}
