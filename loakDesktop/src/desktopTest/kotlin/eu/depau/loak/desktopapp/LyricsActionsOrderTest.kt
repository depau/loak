package eu.depau.loak.desktopapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import eu.depau.loak.domain.models.lyrics.LyricsLine
import eu.depau.loak.domain.models.lyrics.LyricsResult
import eu.depau.loak.ui.screens.lyrics.components.LyricsActions
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test

/**
 * CF-08 at the UI layer: the lyrics action buttons render in the agreed order —
 * provider picker, sync, share, full screen. The order regressed before (restored in late
 * 2026), so the row's own geometry is locked by a test, not by memory.
 */
@OptIn(ExperimentalTestApi::class)
class LyricsActionsOrderTest {

	@Test
	fun rendersProviderSyncShareFullScreenInThatLeftToRightOrder() = runComposeUiTest {
		setContent {
			MaterialTheme {
				LyricsActions(
					providerResults = listOf(lyrics("Source A"), lyrics("Source B")),
					selectedProviderIndex = 0,
					isSelecting = false,
					selectedIndicesSize = 0,
					providerMenuOpen = false,
					onProviderMenuOpenChange = {},
					onSelectProvider = {},
					onSync = {},
					onToggleSelecting = {},
					onShare = {},
					onFullScreen = {}
				)
			}
		}

		// the four buttons are the row's contract: provider, sync, share, full screen
		val provider = onNodeWithContentDescription("Change lyrics source")
		val sync = onNodeWithContentDescription("Adjust lyrics sync")
		val share = onNodeWithContentDescription("Share lyrics")
		val fullScreen = onNodeWithContentDescription("Full screen")

		provider.assertIsDisplayed()
		sync.assertIsDisplayed()
		share.assertIsDisplayed()
		fullScreen.assertIsDisplayed()

		// each later button sits strictly to the right of the previous one (same row, no wrap)
		assertRightOf(sync, provider)
		assertRightOf(share, sync)
		assertRightOf(fullScreen, share)
	}

	private fun lyrics(name: String) = LyricsResult(
		lines = listOf(
			LyricsLine(text = "line one"),
			LyricsLine(time = 1.seconds, text = "line two")
		),
		providerName = name
	)
}

/** [later]'s left edge must be beyond [earlier]'s right edge (same row, no wrap). */
private fun assertRightOf(
	later: androidx.compose.ui.test.SemanticsNodeInteraction,
	earlier: androidx.compose.ui.test.SemanticsNodeInteraction
) {
	val e = earlier.fetchSemanticsNode()
	val l = later.fetchSemanticsNode()
	check(l.boundsInRoot.left >= e.boundsInRoot.right) {
		"Button later than expected — left edge ${l.boundsInRoot.left} < previous right edge ${e.boundsInRoot.right}"
	}
}
