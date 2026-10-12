package eu.depau.loak

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.PlayLogManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.ScrobbleManager
import eu.depau.loak.domain.manager.ScrobblePlayerSource
import eu.depau.loak.domain.manager.SessionManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Scrobble CF at the logic layer. Drives [ScrobbleManager] with a fake player source
 * and a controllable [Clock] (instead of [Clock.System]) whose elapsed time advances in
 * lockstep with the virtual scheduler of `runTest`. The `delay()`s in the progress
 * tracker and the fire-and-forget network work both run on that scheduler, so
 * "how much has been played" and "when the scrobble threshold is crossed" are exact.
 * A fake Subsonic server (MockEngine, also on the test scheduler) records the
 * now-playing/submission requests; `playLog` is resolved from a minimal Koin graph.
 */
class ScrobbleManagerTest {

	private lateinit var playLog: PlayLogManager
	private lateinit var managerScope: CoroutineScope

	@AfterTest
	fun tearDownKoin() {
		stopKoin()
	}

	/** Mutable stand-in for the real player; the manager reads position/duration/isPlaying. */
	private class FakePlayerSource(
		override var duration: Long = 0,
		override var currentPosition: Long = 0,
		override var isPlaying: Boolean = false
	) : ScrobblePlayerSource

	/** Mutable [Clock]: the test "moves time" by advancing this before the scheduler. */
	private class FakeClock(var now: Instant = Instant.fromEpochMilliseconds(0)) : Clock {
		override fun now(): Instant = now
	}

	/** A fake Subsonic server, pinned to the test scheduler, recording scrobble ops. */
	private fun fakeServer(
		scheduler: TestCoroutineScheduler,
		requests: MutableList<Pair<String, Boolean>>,
	) = MockEngine(MockEngineConfig().apply {
		dispatcher = StandardTestDispatcher(scheduler)
		addHandler { request ->
			if (request.url.encodedPath.endsWith("/scrobble.view")) {
				requests += (request.url.parameters["id"] ?: "") to
					(request.url.parameters["submission"] == "true")
			}
			respond(
				content = TestFakes.pingJson(),
				status = HttpStatusCode.OK,
				headers = headersOf(HttpHeaders.ContentType, "application/json")
			)
		}
	})

	private fun scrobbleManager(
		scheduler: TestCoroutineScheduler,
		player: FakePlayerSource,
		clock: FakeClock,
		requests: MutableList<Pair<String, Boolean>>,
	): Pair<ScrobbleManager, PreferenceManager> {
		val settings = Settings()
		// No-arg Settings() on JVM maps to java.util.Preferences.userRoot(), which is
		// shared process-wide — clear it so each test starts from a pristine state.
		settings.clear()
		settings["instanceUrl"] = "http://fake.navidrome.org"
		val prefs = PreferenceManager(settings)
		val conn = ConnectivityManager(prefs)
		val stats = NetworkStatsManager(TestFakes.fakeNetworkStatsDao(), conn)
		val session = SessionManager(settings, prefs, stats, fakeServer(scheduler, requests))
		// A scope on the test scheduler, so the manager's fire-and-forget network
		// work is drained by runCurrent()/advanceTimeBy() exactly like its tracker.
		// Cancelled in the test's finally (before resetMain) so its pending tracker
		// coroutines never touch Dispatchers.Main after the test.
		managerScope = CoroutineScope(StandardTestDispatcher(scheduler))
		val manager = ScrobbleManager(
			playerSource = player,
			connectivityManager = conn,
			syncManager = null,
			sessionManager = session,
			scope = managerScope,
			preferenceManager = prefs,
			clock = clock,
			ioDispatcher = StandardTestDispatcher(scheduler),
		)
		// Resolve the injected `playLog` before the manager can touch it: Koin's
		// factory lambda runs at first resolution, which happens in `checkProgress` —
		// after `playLog` is assigned. Each test starts fresh.
		playLog = PlayLogManager(settings)
		startKoin { modules(module { single<PlayLogManager> { playLog } }) }
		return manager to prefs
	}

	/**
	 * Advance the fake clock by [ms], then the virtual scheduler by the same amount.
	 * Order matters: the tracker computes elapsed play time from `clock` (inside
	 * `checkProgress`), so the clock must have already moved when `delay()` resumes.
	 */
	private fun TestScope.elapseTime(clock: FakeClock, ms: Long) {
		clock.now = Instant.fromEpochMilliseconds(clock.now.toEpochMilliseconds() + ms)
		advanceTimeBy(ms)
		runCurrent()
	}

	@Test
	fun playStateTrueStartsTrackingAndSendsNowPlayingOnce() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		try {
			val clock = FakeClock()
			val player = FakePlayerSource()
			val requests = mutableListOf<Pair<String, Boolean>>()
			val (manager, _) = scrobbleManager(testScheduler, player, clock, requests)

			player.duration = 60_000
			player.isPlaying = true
			manager.onMediaChanged("song-1")
			manager.onPlayStateChanged(true)
			runCurrent()

			elapseTime(clock, 10_000)

			// Exactly one now-playing (submission=false) and no scrobble yet.
			val nowPlaying = requests.count { !it.second }
			assertEquals(1, nowPlaying, "starts playing sends exactly one now-playing")
			assertEquals(0, requests.count { it.second }, "no scrobble submission before threshold")

			// Second onPlayStateChanged(true) must not re-send now-playing.
			manager.onPlayStateChanged(true)
			runCurrent()
			assertEquals(1, requests.count { !it.second }, "now-playing is sent only once per track")
		} finally {
			managerScope.cancel()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun scrobbleFiresOnlyAfterPercentageThreshold() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		try {
			val clock = FakeClock()
			val player = FakePlayerSource()
			val requests = mutableListOf<Pair<String, Boolean>>()
			val (manager, _) = scrobbleManager(testScheduler, player, clock, requests)

			player.duration = 60_000 // threshold 50% = 30s
			manager.onMediaChanged("song-1")
			manager.onPlayStateChanged(true)
			runCurrent() // schedules the tracker's first delay (threshold - elapsed)

			// Below threshold: the tracker's next check has not fired yet.
			elapseTime(clock, 28_000)
			assertEquals(0, requests.count { it.second }, "28s of 60s: below threshold, no scrobble")

			// Cross the 50% threshold. The tracker's first delay was scheduled for the
			// threshold (coerced to at least MIN_CHECK_INTERVAL_MS=2000), so 2s later the
			// check runs and scrobbles.
			elapseTime(clock, 2_000) // 30s: exactly at threshold
			assertEquals(1, requests.count { it.second }, "30s of 60s: threshold crossed, scrobble fires")

			// Playing far past the threshold does not scrobble again.
			elapseTime(clock, 30_000)
			assertEquals(1, requests.count { it.second }, "a track is scrobbled at most once")
		} finally {
			managerScope.cancel()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun pausingAccumulatesPlayTime() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		try {
			val clock = FakeClock()
			val player = FakePlayerSource()
			val requests = mutableListOf<Pair<String, Boolean>>()
			val (manager, _) = scrobbleManager(testScheduler, player, clock, requests)

			player.duration = 60_000
			manager.onMediaChanged("song-1")
			manager.onPlayStateChanged(true)
			runCurrent()

			elapseTime(clock, 20_000)
			manager.onPlayStateChanged(false) // pause: 20s banked

			elapseTime(clock, 60_000) // 60s of wall clock, but only 20s played

			// Resume and play 15s more: cumulative 35s >= 50% of 60s.
			manager.onPlayStateChanged(true)
			runCurrent() // compute the next delay from the banked 20s
			elapseTime(clock, 15_000)

			assertEquals(1, requests.count { it.second }, "pause/resume accumulates played time across gaps")
		} finally {
			managerScope.cancel()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun mediaChangeResetsScrobbleState() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		try {
			val clock = FakeClock()
			val player = FakePlayerSource()
			val requests = mutableListOf<Pair<String, Boolean>>()
			val (manager, _) = scrobbleManager(testScheduler, player, clock, requests)

			player.duration = 60_000
			player.isPlaying = true
			manager.onMediaChanged("song-1")
			manager.onPlayStateChanged(true)
			runCurrent()

			elapseTime(clock, 30_000)
			assertEquals(1, requests.count { it.second }, "song-1 scrobbled at threshold")

			// Fresh media id resets hasScrobbledCurrent: song-2 can be scrobbled too.
			manager.onMediaChanged("song-2")
			runCurrent()
			assertEquals(1, requests.count { it.first == "song-2" && !it.second }, "new media id gets a now-playing")
			assertFalse(requests.any { it.first == "song-2" && it.second }, "song-2 not scrobbled yet")

			elapseTime(clock, 30_000)
			assertEquals(1, requests.count { it.first == "song-2" && it.second }, "song-2 scrobbles after its own threshold")
			assertEquals(2, requests.count { it.second }, "both tracks scrobbled exactly once")
			assertTrue(requests.any { it.first == "song-1" && it.second })
		} finally {
			managerScope.cancel()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun shortSongsNeverScrobbleEvenAtFullPlay() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		try {
			val clock = FakeClock()
			val player = FakePlayerSource()
			val requests = mutableListOf<Pair<String, Boolean>>()
			val (manager, _) = scrobbleManager(testScheduler, player, clock, requests)

			player.duration = 10_000 // < MIN_SCROBBLE_DURATION_MS (30s)
			manager.onMediaChanged("jingle")
			manager.onPlayStateChanged(true)
			runCurrent()

			elapseTime(clock, 10_000) // fully played, 100% past the 50% threshold
			assertEquals(0, requests.count { it.second }, "sub-MIN_SCROBBLE_DURATION track is never scrobbled")

			elapseTime(clock, 30_000) // even played way past its length
			assertEquals(0, requests.count { it.second }, "still never scrobbled")
		} finally {
			managerScope.cancel()
			Dispatchers.resetMain()
		}
	}

	@Test
	fun disableScrobblingSuppressesNetworkButTracksThreshold() = runTest {
		Dispatchers.setMain(StandardTestDispatcher(testScheduler))
		try {
			val clock = FakeClock()
			val player = FakePlayerSource()
			val requests = mutableListOf<Pair<String, Boolean>>()
			val (manager, prefs) = scrobbleManager(testScheduler, player, clock, requests)

			prefs.enableScrobbling = false
			player.duration = 60_000
			manager.onMediaChanged("song-1")
			manager.onPlayStateChanged(true)
			runCurrent()

			elapseTime(clock, 30_000) // threshold crossed

			// No network traffic at all — neither now-playing nor submission.
			assertTrue(requests.isEmpty(), "scrobbling disabled sends nothing")
		} finally {
			managerScope.cancel()
			Dispatchers.resetMain()
		}
	}
}
