package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_mini_player_progress_style_hidden
import eu.depau.loak.generated.resources.option_mini_player_progress_style_seekable
import eu.depau.loak.generated.resources.option_mini_player_progress_style_visible
import org.jetbrains.compose.resources.StringResource

enum class MiniPlayerProgressStyle(val displayName: StringResource) {
	Hidden(Res.string.option_mini_player_progress_style_hidden),
	Visible(Res.string.option_mini_player_progress_style_visible),
	Seekable(Res.string.option_mini_player_progress_style_seekable)
}
