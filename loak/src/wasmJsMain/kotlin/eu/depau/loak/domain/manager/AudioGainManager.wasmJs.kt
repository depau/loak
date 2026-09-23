package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.DomainReplayGain
import eu.depau.loak.domain.models.settings.ReplayGainMode

/**
 * Web: the equaliser/ReplayGain output processing is gated off (see plan), so
 * no amplifier exists to configure. No-op keeps the settings screen harmless.
 */
actual class AudioGainManager {
	actual fun setAmplifierValues(withReplayGain: Float, withoutReplayGain: Float) {}
	actual fun setReplayGainMetadata(metadata: DomainReplayGain?) {}
	actual fun applyGainMode(mode: ReplayGainMode) {}
	actual fun resetGain() {}
}
