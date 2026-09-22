package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.theme_mode_dark
import eu.depau.loak.generated.resources.theme_mode_light
import eu.depau.loak.generated.resources.theme_mode_system
import org.jetbrains.compose.resources.StringResource

enum class ThemeMode(val title: StringResource) {
	System(Res.string.theme_mode_system),
	Dark(Res.string.theme_mode_dark),
	Light(Res.string.theme_mode_light)
}
