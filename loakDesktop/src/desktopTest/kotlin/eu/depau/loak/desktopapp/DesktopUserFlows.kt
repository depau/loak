@file:OptIn(ExperimentalTestApi::class)

package eu.depau.loak.desktopapp

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.lifecycle.ViewModelStore
import eu.depau.loak.App
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.data.database.DownloadDatabase
import eu.depau.loak.di.desktopDataDir
import eu.depau.loak.di.initKoin
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.uitest.UserFlows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.koin.core.context.stopKoin
import org.junit.rules.TestName
import org.junit.rules.Timeout
import java.io.File
import javax.imageio.ImageIO

/** The user flows on the desktop app, at a window size of [width]×[height]. */
abstract class DesktopUserFlows(private val width: Int, private val height: Int) : UserFlows() {
	@get:Rule
	val name = TestName()

	// a hung event thread blocks the test API itself, so fail from outside it
	@get:Rule
	val timeout: Timeout = Timeout.seconds(180)

	override fun runApp(flow: ComposeUiTest.() -> Unit) {
		// Nucleus registers its own Main dispatcher (the Tao main thread, or a fallback
		// thread outside a Tao window); the test drives Compose on the EDT, so Main must be
		// it, from before the app starts until the test has disposed the composition
		Dispatchers.setMain(Dispatchers.Swing)
		val koin = initKoin()
		val player = koin.koin.get<MediaPlayerViewModel>()
		player.setVolume(0f) // flows play songs: keep them silent
		try {
			runDesktopComposeUiTest(width, height) {
				setContent { App() }
				try {
					flow()
				} catch (e: AssertionError) {
					// only when the app is alive: after a crash, capturing hangs the test
					screenshot()
					throw e
				}
			}
		} finally {
			// the player is a Koin singleton, never cleared outside tests: stop its
			// coroutines before the databases they use close under them
			ViewModelStore().apply { put("player", player); clear() }
			runCatching { koin.koin.get<CacheDatabase>().close() }
			runCatching { koin.koin.get<DownloadDatabase>().close() }
			stopKoin()
			Dispatchers.resetMain()
			// a fresh install for the next flow
			desktopDataDir.listFiles()?.forEach { it.deleteRecursively() }
		}
	}

	private fun ComposeUiTest.screenshot() = runCatching {
		val dir = File(System.getProperty("loak.uitest.failures") ?: "build/ui-test-failures").apply { mkdirs() }
		val file = File(dir, "${this@DesktopUserFlows::class.simpleName}.${name.methodName}.png")
		ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
		println("Screenshot of the failure: $file")
	}
}

class DesktopUserFlowsWide : DesktopUserFlows(1280, 800)

class DesktopUserFlowsPhone : DesktopUserFlows(412, 892)
