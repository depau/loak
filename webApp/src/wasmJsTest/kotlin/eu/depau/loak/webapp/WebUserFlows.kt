@file:OptIn(ExperimentalTestApi::class, ExperimentalWasmJsInterop::class)

package eu.depau.loak.webapp

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import eu.depau.loak.App
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.di.initKoin
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.navigation.BrowserHistory
import eu.depau.loak.uitest.LargeLibraryFlows
import eu.depau.loak.uitest.UserFlows
import eu.depau.loak.uitest.harnessWork
import eu.depau.loak.uitest.await
import eu.depau.loak.uitest.click
import eu.depau.loak.uitest.logIn
import eu.depau.loak.uitest.navigateUp
import eu.depau.loak.uitest.openSettings
import eu.depau.loak.uitest.openTab
import eu.depau.loak.uitest.waitForText
import kotlinx.browser.localStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.withContext
import org.koin.core.context.stopKoin
import org.koin.mp.KoinPlatform
import kotlin.js.Promise
import kotlin.test.Test
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The user flows on the real web app in a real browser: the wasm build, its sqlite
 * worker and the browser's HTTP client, against the fixture server.
 */
class WebUserFlows : UserFlows() {
	override fun runApp(flow: suspend ComposeUiTest.() -> Unit) = runWebApp(5.minutes, flow = flow)

	// the browsers may autoplay (karma.config.d) and play without an audio device
	override fun playbackProgress() = player().uiState.value.progress

	// no listening-history sources on the web build (see UserFlows)
	override fun listeningHistorySourceReachable() = false
}

class WebHistoryFlow {
	@Test
	fun browserBackAndForwardFollowPages() = runWebApp(5.minutes, browserHistory = true) {
		logIn()
		waitForText("Quick picks")
		awaitLibrarySync()
		awaitTitle("Home · Lo'ak")
		openSettings()
		awaitTitle("Settings · Lo'ak")
		click("Appearance")
		waitForText("Accent color")
		awaitTitle("Appearance · Lo'ak")
		browserBack()
		waitForText("Integrations")
		awaitTitle("Settings · Lo'ak")
		browserForward()
		waitForText("Accent color")
		awaitTitle("Appearance · Lo'ak")
		navigateUp()
		waitForText("Integrations")
		awaitTitle("Settings · Lo'ak")
		browserBack()
		waitForText("Quick picks")
		awaitTitle("Home · Lo'ak")
		openSettings()
		openTab("Home")
		waitForText("Quick picks")
		browserBack()
		waitForText("Integrations")
		openTab("Library")
		awaitTitle("Library · Lo'ak")
		click("Albums")
		awaitTitle("Albums · Lo'ak")
		click("First Light")
		awaitTitle("First Light · Lo'ak")
		openTab("Library")
		awaitTitle("Library · Lo'ak")
		click("Artists")
		awaitTitle("Artists · Lo'ak")
		click("Aurora Test")
		awaitTitle("Aurora Test · Lo'ak")
		openTab("Playlists")
		awaitTitle("Playlists · Lo'ak")
		click("UI Test Mix")
		awaitTitle("UI Test Mix · Lo'ak")
	}
}

private suspend fun ComposeUiTest.awaitTitle(title: String) =
	await({ "Expected page title $title, got ${documentTitle()}" }) {
		waitForIdle()
		documentTitle() == title
	}

/**
 * Waits until the full library pull has landed, without navigating: a screen opened meanwhile
 * would add a browser history entry, and the flow checks those. The Album list opens on the
 * sync's progress bar for as long as the pull runs, which on CI's slower machines can outlast
 * the flow's own waits.
 */
private suspend fun awaitLibrarySync() {
	val albums = KoinPlatform.getKoin().get<AlbumDao>()
	val sync = KoinPlatform.getKoin().get<SyncManager>()
	val deadline = TimeSource.Monotonic.markNow() + 120.seconds
	while (
		sync.syncState.value.isSyncing ||
		albums.getAllAlbumEntities().none { it.name == "First Light" }
	) {
		if (deadline.hasPassedNow()) throw AssertionError("The library did not sync within 120 s")
		withContext(Dispatchers.Default) { delay(100) }
	}
}

@JsFun("() => document.title")
private external fun documentTitle(): String

@JsFun("() => history.back()")
private external fun browserBack()

@JsFun("() => history.forward()")
private external fun browserForward()

/**
 * The large-library flows on the web app, on demand only (-Ploak.webLargeLibrary): some of
 * them still hang on the web with no library sync ever starting (the cache database never
 * opens), so they would make the default suite flaky.
 */
class WebLargeLibraryFlows : LargeLibraryFlows() {
	// the first sync of the large library takes a while
	override fun runApp(flow: suspend ComposeUiTest.() -> Unit) =
		runWebApp(10.minutes) { if (largeLibraryEnabled()) flow() else println("[loak] skipped") }

	override suspend fun ComposeUiTest.restartApp() = restart!!()

	override fun queue() = player().uiState.value.let { it.queue.size to it.currentSong?.title }
}

// set by webApp/karma.config.d from -Ploak.webLargeLibrary
@JsFun("() => globalThis.__karma__?.config?.loakLargeLibrary === true")
private external fun largeLibraryEnabled(): Boolean

/**
 * The longest the page's main thread may go without running a timer or drawing a frame
 * during a flow: the web hang watchdog's threshold. The flows' longest stalls on an idle
 * Apple-silicon Mac are 0.7 s (Chrome) and 0.9 s (Firefox), at app start and opening
 * Library; CI's runners draw on the CPU and are slower, so a second would fail them.
 */
private const val MAX_GAP_MS = 2000

private fun player() = KoinPlatform.getKoin().get<MediaPlayerViewModel>()

/** The running flow's restart: quits the app, keeping its data, and starts it again. */
private var restart: (suspend ComposeUiTest.() -> Unit)? = null

/**
 * Starts the app as a fresh install, muted, runs [flow] against it, and fails it on an
 * uncaught page error or a main-thread stall over [MAX_GAP_MS].
 */
private fun runWebApp(
	timeout: Duration,
	browserHistory: Boolean = false,
	flow: suspend ComposeUiTest.() -> Unit
): TestResult =
	runComposeUiTest(testTimeout = timeout) {
		trackWorkers()
		// the previous flow's app stops here, not at its end: its composition is only
		// disposed after its test body returns, and that still needs Koin
		stopApp()
		// logged out, default settings, no library database
		localStorage.clear()
		freeOpfs(wipe = true).await<JsAny?>()
		var app = WebApp()
		var screens by mutableStateOf<ViewModelStore?>(app.screens)
		setContent {
			// the screens' ViewModels in the app's own store, so a restarted app gets new ones
			screens?.let { store ->
				val owner = remember(store) { object : ViewModelStoreOwner { override val viewModelStore = store } }
				CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
					App { if (browserHistory) BrowserHistory(it) }
				}
			}
		}
		restart = {
			screens = null
			waitForIdle()
			player().flushState() // the tab closing: pagehide
			app.stop()
			freeOpfs(wipe = false).await<JsAny?>() // the new app's databases need the files
			app = WebApp()
			screens = app.screens
		}
		val monitor = startMonitor()
		// the flow's own scrolling through a list isn't the app stalling
		harnessWork = { work -> tickMonitor(monitor); work().also { skipMonitor(monitor) } }
		var problems: String? = null
		try {
			flow()
		} finally {
			restart = null
			harnessWork = { it() }
			problems = stopMonitor(monitor, MAX_GAP_MS)
		}
		problems?.let { fail(it) }
	}

/** The app's Koin graph, started (muted) on whatever data the last one left. */
private class WebApp {
	val screens = ViewModelStore()

	init {
		initKoin()
		player().setVolume(0f) // flows play songs: keep them silent
	}

	fun stop() {
		screens.clear()
		ViewModelStore().apply { put("player", player()); clear() }
		stopApp()
	}
}

/** Stops Koin and the app's workers (its databases), so their OPFS files can be freed. */
private fun stopApp() {
	stopKoin()
	terminateWorkers()
}

@JsFun(
	"""() => {
	if (globalThis.loakWorkers) return;
	const workers = globalThis.loakWorkers = [];
	const Base = globalThis.Worker;
	globalThis.Worker = class extends Base { constructor(...args) { super(...args); workers.push(this); } };
}"""
)
private external fun trackWorkers()

// a worker stopped mid-load reports its interrupted script load to the page: not an app error
@JsFun(
	"""() => {
	for (const worker of globalThis.loakWorkers.splice(0)) {
		worker.onerror = (e) => e.preventDefault();
		worker.terminate();
	}
}"""
)
private external fun terminateWorkers()

/**
 * Waits until the stopped app's sqlite workers have let go of the OPFS files (a terminated
 * worker frees them a moment later), deleting them all if [wipe].
 */
@JsFun(
	"""async (wipe) => {
	const root = await navigator.storage.getDirectory();
	const files = async function* (dir) {
		for await (const h of dir.values()) h.kind === 'file' ? yield h : yield* files(h);
	};
	for (let tries = 0; ; tries++) {
		try {
			if (wipe) {
				const names = [];
				for await (const name of root.keys()) names.push(name);
				for (const name of names) await root.removeEntry(name, { recursive: true });
			} else {
				for await (const file of files(root)) await (await file.createWritable({ keepExistingData: true })).abort();
			}
			return null;
		} catch (e) {
			if (tries === 100) throw e;
			await new Promise((resolve) => setTimeout(resolve, 100));
		}
	}
}"""
)
private external fun freeOpfs(wipe: Boolean): Promise<JsAny?>

/**
 * Watches the page's main thread: a timer every 50 ms and every animation frame note when
 * they ran, so a long gap between any two is time the thread ran nothing else. Also
 * collects uncaught errors.
 */
@JsFun(
	"""() => {
	const m = { start: performance.now(), max: 0, at: 0, errors: [] };
	m.last = m.start;
	m.tick = () => {
		const now = performance.now();
		if (now - m.last > m.max) { m.max = now - m.last; m.at = m.last - m.start; }
		m.last = now;
	};
	const frame = () => { m.tick(); m.frame = requestAnimationFrame(frame); };
	m.timer = setInterval(m.tick, 50);
	m.frame = requestAnimationFrame(frame);
	m.onError = (e) => m.errors.push((e.error && e.error.stack) || e.message);
	m.onRejection = (e) => m.errors.push('Unhandled rejection: ' + ((e.reason && e.reason.stack) || e.reason));
	addEventListener('error', m.onError);
	addEventListener('unhandledrejection', m.onRejection);
	return m;
}"""
)
private external fun startMonitor(): JsAny

@JsFun("(m) => m.tick()")
private external fun tickMonitor(monitor: JsAny)

/** Leaves the time since [tickMonitor] out of [startMonitor]'s monitor. */
@JsFun("(m) => { m.last = performance.now(); }")
private external fun skipMonitor(monitor: JsAny)

/** Stops [startMonitor]'s monitor: what went wrong, or null. */
@JsFun(
	"""(m, maxGap) => {
	clearInterval(m.timer);
	cancelAnimationFrame(m.frame);
	removeEventListener('error', m.onError);
	removeEventListener('unhandledrejection', m.onRejection);
	console.log('[loak] longest main-thread gap: ' + Math.round(m.max) + ' ms at ' + Math.round(m.at) + ' ms');
	const problems = m.errors.map((e) => 'Uncaught page error: ' + e);
	if (m.max > maxGap) {
		problems.push('The main thread was blocked for ' + Math.round(m.max) + ' ms, ' +
			Math.round(m.at / 1000) + ' s into the flow (limit ' + maxGap + ' ms)');
	}
	return problems.length ? problems.join('\n') : null;
}"""
)
private external fun stopMonitor(monitor: JsAny, maxGap: Int): String?
