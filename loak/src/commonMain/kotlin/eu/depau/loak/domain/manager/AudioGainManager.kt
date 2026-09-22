package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.DomainReplayGain
import eu.depau.loak.domain.models.settings.ReplayGainMode

expect class AudioGainManager {
	fun setAmplifierValues(withReplayGain: Float, withoutReplayGain: Float)
	fun setReplayGainMetadata(metadata: DomainReplayGain?)
	fun applyGainMode(mode: ReplayGainMode)
	fun resetGain()
}
