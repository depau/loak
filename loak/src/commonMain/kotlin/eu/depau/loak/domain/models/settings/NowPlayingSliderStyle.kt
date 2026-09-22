package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_now_playing_slider_style_flat
import eu.depau.loak.generated.resources.option_now_playing_slider_style_slim
import eu.depau.loak.generated.resources.option_now_playing_slider_style_squiggly
import eu.depau.loak.generated.resources.option_now_playing_slider_style_yoyo
import org.jetbrains.compose.resources.StringResource

enum class NowPlayingSliderStyle(val displayName: StringResource) {
	Flat(Res.string.option_now_playing_slider_style_flat),
	Squiggly(Res.string.option_now_playing_slider_style_squiggly),
	Slim(Res.string.option_now_playing_slider_style_slim),
	Yoyo(Res.string.option_now_playing_slider_style_yoyo)
}
