package eu.depau.loak.domain.manager

import eu.depau.loak.domain.models.DomainReplayGain
import eu.depau.loak.domain.models.settings.ReplayGainMode

/**
 * Desktop: rodio's Sink applies a linear gain to every sample, so ReplayGain folds into the
 * player's volume (gain * volume). This manager only relays "settings changed, recompute":
 * the player holds the actual state (volume, current song, prefs) and recomputes the gain.
 */
actual class AudioGainManager {
	private var onGainChanged: (() -> Unit)? = null

	/** The player registers this to receive gain updates when the settings change. */
	fun setOnGainChanged(listener: () -> Unit) {
		onGainChanged = listener
	}

	actual fun setAmplifierValues(withReplayGain: Float, withoutReplayGain: Float) {
		onGainChanged?.invoke()
	}

	actual fun setReplayGainMetadata(metadata: DomainReplayGain?) = Unit

	actual fun applyGainMode(mode: ReplayGainMode) {
		onGainChanged?.invoke()
	}

	actual fun resetGain() {
		onGainChanged?.invoke()
	}
}
