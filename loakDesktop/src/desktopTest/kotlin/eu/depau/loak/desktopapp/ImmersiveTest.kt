package eu.depau.loak.desktopapp

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import eu.depau.loak.ui.util.Immersive
import eu.depau.loak.ui.util.LocalWindowChrome
import eu.depau.loak.ui.util.WindowChrome
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ImmersiveTest {

	@Test
	fun leavingNativeFullscreenExitsImmersiveContent() = runComposeUiTest {
		var fullscreen by mutableStateOf(false)
		val fullscreenRequests = mutableListOf<Boolean>()
		val setFullscreen: (Boolean) -> Unit = fullscreenRequests::add
		var exits = 0

		setContent {
			CompositionLocalProvider(
				LocalWindowChrome provides WindowChrome(
					barHeight = 0.dp,
					dragArea = Modifier,
					leftControls = null,
					rightControls = null,
					fullscreen = fullscreen,
					setFullscreen = setFullscreen,
				)
			) {
				Immersive { exits++ }
			}
		}

		waitForIdle()
		assertEquals(listOf(true), fullscreenRequests)
		assertEquals(0, exits)

		fullscreen = true
		waitForIdle()
		fullscreen = false
		waitForIdle()

		assertEquals(1, exits)
	}

	/** A window the user put in full screen stays there, and gets its controls back, after karaoke. */
	@Test
	fun karaokeInAUserFullscreenWindowOnlyMarksItselfImmersiveWhileShown() = runComposeUiTest {
		val fullscreenRequests = mutableListOf<Boolean>()
		val immersive = mutableListOf<Boolean>()
		var shown by mutableStateOf(true)

		setContent {
			CompositionLocalProvider(
				LocalWindowChrome provides WindowChrome(
					barHeight = 0.dp,
					dragArea = Modifier,
					leftControls = null,
					rightControls = null,
					fullscreen = true,
					setFullscreen = { fullscreenRequests += it },
					setImmersive = { immersive += it },
				)
			) {
				if (shown) Immersive {}
			}
		}

		waitForIdle()
		assertEquals(listOf(true), immersive)
		shown = false
		waitForIdle()

		assertEquals(listOf(true, false), immersive)
		assertEquals(emptyList(), fullscreenRequests)
	}
}
