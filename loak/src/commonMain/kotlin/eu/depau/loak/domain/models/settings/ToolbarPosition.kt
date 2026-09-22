package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_position_bottom
import eu.depau.loak.generated.resources.option_position_top
import org.jetbrains.compose.resources.StringResource

enum class ToolbarPosition(val displayName: StringResource) {
	Top(Res.string.option_position_top),
	Bottom(Res.string.option_position_bottom)
}
