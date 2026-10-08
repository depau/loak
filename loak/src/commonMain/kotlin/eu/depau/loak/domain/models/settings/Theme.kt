package eu.depau.loak.domain.models.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import dev.zt64.compose.pipette.HsvColor
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.theme_dynamic
import eu.depau.loak.generated.resources.theme_seeded
import org.jetbrains.compose.resources.StringResource
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.manager.PreferenceManager

/**
 * Theme choices that the user can choose from
 */
enum class Theme(val title: StringResource) {

	/**
	 * The app will be themed based on a "seed" colour.
	 *
	 * When this is selected, `accentColor(H/S/V)` settings
	 * will be exposed in the UI as a colour picker.
	 */
	Seeded(Res.string.theme_seeded),

	/**
	 * The app follows the system's colors: Android's wallpaper, the desktop accent.
	 * Where there are none, Lo'ak's default scheme.
	 */
	Dynamic(Res.string.theme_dynamic);

	@Composable
	fun colorScheme(): ColorScheme {
		val platformContext = LocalPlatformContext.current
		val inDarkTheme = isSystemInDarkTheme()
		val preferenceManager = koinInject<PreferenceManager>()
		val isDark = remember(preferenceManager.themeMode, inDarkTheme) {
			when (preferenceManager.themeMode) {
				ThemeMode.System -> inDarkTheme
				ThemeMode.Dark -> true
				ThemeMode.Light -> false
			}
		}
		return when (this) {
			Dynamic -> platformContext.systemColorScheme(isDark) ?: remember(isDark) {
				if (isDark)
					darkColorScheme()
				else expressiveLightColorScheme()
			}

			Seeded -> rememberDynamicColorScheme(
				seedColor = HsvColor(
					hue = preferenceManager.paletteAccentH,
					saturation = 1f,
					value = 1f
				).toColor(),
				isDark = isDark,
				specVersion = ColorSpec.SpecVersion.SPEC_2025,
				style = preferenceManager.paletteStyle
			)
		}
	}
}
