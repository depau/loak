package eu.depau.loak.domain.models

enum class PlaylistKind {
	Regular,

	/** AudioMuse-AI's clustering playlist: deleted and recreated on every run. */
	AudioMuseAutomatic,

	/** AudioMuse-AI's scheduled playlist (Sonic Fingerprint, …): emptied and refilled in place. */
	AudioMuseScheduled,

	/** Made on request in AudioMuse-AI, then left alone. */
	AudioMuseInstant,

	/** An AudioMuse-AI alchemy radio: refilled in place on its schedule, named like the recipe. */
	AudioMuseRadio,

	/** Navidrome smart playlist: the server picks the songs from rules. */
	Smart;

	val isAudioMuse get() = this == AudioMuseAutomatic || this == AudioMuseScheduled || this == AudioMuseInstant || this == AudioMuseRadio

	/** AudioMuse-AI rebuilds it, so edits made here may be lost. */
	val isRebuilt get() = this == AudioMuseAutomatic || this == AudioMuseScheduled || this == AudioMuseRadio
}

/** A playlist name with AudioMuse-AI's marker split off. [suffix] goes back on when renaming. */
data class PlaylistName(val display: String, val kind: PlaylistKind, val suffix: String = "") {
	fun rename(newDisplay: String) = newDisplay + suffix
}

// AudioMuse-AI's own matcher (tasks/ai/api.py); "Name_automatic (2)" are chunks of a big cluster
private val AUTOMATIC = Regex("""^(.*?)_automatic(\s*\(\d+\))?$""", RegexOption.IGNORE_CASE)
private val INSTANT = Regex("""^(.*?)_instant$""", RegexOption.IGNORE_CASE)
private val SCHEDULED = Regex("""^(.*?) by AudioMuse-AI$""", RegexOption.IGNORE_CASE)

/** AudioMuse-AI's scheduled playlists, which get their own cover. */
val SPECIAL_AUDIOMUSE_PLAYLISTS = setOf("Sonic Fingerprint", "Album of the Week")

/**
 * [radios]: the names of AudioMuse-AI's radios; they carry no marker, so only a connection to
 * AudioMuse-AI can tell them apart.
 */
fun parsePlaylistName(raw: String?, smart: Boolean, audioMuse: Boolean, radios: Set<String> = emptySet()): PlaylistName {
	val name = raw.orEmpty()
	if (smart) return PlaylistName(name, PlaylistKind.Smart)
	if (!audioMuse) return PlaylistName(name, PlaylistKind.Regular)
	if (name in radios) return PlaylistName(name, PlaylistKind.AudioMuseRadio)
	AUTOMATIC.matchEntire(name)?.let {
		// ponytail: a chunk keeps its " (2)" in the display name; renaming one puts the
		// suffix after it, which AudioMuse-AI then cleans up like any other cluster
		val (base, chunk) = it.destructured
		return PlaylistName(base + chunk, PlaylistKind.AudioMuseAutomatic, "_automatic")
	}
	INSTANT.matchEntire(name)?.let {
		return PlaylistName(it.groupValues[1], PlaylistKind.AudioMuseInstant, "_instant")
	}
	SCHEDULED.matchEntire(name)?.let {
		return PlaylistName(it.groupValues[1], PlaylistKind.AudioMuseScheduled, " by AudioMuse-AI")
	}
	return PlaylistName(name, PlaylistKind.Regular)
}
