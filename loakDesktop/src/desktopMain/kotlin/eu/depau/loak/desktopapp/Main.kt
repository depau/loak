package eu.depau.loak.desktopapp

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import eu.depau.loak.App
import eu.depau.loak.di.initKoin
import javax.imageio.ImageIO

/**
 * Desktop (JVM) entry point. Boots Koin and hosts the shared Compose [App]
 * in a native resizable window.
 */
fun main() {
	initKoin()
	application {
		val state = rememberWindowState(width = 1400.dp, height = 900.dp)
		Window(
			onCloseRequest = ::exitApplication,
			title = "Lo'ak",
			state = state
		) {
			LaunchedEffect(Unit) {
				runCatching {
					val stream = Thread.currentThread().contextClassLoader
						?.getResourceAsStream("icons/loak.png")
					if (stream != null) {
						window.iconImage = ImageIO.read(stream)
					}
				}
			}
			App()
		}
	}
}
