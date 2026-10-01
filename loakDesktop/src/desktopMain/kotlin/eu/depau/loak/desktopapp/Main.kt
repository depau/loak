package eu.depau.loak.desktopapp

import androidx.compose.ui.graphics.vector.rememberVectorPainter
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.brand.Loak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import eu.depau.loak.App
import eu.depau.loak.di.initKoin

/**
 * Desktop (JVM) entry point. Boots Koin and hosts the shared Compose [App]
 * in a native resizable window.
 */
fun main() {
	initKoin()
	application {
		val state = rememberWindowState(width = 1400.dp, height = 900.dp)
		val icon = rememberVectorPainter(Icons.Brand.Loak)
		Window(
			onCloseRequest = ::exitApplication,
			title = "Lo'ak",
			state = state,
			icon = icon
		) {
			App()
		}
	}
}
