package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_explicit_playback_allowed
import eu.depau.loak.generated.resources.option_explicit_playback_skip
import eu.depau.loak.generated.resources.option_explicit_playback_skip_session
import org.jetbrains.compose.resources.StringResource

enum class ExplicitContentPlayback(val displayName: StringResource) {
	Allowed(Res.string.option_explicit_playback_allowed),
	Skip(Res.string.option_explicit_playback_skip),
	SkipForThisSession(Res.string.option_explicit_playback_skip_session)
}
