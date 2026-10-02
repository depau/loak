package eu.depau.loak.ui.screens.login

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.depau.loak.ui.screens.login.pages.LoginScreenContent
import eu.depau.loak.ui.util.LocalWindowChrome
import eu.depau.loak.ui.util.windowDragArea

@Composable
fun LoginScreen() {
	Scaffold(
		topBar = {
			// no app bar here, so an empty title-bar band moves the window (desktop only)
			LocalWindowChrome.current?.let {
				Spacer(Modifier.fillMaxWidth().height(it.barHeight).windowDragArea())
			}
		}
	) { innerPadding ->
		LoginScreenContent(
			innerPadding = innerPadding
		)
	}
}
