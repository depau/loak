package eu.depau.loak.ui.util

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollDispatcher
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.ChevronForward
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Hands a vertical mouse wheel on to the scrolling parent. Desktop Compose otherwise turns
 * it into a sideways scroll of a horizontal list, so a page full of carousels can't be
 * scrolled. Horizontal wheels and touchpad swipes still scroll the list.
 *
 * It goes through nested scrolling, so a collapsing top bar follows as with a touch drag.
 */
fun Modifier.verticalWheelToParent(): Modifier = composed {
	val dispatcher = remember { NestedScrollDispatcher() }
	val step = with(LocalDensity.current) { WHEEL_STEP.toPx() }
	this
		.nestedScroll(remember { object : NestedScrollConnection {} }, dispatcher)
		.pointerInput(Unit) {
			awaitPointerEventScope {
				while (true) {
					val event = awaitPointerEvent(PointerEventPass.Initial)
					if (event.type != PointerEventType.Scroll) continue
					val delta = event.changes.fold(Offset.Zero) { sum, it -> sum + it.scrollDelta }
					if (abs(delta.y) <= abs(delta.x)) continue
					event.changes.forEach { it.consume() }
					// a wheel turned down moves the content up, like a drag upwards
					val available = Offset(0f, -delta.y * step)
					// SideEffect, not UserInput: pull to refresh would take a wheel at the top as a pull
					val pre = dispatcher.dispatchPreScroll(available, NestedScrollSource.SideEffect)
					dispatcher.dispatchPostScroll(Offset.Zero, available - pre, NestedScrollSource.SideEffect)
				}
			}
		}
}

/**
 * Arrow buttons at the sides of a horizontal list, shown while a mouse hovers it (touch
 * never hovers, so phones don't get them).
 */
@Composable
fun HorizontalScrollArrows(
	canScrollBackward: Boolean,
	canScrollForward: Boolean,
	onBackward: suspend () -> Unit,
	onForward: suspend () -> Unit,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit
) {
	val interactionSource = remember { MutableInteractionSource() }
	val hovered by interactionSource.collectIsHoveredAsState()
	val scope = rememberCoroutineScope()

	Box(modifier.hoverable(interactionSource)) {
		content()
		AnimatedVisibility(
			visible = hovered && canScrollBackward,
			enter = fadeIn(),
			exit = fadeOut(),
			modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
		) {
			ArrowButton(back = true) { scope.launch { onBackward() } }
		}
		AnimatedVisibility(
			visible = hovered && canScrollForward,
			enter = fadeIn(),
			exit = fadeOut(),
			modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
		) {
			ArrowButton(back = false) { scope.launch { onForward() } }
		}
	}
}

@Composable
private fun ArrowButton(back: Boolean, onClick: () -> Unit) {
	FilledTonalIconButton(
		onClick = onClick,
		modifier = Modifier.size(40.dp).shadow(4.dp, shape = androidx.compose.foundation.shape.CircleShape)
	) {
		Icon(
			Icons.Outlined.ChevronForward,
			contentDescription = null,
			modifier = if (back) Modifier.rotate(180f) else Modifier
		)
	}
}

/** Scrolls a horizontal list by most of its width, [direction] -1 or 1. */
suspend fun LazyListState.pageBy(direction: Int) =
	animateScrollBy(direction * layoutInfo.viewportSize.width * PAGE_FRACTION)

/** Scrolls a horizontal grid by most of its width, [direction] -1 or 1. */
suspend fun LazyGridState.pageBy(direction: Int) =
	animateScrollBy(direction * layoutInfo.viewportSize.width * PAGE_FRACTION)

private const val PAGE_FRACTION = .85f

/** How far one notch of the mouse wheel scrolls. */
private val WHEEL_STEP = 64.dp
