package eu.depau.loak.domain.models.settings

import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_album_replay_gain
import eu.depau.loak.generated.resources.info_dynamic_replay_gain
import eu.depau.loak.generated.resources.info_track_replay_gain
import eu.depau.loak.generated.resources.option_off
import org.jetbrains.compose.resources.StringResource

enum class ReplayGainMode(val displayName: StringResource) {
	Off(Res.string.option_off),
	Track(Res.string.info_track_replay_gain),
	Album(Res.string.info_album_replay_gain),
	Dynamic(Res.string.info_dynamic_replay_gain)
}
