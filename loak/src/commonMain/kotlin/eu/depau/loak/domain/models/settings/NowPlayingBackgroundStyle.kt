package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_now_playing_background_style_dynamic
import eu.depau.loak.generated.resources.option_now_playing_background_style_static
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingBackgroundStyle(val displayName: StringResource) {
	Static(Res.string.option_now_playing_background_style_static),
	Dynamic(Res.string.option_now_playing_background_style_dynamic)
}
