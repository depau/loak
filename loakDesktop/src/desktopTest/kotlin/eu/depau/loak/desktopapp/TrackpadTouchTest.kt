package eu.depau.loak.desktopapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.depau.loak.desktopapp.TrackpadTouch.Phase.Began
import eu.depau.loak.desktopapp.TrackpadTouch.Phase.Changed
import eu.depau.loak.desktopapp.TrackpadTouch.Phase.Ended
import eu.depau.loak.desktopapp.TrackpadTouch.Phase.Other
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Trackpad swipes act as a touch at the pointer, and one towards the start edge is back. */
@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
class TrackpadTouchTest {
	@Test
	fun swipeDragsWhatATouchDrags() = runDesktopComposeUiTest(800, 600) {
		var dragged = 0f
		setContent {
			Box(Modifier.fillMaxSize().draggable(rememberDraggableState { dragged += it }, Orientation.Horizontal))
		}
		val touch = trackpad()
		swipe(touch, listOf(Offset(-200f, 0f)))
		// past the slop, the drag follows the fingers; the coasting after them is dropped
		runOnUiThread { touch.onScroll(Offset(-500f, 0f), Other) }
		waitForIdle()
		assertTrue(dragged in -200f..-150f, "dragged $dragged")
	}

	@Test
	fun swipeOverAButtonDoesNotClickIt() = runDesktopComposeUiTest(800, 600) {
		var clicks = 0
		setContent { Box(Modifier.fillMaxSize().clickable { clicks++ }) }
		val touch = trackpad()
		swipe(touch, listOf(Offset(0f, 150f)))
		swipe(touch, listOf(Offset(5f, 0f)))
		waitForIdle()
		assertEquals(0, clicks)
	}

	@Test
	fun verticalSwipeScrollsAList() = runDesktopComposeUiTest(800, 600) {
		val list = LazyListState()
		setContent {
			LazyColumn(Modifier.fillMaxSize(), list) {
				items(100) { Text("$it", Modifier.height(50.dp)) }
			}
		}
		swipe(trackpad(), listOf(Offset(0f, -300f)))
		waitForIdle()
		assertTrue(list.firstVisibleItemIndex > 0)
	}

	@Test
	fun swipeTowardsTheStartGoesBackFollowingTheFingers() = runDesktopComposeUiTest(800, 600) {
		val back = BackRecorder()
		setContent { back.Content() }
		val touch = trackpad(back.input)
		swipe(touch, listOf(Offset(400f, 0f)), end = false)
		waitForIdle()
		val progress = (back.state.transitionState as NavigationEventTransitionState.InProgress)
			.latestEvent.progress
		assertEquals(.5f, progress, .05f)
		runOnUiThread { touch.onScroll(Offset.Zero, Ended) }
		waitForIdle()
		assertEquals(1, back.completed)

		// let go before a third of the window, the swipe coming back: back is cancelled
		swipe(touch, listOf(Offset(300f, 0f), Offset(-250f, 0f)))
		waitForIdle()
		assertEquals(1, back.completed)
		assertEquals(1, back.cancelled)
	}

	@Test
	fun swipeTakenByADragIsNotBack() = runDesktopComposeUiTest(800, 600) {
		val back = BackRecorder()
		setContent {
			back.Content()
			Box(Modifier.fillMaxSize().draggable(rememberDraggableState {}, Orientation.Horizontal))
		}
		swipe(trackpad(back.input), listOf(Offset(500f, 0f)))
		waitForIdle()
		assertEquals(0, back.completed)
	}

	private class BackRecorder {
		val input = DirectNavigationEventInput()
		lateinit var state: NavigationEventState<NavigationEventInfo.None>
		var completed = 0
		var cancelled = 0

		@androidx.compose.runtime.Composable
		fun Content() {
			val owner = rememberNavigationEventDispatcherOwner(parent = null)
			DisposableEffect(owner) {
				owner.navigationEventDispatcher.addInput(input)
				onDispose { owner.navigationEventDispatcher.removeInput(input) }
			}
			CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
				state = rememberNavigationEventState(NavigationEventInfo.None)
				NavigationBackHandler(
					state = state,
					isBackEnabled = true,
					onBackCompleted = { completed++ },
					onBackCancelled = { cancelled++ }
				)
			}
		}
	}
}

	@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
	internal fun ComposeUiTest.trackpad(
		back: DirectNavigationEventInput = DirectNavigationEventInput(),
		pointer: Offset = Offset(200f, 300f)
	) = TrackpadTouch(
			scene = { (this as SkikoComposeUiTest).scene },
			pointer = { pointer },
			back = back,
			touchSlop = 18f,
			backDirection = 1f,
			backFlingVelocity = 1000f,
		)

	/**
	 * Fingers down, each of [moves] in ten steps, then lifted unless not [end]: one event a
	 * frame, as the OS sends them, so what they set going runs in between.
	 */
	@OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
	internal fun ComposeUiTest.swipe(touch: TrackpadTouch, moves: List<Offset>, end: Boolean = true) {
		fun send(delta: Offset, phase: TrackpadTouch.Phase) {
			runOnUiThread { touch.onScroll(delta, phase) }
			mainClock.advanceTimeByFrame()
		}
		send(Offset.Zero, Began)
		for (move in moves) repeat(10) { send(move / 10f, Changed) }
		if (end) send(Offset.Zero, Ended)
	}
