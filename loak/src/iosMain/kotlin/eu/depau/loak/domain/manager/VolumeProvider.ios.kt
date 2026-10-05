package eu.depau.loak.domain.manager

import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.outputVolume

actual class VolumeProvider {
	actual fun read(): Float {
		// outputVolume is 0 when the hardware buttons are all the way down
		return AVAudioSession.sharedInstance().outputVolume
	}
}
