package eu.depau.loak.domain.manager

// desktop: the app has no system volume to read, so it is never "muted"
actual class VolumeProvider {
	actual fun read(): Float = 1f
}
