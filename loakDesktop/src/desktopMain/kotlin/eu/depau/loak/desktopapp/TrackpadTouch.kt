@file:OptIn(InternalComposeUiApi::class)

package eu.depau.loak.desktopapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.ComposeScenePointer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import dev.nucleusframework.window.tao.TaoWindow
import eu.depau.loak.util.Logger
import kotlin.math.abs

/**
 * Two-finger trackpad swipes as a finger on a touch screen, put down where the mouse pointer
 * is: whatever a touch drag does there, a swipe does too, following the fingers (lists and
 * pagers scroll and fling, song rows swipe, sheets and the player drag). A swipe towards the
 * start edge (to the right in left-to-right layouts) that nothing under the pointer takes is
 * back, which the screens animate as it goes, like the back gesture on phones.
 *
 * The touch is put down once the fingers have moved a touch slop, so resting fingers or a
 * nudge never tap, and it's lifted with the fingers: the OS's coasting after that is dropped,
 * the touch's own fling replaces it. A touch nothing moved with is cancelled, not lifted, so a
 * swipe over a button doesn't click it. Fingers coming down first put a touch down and cancel
 * it at once, in an event of its own: that stops what still coasts under the pointer, as a
 * finger would, and clears what the scene kept of earlier input. Without it, on macOS the first
 * swipe on another list after the mouse moved there was lost.
 */
internal class TrackpadTouch(
	private val scene: () -> ComposeScene?,
	/** Where the mouse pointer is, in the scene's coordinates. */
	private val pointer: () -> Offset,
	private val back: DirectNavigationEventInput,
	private val touchSlop: Float,
	/** 1 when back is a swipe to the right (left-to-right layouts), -1 when it's to the left. */
	private val backDirection: Float,
	/** Pixels per second past which a back swipe goes back, however short. */
	private val backFlingVelocity: Float,
) {
	private enum class State { Idle, Waiting, Touching, Back }

	private var state = State.Idle
	private var target: ComposeScene? = null
	private var origin = Offset.Zero
	private var moved = Offset.Zero
	/** Something under the touch moved with it: a list scrolled, a row swiped. */
	private var claimed = false
	private val velocity = VelocityTracker()

	/**
	 * A trackpad swipe's phases: [MayBegin] is the fingers coming down before they move, [Other]
	 * the coasting after a swipe.
	 */
	enum class Phase { MayBegin, Began, Changed, Ended, Cancelled, Other }

	/** Takes a scroll event; false when it isn't part of a trackpad swipe (a mouse wheel). */
	fun onScroll(delta: Offset, phase: Phase?): Boolean {
		if (phase == null) return false
		val scene = scene() ?: return false
		when (phase) {
			Phase.MayBegin -> if (state == State.Idle) stopCoasting(scene, pointer())
			Phase.Began -> {
				end(commit = false)
				start(scene)
				moved = delta
			}
			Phase.Changed -> if (state == State.Idle) {
				start(scene)
				moved = delta
			} else {
				move(delta)
			}
			Phase.Ended -> end(commit = true)
			Phase.Cancelled -> end(commit = false)
			Phase.Other -> {}
		}
		return true
	}

	private fun start(scene: ComposeScene) {
		target = scene
		origin = pointer()
		moved = Offset.Zero
		claimed = false
		velocity.resetTracking()
		state = State.Waiting
		stopCoasting(scene, origin)
	}

	/** A touch down and cancelled at once: whatever flings there stops, nothing is tapped. */
	private fun stopCoasting(scene: ComposeScene, position: Offset) {
		scene.sendPointerEvent(
			PointerEventType.Press,
			listOf(ComposeScenePointer(TouchId, position, pressed = true, PointerType.Touch))
		)
		scene.cancelPointerInput()
	}

	private fun move(delta: Offset) {
		moved += delta
		val position = origin + moved
		velocity.addPosition(System.nanoTime() / 1_000_000, position)
		when (state) {
			State.Waiting -> if (moved.getDistance() > touchSlop) {
				touch(PointerEventType.Press, origin)
				state = State.Touching
				drag(position)
			}
			State.Touching -> drag(position)
			State.Back -> back.backProgressed(navigationEvent(position))
			State.Idle -> {}
		}
	}

	private fun drag(position: Offset) {
		touch(PointerEventType.Move, position)
		// past twice the slop, whatever drags that way under the pointer has taken it
		val towardsBack = moved.x * backDirection > abs(moved.y)
		if (!claimed && towardsBack && moved.getDistance() > 2 * touchSlop) {
			target?.cancelPointerInput()
			state = State.Back
			back.backStarted(navigationEvent(position))
		}
	}

	private fun end(commit: Boolean) {
		when (state) {
			State.Touching ->
				if (commit && claimed) touch(PointerEventType.Release, origin + moved)
				else target?.cancelPointerInput()
			State.Back -> {
				val flung = velocity.calculateVelocity().x * backDirection >= backFlingVelocity
				if (commit && (progress() >= BACK_PROGRESS || flung)) back.backCompleted()
				else back.backCancelled()
			}
			State.Waiting, State.Idle -> {}
		}
		state = State.Idle
		target = null
	}

	private fun touch(type: PointerEventType, position: Offset) {
		val pressed = type != PointerEventType.Release
		val result = target?.sendPointerEvent(
			type,
			listOf(ComposeScenePointer(TouchId, position, pressed, PointerType.Touch))
		) ?: return
		@Suppress("INVISIBLE_REFERENCE")
		if (type == PointerEventType.Move && result.anyMovementConsumed) claimed = true
	}

	private fun progress(): Float {
		val width = target?.size?.width?.takeIf { it > 0 } ?: return 0f
		return (moved.x * backDirection / width).coerceIn(0f, 1f)
	}

	private fun navigationEvent(position: Offset) = NavigationEvent(
		touchX = position.x,
		touchY = position.y,
		progress = progress(),
		swipeEdge = if (backDirection > 0) NavigationEvent.EDGE_LEFT else NavigationEvent.EDGE_RIGHT,
	)

	private companion object {
		/** Apart from the mouse (pointer 0) and the touch screens' own ids. */
		val TouchId = PointerId(Long.MAX_VALUE)
		/** How far across the window a slow back swipe has to go. */
		const val BACK_PROGRESS = .3f
	}
}

/** Routes [window]'s trackpad swipes through [TrackpadTouch] while composed. */
@Composable
internal fun TrackpadTouchEffect(window: TaoWindow, back: DirectNavigationEventInput) {
	val touchSlop = LocalViewConfiguration.current.touchSlop
	val backDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
	val backFlingVelocity = with(LocalDensity.current) { 1000.dp.toPx() }
	DisposableEffect(window, back, touchSlop, backDirection, backFlingVelocity) {
		val undo = window.routeTrackpad { scene, pointer ->
			TrackpadTouch(scene, pointer, back, touchSlop, backDirection, backFlingVelocity)
		}
		onDispose { undo?.invoke() }
	}
}

/**
 * Puts [TrackpadTouch] in front of the window's scroll events; Nucleus gets the rest. Its
 * scene host is internal, so it's reached by reflection: the scroll listener Nucleus gives the
 * window is a lambda over it. Returns the undo, or null when that moved in a Nucleus update:
 * the swipes then scroll as Nucleus sends them, and the error reaches Sentry.
 */
internal fun TaoWindow.routeTrackpad(
	create: (scene: () -> ComposeScene?, pointer: () -> Offset) -> TrackpadTouch
): (() -> Unit)? = try {
	val listener = TaoWindow::class.java.getDeclaredField("pointerScrollListener")
		.apply { isAccessible = true }
	@Suppress("UNCHECKED_CAST")
	val nucleus = listener.get(this) as (Any) -> Unit
	val host = nucleus.javaClass.declaredFields.firstNotNullOf { field ->
		field.isAccessible = true
		field.get(nucleus)?.takeIf { it.javaClass.hasField("sceneBundle") }
	}
	val bundle = host.javaClass.field("sceneBundle")
	val scale = host.javaClass.field("scale")
	val deadband = host.javaClass.field("pointerDeadband").get(host)
	val x = deadband.javaClass.getMethod("getX")
	val y = deadband.javaClass.getMethod("getY")
	val touch = create(
		{ bundle.get(host)?.let { it.javaClass.getMethod("getScene").invoke(it) as ComposeScene } },
		{ Offset(x.invoke(deadband) as Float, y.invoke(deadband) as Float) }
	)
	listener.set(this, { event: Any ->
		if (!touch.onScroll(event, scale.getFloat(host))) nucleus(event)
	})
	val undo = { listener.set(this, nucleus) }
	undo
} catch (e: Exception) {
	Logger.e("TrackpadTouch", "Nucleus' scroll routing moved: no trackpad swipes", e)
	null
}

/**
 * Takes one of Nucleus' scroll events, whose class is internal. They're shaped like AWT's
 * wheel: positive scrolls down or right, which moves the content (and so a finger dragging
 * it) the other way, in units of 10dp, as Nucleus' own pans count them. [scale]: the display's.
 */
@Suppress("INVISIBLE_REFERENCE")
private fun TrackpadTouch.onScroll(event: Any, scale: Float): Boolean {
	event as dev.nucleusframework.window.tao.TaoPointerScrollEvent
	val phase = when (event.gesturePhase) {
		null -> null
		dev.nucleusframework.window.tao.TaoScrollGesturePhase.MAY_BEGIN -> TrackpadTouch.Phase.MayBegin
		dev.nucleusframework.window.tao.TaoScrollGesturePhase.BEGAN -> TrackpadTouch.Phase.Began
		dev.nucleusframework.window.tao.TaoScrollGesturePhase.CHANGED -> TrackpadTouch.Phase.Changed
		dev.nucleusframework.window.tao.TaoScrollGesturePhase.ENDED -> TrackpadTouch.Phase.Ended
		dev.nucleusframework.window.tao.TaoScrollGesturePhase.CANCELLED -> TrackpadTouch.Phase.Cancelled
		else -> TrackpadTouch.Phase.Other
	}
	return onScroll(Offset(-event.dxAwt, -event.dyAwt) * (10f * scale), phase)
}

private fun Class<*>.hasField(name: String) = declaredFields.any { it.name == name }

private fun Class<*>.field(name: String) = getDeclaredField(name).apply { isAccessible = true }
