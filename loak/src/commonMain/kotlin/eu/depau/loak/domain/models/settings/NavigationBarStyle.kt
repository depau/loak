package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_navigation_bar_style_normal
import eu.depau.loak.generated.resources.option_navigation_bar_style_short
import org.jetbrains.compose.resources.StringResource

enum class NavigationBarStyle(val displayName: StringResource) {
	Normal(Res.string.option_navigation_bar_style_normal),
	Short(Res.string.option_navigation_bar_style_short)
}
