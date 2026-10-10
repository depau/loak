package eu.depau.loak.domain.discover

import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.core.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** A preview currently being played, as the mini-player shows it. */
data class ActivePreview(
	val song: DiscoverSong,
	val isPlaying: Boolean,
	val progress: Float,
	val isEnded: Boolean = false
)

/** Deezer preview links go stale after a while, so this fresh after that long. */
internal val PREVIEW_URL_TTL: Duration = 10.minutes

/** "Main player is actually audible" — some song loaded and not paused. */
fun mainPlaybackActive(state: PlayerUiState): Boolean =
	state.currentSong != null && !state.isPaused

/**
 * True when [song]'s preview URL needs a fresh Deezer fetch before playing: the URL is missing,
 * or the one we hold is older than [PREVIEW_URL_TTL]. A URL that just arrived (from a live
 * search, e.g.) has a null [lastFetchedAt] and counts as fresh — Deezer gave it to us this
 * session. Whether a refetch is possible is the caller's job ([DiscoverSong.deezerId]).
 */
fun shouldRefreshPreview(song: DiscoverSong, lastFetchedAt: Instant?, now: Instant): Boolean {
	val fresh = song.previewUrl != null && (lastFetchedAt == null || now - lastFetchedAt <= PREVIEW_URL_TTL)
	return !fresh
}

/**
 * First index >= [fromIndex] whose song could play a preview (it has a URL, or a Deezer id
 * to refetch one from); -1 when the rest of the list can't. Skips tracks without previews.
 */
fun nextPreviewable(songs: List<DiscoverSong>, fromIndex: Int): Int {
	var i = fromIndex
	while (i < songs.size) {
		val song = songs[i]
		if (song.previewUrl != null || song.deezerId != null) return i
		i++
	}
	return -1
}

/**
 * Plays Deezer 30-second previews in parallel with the main player, whose playback it
 * suspends and resumes around each preview. One instance per app (Koin singleton).
 *
 * Main-player coordination: if the main player is audible when a preview starts, it is
 * paused and resumed only when the preview ends on its own (or the user closes it). If the
 * user starts the main player while a preview is active, the preview stops silently.
 *
 * Preview URLs go stale, so a [DiscoverSong] older than [PREVIEW_URL_TTL] (or with no URL)
 * is re-fetched from Deezer before playing, when it carries a Deezer id.
 */
class PreviewManager(
	private val previewPlayer: PreviewPlayer,
	private val deezerClient: DeezerClient,
	private val mediaPlayerProvider: () -> MediaPlayerViewModel,
	private val clock: () -> Instant = { Clock.System.now() }
) {
	// the players only take calls on the main thread; network calls switch away on their own
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

	/** When each song's preview was fetched, so stale URLs get refreshed. */
	private val fetchedAt = mutableMapOf<String, Instant>()

	private val _activePreview = MutableStateFlow<ActivePreview?>(null)
	/** The preview now playing, or null when nothing is. */
	val activePreview: StateFlow<ActivePreview?> = _activePreview.asStateFlow()

	/** Whether the main player was audible when the preview started — to resume it on end. */
	@Volatile private var resumeMainOnEnd = false

	/** A [playAll] list being stepped through, advanced as each preview ends. */
	@Volatile private var playAllQueue: List<DiscoverSong>? = null
	private var playAllIndex = 0

	init {
		scope.launch {
			previewPlayer.state.collect { state ->
				val active = _activePreview.value ?: return@collect
				if (state.isEnded) {
					onNaturalEnd(active)
				} else {
					_activePreview.value = active.copy(
						isPlaying = state.isPlaying,
						progress = progressOf(state),
						isEnded = false
					)
				}
			}
		}
		scope.launch {
			val mainPlayer = mediaPlayerProvider()
			var wasPlaying = mainPlaybackActive(mainPlayer.uiState.value)
			mainPlayer.uiState
				.map(::mainPlaybackActive)
				.distinctUntilChanged()
				.collect { isPlaying ->
					if (isPlaying && !wasPlaying && _activePreview.value != null) {
						// user started the main player mid-preview: drop the preview, no resume
						resumeMainOnEnd = false
						playAllQueue = null
						stopPreview()
					}
					wasPlaying = isPlaying
				}
		}
	}

	/** Starts [song]'s preview, re-fetching a fresh URL first when it's stale. */
	fun play(song: DiscoverSong) {
		cancelPlayAll()
		scope.launch {
			val ready = provision(song) ?: return@launch
			startPreview(ready)
		}
	}

	/** Plays [song] if it isn't; pauses/resumes it if it is. */
	fun toggle(song: DiscoverSong) {
		val active = _activePreview.value
		if (active?.song?.id == song.id) {
			if (active.isPlaying) pause() else resume()
		} else {
			play(song)
		}
	}

	fun pause() {
		previewPlayer.pause()
	}

	fun resume() {
		previewPlayer.resume()
	}

	/** Stops the preview (resuming the main player if it was playing before it) and any [playAll]. */
	fun stop() {
		cancelPlayAll()
		finishPreview()
	}

	/** Plays the previews of [songs] in order, skipping tracks that can't preview. */
	fun playAll(songs: List<DiscoverSong>) {
		cancelPlayAll()
		playAllQueue = songs
		playAllIndex = 0
		playQueueNext()
	}

	private fun playQueueNext() {
		val songs = playAllQueue ?: return
		val index = nextPreviewable(songs, playAllIndex)
		if (index == -1) {
			playAllQueue = null
			return
		}
		playAllIndex = index
		scope.launch {
			val ready = provision(songs[index]) ?: run {
				if (playAllQueue === songs) {
					playAllIndex = index + 1
					playQueueNext()
				}
				return@launch
			}
			if (playAllQueue === songs) startPreview(ready)
		}
	}

	private fun cancelPlayAll() {
		playAllQueue = null
	}

	/**
	 * Refetches the Deezer track when its preview is stale or missing, returning a song with
	 * a playable URL, or null when neither URL nor Deezer id can provide one.
	 */
	private suspend fun provision(song: DiscoverSong): DiscoverSong? {
		if (song.deezerId != null && shouldRefreshPreview(song, fetchedAt[song.id], clock())) {
			val refetched = deezerClient.getTrack(song.deezerId)
			if (refetched?.previewUrl != null) return refetched
		}
		return song.previewUrl?.let { song }
	}

	/** Pauses the main player if it's audible, then starts the preview. */
	private fun startPreview(song: DiscoverSong) {
		val mainPlayer = mediaPlayerProvider()
		val wasPlaying = mainPlaybackActive(mainPlayer.uiState.value)
		resumeMainOnEnd = wasPlaying
		if (wasPlaying) mainPlayer.pause()
		previewPlayer.play(song.previewUrl.orEmpty())
		_activePreview.value = ActivePreview(song = song, isPlaying = true, progress = 0f)
		// the URL is fresh *now*; age from here, so a tap 10 min later re-fetches it
		fetchedAt[song.id] = clock()
	}

	/** The preview finished on its own: resume the main player if it was playing before. */
	private fun onNaturalEnd(active: ActivePreview) {
		if (active.song.id != _activePreview.value?.song?.id) return // superseded already
		finishPreview()
		val queue = playAllQueue
		if (queue != null) {
			playAllIndex += 1
			playQueueNext()
		}
	}

	private fun finishPreview() {
		val resume = resumeMainOnEnd
		resumeMainOnEnd = false
		stopPreview()
		if (resume) {
			runCatching { mediaPlayerProvider().resume() }
		}
	}

	private fun stopPreview() {
		_activePreview.value = null
		previewPlayer.stop()
	}

	private fun progressOf(state: PreviewPlayerState): Float =
		if (state.durationMs > 0) {
			(state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
		} else {
			0f
		}
}
