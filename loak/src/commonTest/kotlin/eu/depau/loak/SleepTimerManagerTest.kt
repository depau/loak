package eu.depau.loak

import eu.depau.loak.domain.manager.SleepTimerManager
import eu.depau.loak.domain.manager.SleepTimerMode
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.shared.PlayerControl
import eu.depau.loak.ui.core.PlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * CF-09 (sleep timer) at the logic layer: the real [SleepTimerManager] drives a fake
 * [PlayerControl] backed by an in-memory [PlayerUiState] on a virtual (test) dispatcher,
 * so the time/song-count semantics run deterministically with no real player or I/O.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerManagerTest {

	private fun song(id: String) = DomainSong(
		id = id,
		title = "t",
		artistName = "a",
		artistId = "a1",
		albumTitle = null,
		albumId = null,
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = null,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = 0.seconds,
		bpm = null,
		contributors = emptyList(),
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 0,
		fileExtension = null,
		mimeType = null,
		filePath = null,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown,
		artists = emptyList(),
		albumArtists = emptyList()
	)

	/**
	 * Fake player: the manager only ever reads [uiState] and calls [pause]. The observer's
	 * emissions share the test's virtual scheduler, so `runCurrent`/`advanceUntilIdle` drive
	 * the manager's song-watching collector deterministically.
	 */
	private class FakePlayer(initial: PlayerUiState = PlayerUiState()) : PlayerControl {
		private val state = MutableStateFlow(initial)
		override val uiState = state
		var paused = false; private set

		override fun pause() {
			paused = true
			state.value = state.value.copy(isPaused = true)
		}

		/** Advance playback to [index], i.e. emit a new [PlayerUiState.currentIndex]. */
		fun playThrough(index: Int, scope: CoroutineScope) {
			scope.launch { state.value = state.value.copy(currentIndex = index) }
		}
	}

	// ---- time-based timer ----

	@Test
	fun timeTimerPausesAndResetsAfterElapsedDuration() = runTest(StandardTestDispatcher()) {
		val player = FakePlayer()
		val manager = SleepTimerManager(player, ioDispatcher = StandardTestDispatcher(testScheduler))

		manager.startTimer(30.seconds)
		assertTrue(manager.mode.value is SleepTimerMode.Time)

		advanceTimeBy(29.seconds)
		runCurrent()
		assertFalse(player.paused)
		assertTrue(manager.mode.value is SleepTimerMode.Time)

		advanceTimeBy(1.seconds)
		runCurrent()
		assertTrue(player.paused)
		assertEquals(SleepTimerMode.Disabled, manager.mode.value)
	}

	@Test
	fun stopTimerCancelsTimersWithoutPausing() = runTest(StandardTestDispatcher()) {
		val player = FakePlayer()
		val manager = SleepTimerManager(player, ioDispatcher = StandardTestDispatcher(testScheduler))

		manager.startTimer(30.seconds)
		manager.startSongsTimer(2)
		manager.stopTimer()

		assertEquals(SleepTimerMode.Disabled, manager.mode.value)
		assertFalse(player.paused)
	}

	// ---- songs-based timer ----

	@Test
	fun songsTimerPausesAndResetsWhenRemainingHitsZero() = runTest(StandardTestDispatcher()) {
		val player = FakePlayer(PlayerUiState(queue = (0 until 5).map { song(it.toString()) }, currentIndex = 0))
		val manager = SleepTimerManager(player, ioDispatcher = StandardTestDispatcher(testScheduler))

		manager.startSongsTimer(3)
		assertTrue(manager.mode.value is SleepTimerMode.Songs)

		// one song per index change; the manager drops the initial index before counting
		player.playThrough(1, backgroundScope)
		runCurrent()
		assertEquals(SleepTimerMode.Songs(2), manager.mode.value)

		player.playThrough(2, backgroundScope)
		runCurrent()
		assertEquals(SleepTimerMode.Songs(1), manager.mode.value)

		player.playThrough(3, backgroundScope)
		runCurrent()
		assertTrue(player.paused)
		assertEquals(SleepTimerMode.Disabled, manager.mode.value)
	}

	@Test
	fun endOfQueueTimerCountsSongsUntilQueueEnd() = runTest(StandardTestDispatcher()) {
		val player = FakePlayer(PlayerUiState(queue = (0 until 5).map { song(it.toString()) }, currentIndex = 2))
		val manager = SleepTimerManager(player, ioDispatcher = StandardTestDispatcher(testScheduler))

		manager.startEndOfQueueTimer()
		assertTrue(manager.mode.value is SleepTimerMode.EndOfQueue)

		// remaining = queue.size - currentIndex = 3; three index changes pause playback
		player.playThrough(3, backgroundScope)
		runCurrent()
		assertFalse(player.paused)

		player.playThrough(4, backgroundScope)
		runCurrent()
		assertFalse(player.paused)

		player.playThrough(5, backgroundScope)
		runCurrent()
		assertTrue(player.paused)
		assertEquals(SleepTimerMode.Disabled, manager.mode.value)
	}

	@Test
	fun endOfQueueTimerStaysDisabledWithNoCurrentSong() = runTest(StandardTestDispatcher()) {
		val player = FakePlayer(PlayerUiState(queue = (0 until 5).map { song(it.toString()) }, currentIndex = -1))
		val manager = SleepTimerManager(player, ioDispatcher = StandardTestDispatcher(testScheduler))

		manager.startEndOfQueueTimer()
		assertEquals(SleepTimerMode.Disabled, manager.mode.value)
		assertFalse(player.paused)
	}
}
