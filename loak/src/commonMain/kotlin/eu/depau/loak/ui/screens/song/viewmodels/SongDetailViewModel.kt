package eu.depau.loak.ui.screens.song.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_album_replay_gain
import eu.depau.loak.generated.resources.info_track_album
import eu.depau.loak.generated.resources.info_track_artist
import eu.depau.loak.generated.resources.info_track_bit_depth
import eu.depau.loak.generated.resources.info_track_bitrate
import eu.depau.loak.generated.resources.info_track_channel_count
import eu.depau.loak.generated.resources.info_track_disc_number
import eu.depau.loak.generated.resources.info_track_duration
import eu.depau.loak.generated.resources.info_track_file_size
import eu.depau.loak.generated.resources.info_track_format
import eu.depau.loak.generated.resources.info_track_genre
import eu.depau.loak.generated.resources.info_track_name
import eu.depau.loak.generated.resources.info_track_number
import eu.depau.loak.generated.resources.info_track_path
import eu.depau.loak.generated.resources.info_track_replay_gain
import eu.depau.loak.generated.resources.info_track_replay_gain_effective
import eu.depau.loak.generated.resources.info_track_sampling_rate
import eu.depau.loak.generated.resources.info_track_year
import org.jetbrains.compose.resources.StringResource
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.repositories.CollectionRepository
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.util.effectiveGain
import eu.depau.loak.util.toFileSize
import eu.depau.loak.util.toHoursMinutesSeconds

class SongDetailViewModel(
	songId: String,
	private val repository: CollectionRepository,
	private val preferenceManager: PreferenceManager
) : ViewModel() {
	val songState: StateFlow<UiState<DomainSong>>
		field = MutableStateFlow<UiState<DomainSong>>(UiState.Loading())

	val info: StateFlow<List<Pair<StringResource, String?>>>
		field = MutableStateFlow(emptyList())

	init {
		viewModelScope.launch {
			val song = repository.getSongById(songId)
			if (song != null) {
				songState.value = UiState.Success(song)
				info.value = getInfo(song)
			} else {
				songState.value = UiState.Error(Exception("Unknown song"))
			}
		}
	}

	fun getInfo(song: DomainSong): PersistentList<Pair<StringResource, String?>> =
		persistentListOf(
			Res.string.info_track_name to song.title,
			Res.string.info_track_artist to song.artistName,
			Res.string.info_track_album to song.albumTitle,

			Res.string.info_track_number to song.trackNumber.toString(),
			Res.string.info_track_disc_number to song.discNumber.toString(),
			Res.string.info_track_year to song.year.toString(),
			Res.string.info_track_genre to song.genre,

			Res.string.info_track_duration to song.duration.toHoursMinutesSeconds(),
			Res.string.info_track_format to song.mimeType,
			Res.string.info_track_bitrate to song.bitRate?.let { "$it kbps" },
			Res.string.info_track_bit_depth to song.bitDepth?.toString(),
			Res.string.info_track_sampling_rate to song.sampleRate?.let { "$it Hz" },
			Res.string.info_track_channel_count to song.audioChannelCount?.toString(),

			Res.string.info_track_file_size to song.fileSize.toFileSize(),
			Res.string.info_track_path to song.filePath,

			Res.string.info_track_replay_gain to song.replayGain?.trackGain?.let { "$it dB" },
			Res.string.info_album_replay_gain to song.replayGain?.albumGain?.let { "$it dB" },
			Res.string.info_track_replay_gain_effective to song.replayGain?.effectiveGain(
				preferenceManager.replayGainMode
			)?.toString()
		)
}
