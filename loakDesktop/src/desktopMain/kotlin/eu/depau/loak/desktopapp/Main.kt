package eu.depau.loak.desktopapp

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.nucleusApplication
import dev.nucleusframework.window.WindowControls
import dev.nucleusframework.window.WindowScaffold
import dev.nucleusframework.window.windowDragArea
import eu.depau.loak.App
import eu.depau.loak.di.initKoin
import org.jetbrains.skia.Image

/**
 * Desktop (JVM) entry point. Boots Koin and hosts the shared Compose [App] in a
 * Nucleus decorated window: no OS title bar, the app draws its own (client-side
 * decorations) with native-looking window controls.
 */
fun main(args: Array<String>) {
	initKoin()
	nucleusApplication(args) {
		DecoratedWindow(
			onCloseRequest = ::exitApplication,
			title = "Lo'ak",
			icon = remember { loadIcon() },
			state = rememberWindowState(width = 1400.dp, height = 900.dp),
		) {
			WindowScaffold(
				titleBar = {
					Row(
						modifier = Modifier.fillMaxWidth().height(40.dp).windowDragArea(),
						verticalAlignment = Alignment.CenterVertically,
					) {
						Spacer(Modifier.weight(1f))
						WindowControls()
					}
				}
			) {
				App()
			}
		}
	}
}

private fun loadIcon(): BitmapPainter? =
	Thread.currentThread().contextClassLoader?.getResourceAsStream("icons/loak.png")?.use {
		BitmapPainter(Image.makeFromEncoded(it.readAllBytes()).toComposeImageBitmap())
	}
