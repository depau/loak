package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_animation_style_expressive
import eu.depau.loak.generated.resources.option_animation_style_standard
import org.jetbrains.compose.resources.StringResource

enum class AnimationStyle(val displayName: StringResource) {
	Expressive(Res.string.option_animation_style_expressive),
	Standard(Res.string.option_animation_style_standard)
}
