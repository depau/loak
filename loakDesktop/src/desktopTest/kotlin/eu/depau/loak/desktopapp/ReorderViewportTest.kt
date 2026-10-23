package eu.depau.loak.desktopapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.ui.util.dragHandle
import eu.depau.loak.ui.util.draggableItemsIndexed
import eu.depau.loak.ui.util.rememberDraggableListState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
class ReorderViewportTest {
	@Test
	fun movingFirstVisibleRowKeepsViewportAtTheSameSlot() = runDesktopComposeUiTest(400, 500) {
		val items = mutableStateListOf(*(0 until 20).toList().toTypedArray())
		val listState = LazyListState()
		setContent {
			val reorder = rememberDraggableListState(listState) { from, to ->
				items.add(to, items.removeAt(from))
			}
			CompositionLocalProvider(LocalPlatformContext provides DesktopTestContext) {
				LazyColumn(state = listState) {
					draggableItemsIndexed(
						state = reorder,
						items = items,
						key = { _, item -> item }
					) { _, item, _ ->
						Box(
							Modifier
								.fillMaxWidth()
								.height(80.dp)
								.testTag("row-$item")
								.dragHandle(reorder, key = item)
						)
					}
				}
			}
		}

		onNodeWithTag("row-0").performTouchInput {
			swipe(center, center.copy(y = center.y + 240f), durationMillis = 1_000)
		}
		waitForIdle()

		runOnIdle {
			assertEquals(0, listState.firstVisibleItemIndex)
			assertEquals(0, listState.firstVisibleItemScrollOffset)
			assertEquals(0, items[3])
		}
	}

	private object DesktopTestContext : PlatformContext {
		override val name = "desktop-test"
		override val appVersion = "test"
		override val buildVariant = "test"
		override val sizeClass = WindowSizeClass.calculateFromSize(DpSize(400.dp, 500.dp))
		override val platformType = PlatformType.Desktop

		@Composable
		override fun systemColorScheme(isDark: Boolean) = null
	}
}
