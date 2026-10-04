package eu.depau.loak.domain.manager

import eu.depau.loak.util.IoDispatcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import eu.depau.loak.data.database.entities.SyncActionType
import kotlin.time.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

interface ScrobblePlayerSource {
	val currentPosition: Long
	val duration: Long
	val isPlaying: Boolean
}

class ScrobbleManager(
	private val playerSource: ScrobblePlayerSource,
	private val connectivityManager: ConnectivityManager,
	private val syncManager: SyncManager,
	private val sessionManager: SessionManager,
	private val scope: CoroutineScope,
	private val preferenceManager: PreferenceManager
) : KoinComponent {
	// injected rather than passed in: each platform builds this class itself
	private val playLog: PlayLogManager by inject()

	private var currentMediaId: String? = null
	private var hasScrobbledCurrent = false
	private var hasSentNowPlaying = false
	private var progressJob: Job? = null
	private var accumulatedPlayTime: Long = 0
	/** When playback last started, while it plays; null while paused. */
	private var playingSince: Long? = null

	fun onMediaChanged(mediaId: String?) {
		currentMediaId = mediaId
		hasScrobbledCurrent = false
		hasSentNowPlaying = false
		accumulatedPlayTime = 0
		playingSince = null

		progressJob?.cancel()
		if (playerSource.isPlaying) {
			startProgressTracker()
			scrobbleNowPlaying(mediaId)
			hasSentNowPlaying = true
		}
	}

	fun onPlayStateChanged(isPlaying: Boolean) {
		if (isPlaying) {
			startProgressTracker()
			if (!hasSentNowPlaying) {
				scrobbleNowPlaying(currentMediaId)
				hasSentNowPlaying = true
			}
		} else {
			progressJob?.cancel()
			playingSince?.let { accumulatedPlayTime += now() - it }
			playingSince = null
		}
	}

	private fun now() = Clock.System.now().toEpochMilliseconds()

	private fun playedTime() = accumulatedPlayTime + (playingSince?.let { now() - it } ?: 0)

	private fun startProgressTracker() {
		progressJob?.cancel()
		if (playingSince == null) playingSince = now()
		progressJob = scope.launch(Dispatchers.Main) {
			// sleeps until enough has been played rather than waking the CPU every few seconds;
			// the length may only be known once the track has loaded
			while (isActive && !hasScrobbledCurrent) {
				val duration = playerSource.duration
				val left = if (duration <= 0) 0L else
					(duration * preferenceManager.scrobblePercentage).toLong() - playedTime()
				delay(left.coerceAtLeast(MIN_CHECK_INTERVAL_MS))
				checkProgress()
			}
		}
	}

	private fun checkProgress() {
		if (hasScrobbledCurrent) return

		val duration = playerSource.duration
		if (duration <= 0) return

		val percent = playedTime().toFloat() / duration.toFloat()
		val playedEnoughPercent = percent >= preferenceManager.scrobblePercentage
		val isValidSong = duration >= preferenceManager.minDurationToScrobble * 1000

		if (isValidSong && playedEnoughPercent) {
			currentMediaId?.let { playLog.recordSong(it) }
			scrobbleSubmission(currentMediaId)
			hasScrobbledCurrent = true
		}
	}

	private fun scrobbleSubmission(songId: String?) {
		if (!preferenceManager.enableScrobbling || songId == null) return

		scope.launch(IoDispatcher) {
			val currentTime = Clock.System.now()

			if (connectivityManager.isOnline.value) {
				try {
					sessionManager.api.scrobble(songId, submission = true, time = currentTime)
				} catch (_: Exception) {
					syncManager.enqueueAction(SyncActionType.SCROBBLE, songId, currentTime)
				}
			} else {
				syncManager.enqueueAction(SyncActionType.SCROBBLE, songId, currentTime)
			}
		}
	}

	private fun scrobbleNowPlaying(songId: String?) {
		if (!preferenceManager.enableScrobbling || songId == null) return

		if (!connectivityManager.isOnline.value) return

		scope.launch(IoDispatcher) {
			try {
				sessionManager.api.scrobble(songId, submission = false)
			} catch (_: Exception) {
			}
		}
	}

	private companion object {
		const val MIN_CHECK_INTERVAL_MS = 2_000L
	}
}
