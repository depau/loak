package eu.depau.loak.di

// This class is a workaround for not being able to access :loakApp's R class inside :loak
interface ResourceProvider {
	val icLoak: Int
	val animPlaylist: Int
	val animArtist: Int
	val animPause: Int
}
