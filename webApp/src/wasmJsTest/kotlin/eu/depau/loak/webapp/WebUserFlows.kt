package eu.depau.loak.webapp

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import eu.depau.loak.App
import eu.depau.loak.di.initKoin
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.uitest.UserFlows
import kotlinx.browser.localStorage
import kotlinx.coroutines.test.TestResult
import org.koin.core.context.stopKoin
import kotlin.time.Duration.Companion.minutes

/**
 * The user flows on the real web app in a real browser: the wasm build, its sqlite
 * worker and the browser's HTTP client, against the fixture server.
 */
@OptIn(ExperimentalTestApi::class)
class WebUserFlows : UserFlows() {
	override fun runApp(flow: suspend ComposeUiTest.() -> Unit): TestResult = runComposeUiTest(testTimeout = 5.minutes) {
		// logged out, default settings. ponytail: the library database stays (OPFS);
		// clear it too if a flow ever depends on an empty one
		localStorage.clear()
		// the previous flow's app stops here, not at its end: its composition is only
		// disposed after its test body returns, and that still needs Koin
		stopKoin()
		val koin = initKoin()
		koin.koin.get<MediaPlayerViewModel>().setVolume(0f) // flows play songs: keep them silent
		setContent { App() }
		flow()
	}
}
