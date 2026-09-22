package eu.depau.loak.domain.manager

import androidx.media3.common.util.UnstableApi
import eu.depau.loak.domain.models.DomainReplayGain
import eu.depau.loak.domain.models.settings.ReplayGainMode
import eu.depau.loak.exoplayer.AudioGainProcessor

@UnstableApi
actual class AudioGainManager(
	private val audioGainProcessor: AudioGainProcessor
) {
    actual fun applyGainMode(
        mode: ReplayGainMode
    ) {
		audioGainProcessor.applyGainMode(mode)
    }

    actual fun resetGain() {
		audioGainProcessor.resetGain()
    }

	actual fun setAmplifierValues(withReplayGain: Float, withoutReplayGain: Float) {
		audioGainProcessor.rgAmpValue = withReplayGain
		audioGainProcessor.ampValue = withoutReplayGain
	}

	actual fun setReplayGainMetadata(metadata: DomainReplayGain?) {
		audioGainProcessor.setReplayGainMetadata(metadata)
	}
}
