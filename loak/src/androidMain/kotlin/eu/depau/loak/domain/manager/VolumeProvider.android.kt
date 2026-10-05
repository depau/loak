package eu.depau.loak.domain.manager

import android.content.Context
import android.media.AudioManager

actual class VolumeProvider(
	private val context: Context
) {
	actual fun read(): Float {
		val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
			?: return 1f
		val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
		if (max <= 0) return 1f
		return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
	}
}
