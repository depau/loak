package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_mini_player_style_detached
import eu.depau.loak.generated.resources.option_mini_player_style_unified
import org.jetbrains.compose.resources.StringResource

enum class MiniPlayerStyle(val displayName: StringResource) {
	Unified(Res.string.option_mini_player_style_unified),
	Detached(Res.string.option_mini_player_style_detached)
}
