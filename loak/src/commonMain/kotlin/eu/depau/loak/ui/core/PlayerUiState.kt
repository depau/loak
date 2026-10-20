package eu.depau.loak.ui.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.PlaybackDetails

@Serializable
data class PlayerUiState(
	val queue: List<DomainSong> = emptyList(),
	val currentSong: DomainSong? = null,
	val currentCollection: DomainSongCollection? = null,
	val currentIndex: Int = -1,
	val isPaused: Boolean = false,
	val isShuffleEnabled: Boolean = false,
	val repeatMode: Int = 0,
	val progress: Float = 0f,
	val isLoading: Boolean = false,
	/** The songs of an instant mix are being fetched, the seed (if known) already playing. */
	@Transient
	val isBuildingMix: Boolean = false,
	val playbackSpeed: Float = 1.0f,
	@Transient
	val playbackDetails: PlaybackDetails? = null,
	val instantMix: InstantMix? = null
) {
	/** The player is working on something the user asked for: buffering, or building a mix. */
	val isBusy: Boolean get() = isLoading || isBuildingMix
}

/** The seed's name and the songs of an instant mix. */
@Serializable
data class InstantMix(val seedName: String, val songIds: Set<String>)
