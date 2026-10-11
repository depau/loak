package eu.depau.loak.ui.screens.lyrics.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.lyrics.LyricsLine
import eu.depau.loak.domain.models.lyrics.LyricsResult
import eu.depau.loak.domain.repositories.LOCAL_PROVIDER
import eu.depau.loak.domain.repositories.LyricsRepository
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_lyrics_saved
import eu.depau.loak.generated.resources.notice_lyrics_save_failed
import eu.depau.loak.ui.core.UiState
import kotlin.time.Duration.Companion.milliseconds

class LyricsScreenViewModel(
	private val song: DomainSong?,
	private val repository: LyricsRepository,
	private val snackBarManager: SnackBarManager
) : ViewModel() {
	/** Lyrics found for the current song, one per provider (or the cached/saved lyrics). */
	val lyricsState: StateFlow<UiState<List<LyricsResult>>>
		field = MutableStateFlow<UiState<List<LyricsResult>>>(UiState.Loading())

	/** Index into [lyricsState] data of the lyrics currently shown. */
	val selectedProviderIndex: StateFlow<Int>
		field = MutableStateFlow(0)

	/** User-adjustable sync offset in milliseconds; positive = lyrics appear earlier. */
	val lyricOffset: StateFlow<Long>
		field = MutableStateFlow(0L)

	val isSaving: StateFlow<Boolean>
		field = MutableStateFlow(false)

	val currentResult: LyricsResult?
		get() = (lyricsState.value as? UiState.Success)?.data?.getOrNull(
			selectedProviderIndex.value
		)

	init {
		refreshResults()
	}

	private fun adjustOffsets(lines: List<LyricsLine>, offsetMs: Long): List<LyricsLine> {
		if (offsetMs == 0L) return lines
		return lines.map { line ->
			line.time?.let { time -> line.copy(time = time + offsetMs.milliseconds) } ?: line
		}
	}

	fun refreshResults() {
		viewModelScope.launch {
			if (song == null) {
				lyricsState.value = UiState.Success(emptyList())
				return@launch
			}
			lyricsState.value = UiState.Loading()
			try {
				lyricsState.value = UiState.Success(repository.fetchAllLyrics(song))
				selectedProviderIndex.value = 0
				lyricOffset.value = 0L
			} catch (e: Exception) {
				lyricsState.value = UiState.Error(e)
			}
		}
	}

	fun selectProvider(index: Int) {
		if (index == selectedProviderIndex.value) return
		selectedProviderIndex.value = index
		lyricOffset.value = 0L
	}

	/** Shift the synced lyrics by [deltaMs]; positive = lyrics appear earlier (feishin sign). */
	fun adjustOffset(deltaMs: Long) {
		val newOffset = (lyricOffset.value + deltaMs).coerceIn(-200000L, 200000L)
		lyricOffset.value = newOffset
	}

	/**
	 * Bake the current offset into the lyrics and store them as this device's saved lyrics.
	 * During the live preview the display compares [currentDuration] with `line.time`; to make a
	 * line be highlighted `offsetMs` earlier we treat the adjusted time as `time - offset`,
	 * so the saved copy must store `time = originalTime - offset`.
	 */
	fun saveLyrics() {
		val result = currentResult ?: return
		val offset = lyricOffset.value
		if (offset == 0L && result.providerName == LOCAL_PROVIDER) {
			// already saved, nothing changed
			snackBarManager.notify(Res.string.notice_lyrics_saved)
			return
		}
		// rebuild the raw content from lines so the offset is baked in for good
		val content = result.lines
			.let { adjustOffsets(it, -offset) }
			.joinToString("\n") { line -> line.toLrc() }
		viewModelScope.launch {
			isSaving.value = true
			try {
				repository.saveLyrics(song!!, content)
				// show the saved copy as the active provider
				lyricsState.value = UiState.Success(repository.fetchAllLyrics(song!!))
				selectedProviderIndex.value =
					(lyricsState.value as UiState.Success).data.indexOfFirst {
						it.providerName == LOCAL_PROVIDER
					}.coerceAtLeast(0)
				lyricOffset.value = 0L
				snackBarManager.notify(Res.string.notice_lyrics_saved)
			} catch (e: Exception) {
				snackBarManager.notify(Res.string.notice_lyrics_save_failed)
			} finally {
				isSaving.value = false
			}
		}
	}

	private fun LyricsLine.toLrc(): String {
		val t = time ?: return text
		val m = t.inWholeMinutes.toString().padStart(2, '0')
		val s = (t.inWholeSeconds % 60).toString().padStart(2, '0')
		val ms = ((t.inWholeMilliseconds % 1000) / 10).toString().padStart(2, '0')
		return "[$m:$s.$ms]$text"
	}
}
