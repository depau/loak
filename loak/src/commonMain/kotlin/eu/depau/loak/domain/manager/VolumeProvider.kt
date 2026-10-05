package eu.depau.loak.domain.manager

/**
 * Reads the device's media volume, 0..1, for the app to notice a fully-muted device.
 *
 * A [read] on demand at the moment playback starts is enough for the volume-0 snackbar:
 * media (Android STREAM_MUSIC / iOS outputVolume) has no per-song app volume to report.
 * Platforms without an in-app volume (desktop, web) report 1f so they never warn.
 */
expect class VolumeProvider {
	fun read(): Float
}
