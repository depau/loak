package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.DomainReplayGain
import eu.depau.loak.domain.models.settings.ReplayGainMode

/**
 * Desktop: no system equaliser/replay-gain hardware layer to configure.
 * No-op; playback-side ReplayGain is not implemented yet (see DESIGN_CHANGES).
 */
actual class AudioGainManager {
	actual fun setAmplifierValues(withReplayGain: Float, withoutReplayGain: Float) {}
	actual fun setReplayGainMetadata(metadata: DomainReplayGain?) {}
	actual fun applyGainMode(mode: ReplayGainMode) {}
	actual fun resetGain() {}
}
