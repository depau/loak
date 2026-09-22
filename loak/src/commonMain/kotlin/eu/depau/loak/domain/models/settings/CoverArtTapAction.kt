package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_cover_art_action_disabled
import eu.depau.loak.generated.resources.option_cover_art_action_show_lyrics
import org.jetbrains.compose.resources.StringResource

enum class CoverArtTapAction(val displayName: StringResource) {
	Disabled(Res.string.option_cover_art_action_disabled),
	ShowLyrics(Res.string.option_cover_art_action_show_lyrics)
}
