/*
 * Copyright 2024 Allan Veloso Lopes
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Temporarily implementation until this issue is made available on the official library
// https://issuetracker.google.com/issues/356619939
// Based on https://cs.android.com/androidx/platform/frameworks/support/+/androidx-main:compose/foundation/foundation/integration-tests/foundation-demos/src/main/java/androidx/compose/foundation/demos/LazyColumnDragAndDropDemo.kt
// https://issuetracker.google.com/issues/181282427

package eu.depau.loak.ui.util

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Creates a [DraggableListState] that is remembered across compositions.
 *
 * @param lazyListState the [LazyListState] whose items are being dragged.
 * @param canMove whether the item at the first index may move to the second one.
 * @param onMove the callback that is triggered when an item is moved.
 */
@Composable
fun rememberDraggableListState(
	lazyListState: LazyListState = rememberLazyListState(),
	canMove: (Int, Int) -> Boolean = { _, _ -> true },
	onMove: (Int, Int) -> Unit
): DraggableListState {
	val scope = rememberCoroutineScope()
	val state = remember(lazyListState) {
		DraggableListState(
			listState = lazyListState,
			canMove = canMove,
			onMove = onMove,
			scope = scope
		)
	}
	// Edge autoscroll runs per frame, not per pointer event: a mouse sends events at its own
	// rate (or none while held still), and can be dragged far past the list's edge.
	LaunchedEffect(state) {
		snapshotFlow { state.draggingItemIndex != null }.collectLatest { dragging ->
			if (!dragging) return@collectLatest
			var last = withFrameNanos { it }
			while (true) {
				val now = withFrameNanos { it }
				val step = state.autoScrollStep((now - last) / 1e9f)
				last = now
				if (step != 0f) {
					lazyListState.scrollBy(step)
					state.onDrag(Offset.Zero)
				}
			}
		}
	}
	return state
}

/**
 * A state object that can be hoisted to control and observe dragging within a list.
 *
 * In most cases, this will be created via [rememberLazyListState].
 *
 * @param listState the state of the list where the item is being dragged.
 * @param scope a coroutine scope for performing animations.
 * @param canMove whether the item at the first index may move to the second one.
 * @param onMove the callback that is triggered when an item is moved. The callback is
 * invoked whenever the dragged item middle point passes another item top or bottom offset,
 * it does not depend on the drag to be completed.
 */
class DraggableListState internal constructor(
	val listState: LazyListState,
	private val scope: CoroutineScope,
	private val canMove: (Int, Int) -> Boolean,
	private val onMove: (Int, Int) -> Unit
) {
	var draggingItemIndex by mutableStateOf<Int?>(null)
		private set

	/**
	 * Whether a [dragHandle] is held, dragging or not: a row that's also long-clickable
	 * should ignore long-presses on its handle.
	 */
	var isHandlePressed by mutableStateOf(false)
		internal set

	/** Found by key: until the list lays out a move, [draggingItemIndex] is another item's. */
	private var draggingItemKey: Any? = null

	private var draggingItemDraggedDelta by mutableFloatStateOf(0f)

	private var draggingItemInitialOffset by mutableIntStateOf(0)

	/** Where the pointer holds the dragged item's top, even past the list's edges. */
	private val draggingItemPointerOffset: Float
		get() = draggingItemInitialOffset + draggingItemDraggedDelta

	/**
	 * Kept inside the viewport: a pointer dragged past the list's edge (easy with a mouse)
	 * leaves the item at the edge, where autoscroll keeps moving it along.
	 */
	val draggingItemOffset: Float
		get() = draggingItemLayoutInfo?.let { item ->
			val info = listState.layoutInfo
			draggingItemPointerOffset
				.coerceAtMost((info.viewportEndOffset - item.size).toFloat())
				.coerceAtLeast(info.viewportStartOffset.toFloat()) - item.offset
		} ?: 0f

	private val draggingItemLayoutInfo: LazyListItemInfo?
		get() = draggingItemKey?.let { key ->
			listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
		}

	var previousIndexOfDraggedItem by mutableStateOf<Int?>(null)
		private set

	var previousItemOffset = Animatable(0f)
		private set

	internal fun onDragStart(index: Int) = onDragStart { it.index == index }

	internal fun onDragStart(key: Any) = onDragStart { it.key == key }

	private fun onDragStart(predicate: (LazyListItemInfo) -> Boolean) {
		listState.layoutInfo.visibleItemsInfo.find(predicate)?.also {
			draggingItemIndex = it.index
			draggingItemKey = it.key
			draggingItemInitialOffset = it.offset
		}
	}

	internal fun onDragInterrupted() {
		if (draggingItemIndex != null) {
			previousIndexOfDraggedItem = draggingItemIndex
			val startOffset = draggingItemOffset
			scope.launch {
				previousItemOffset.snapTo(startOffset)
				previousItemOffset.animateTo(
					0f,
					spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 1f)
				)
				previousIndexOfDraggedItem = null
			}
		}
		draggingItemDraggedDelta = 0f
		draggingItemIndex = null
		draggingItemKey = null
		draggingItemInitialOffset = 0
	}

	internal fun onDrag(offset: Offset) {
		draggingItemDraggedDelta += offset.y

		// off-screen (autoscroll brings it back), or its last move isn't laid out yet
		val draggingItem = draggingItemLayoutInfo
			?.takeIf { it.index == draggingItemIndex } ?: return
		val startOffset = draggingItem.offset + draggingItemOffset
		val endOffset = startOffset + draggingItem.size
		val middleOffset = startOffset + (endOffset - startOffset) / 2f

		val targetItem = listState.layoutInfo.visibleItemsInfo.find { item ->
			middleOffset.toInt() in item.offset..item.offsetEnd &&
				draggingItem.index != item.index
		}?.takeIf { canMove(draggingItem.index, it.index) }

		if (targetItem != null) {
			if (
				draggingItem.index == listState.firstVisibleItemIndex ||
				targetItem.index == listState.firstVisibleItemIndex
			) {
				listState.requestScrollToItem(
					listState.firstVisibleItemIndex,
					listState.firstVisibleItemScrollOffset
				)
			}
			onMove(draggingItem.index, targetItem.index)
			draggingItemIndex = targetItem.index
		}
	}

	/**
	 * How far to scroll in [seconds] while the pointer holds the dragged item past the
	 * viewport: [AUTO_SCROLL_ITEMS_PER_SECOND] items per second per item out, up to 1.5 items.
	 */
	internal fun autoScrollStep(seconds: Float): Float {
		val info = listState.layoutInfo
		val item = draggingItemLayoutInfo ?: run {
			// a move next to the first visible item can leave its slot just out of view:
			// scroll back to it rather than lose the drag
			val index = draggingItemIndex ?: return 0f
			val size = info.visibleItemsInfo.firstOrNull()?.size ?: return 0f
			val step = size * AUTO_SCROLL_ITEMS_PER_SECOND * seconds
			return if (index < listState.firstVisibleItemIndex) -step else step
		}
		val start = draggingItemPointerOffset
		val end = start + item.size
		val overscroll = when {
			draggingItemDraggedDelta > 0 -> (end - info.viewportEndOffset).coerceAtLeast(0f)
			draggingItemDraggedDelta < 0 -> (start - info.viewportStartOffset).coerceAtMost(0f)
			else -> 0f
		}
		val size = item.size.toFloat()
		val step = overscroll.coerceIn(-1.5f * size, 1.5f * size) *
			AUTO_SCROLL_ITEMS_PER_SECOND * seconds
		// never past the item's own slot: when it can't move on (canMove), the drag would be lost
		return step.coerceIn(
			(item.offsetEnd - info.viewportEndOffset).coerceAtMost(0).toFloat(),
			(item.offset - info.viewportStartOffset).coerceAtLeast(0).toFloat()
		)
	}

	private val LazyListItemInfo.offsetEnd: Int
		get() = this.offset + this.size
}

private const val AUTO_SCROLL_ITEMS_PER_SECOND = 3f

/**
 * Defines the area that will act as the handle for dragging operations.
 *
 * @param state The draggable state of the list where the handler is being used.
 * @param index The index of the item for which the handler is being used.
 * @param onlyAfterLongPress Indicates whether dragging should begin only after a long press.
 */
fun Modifier.dragHandle(
	state: DraggableListState,
	index: Int,
	onlyAfterLongPress: Boolean = false
): Modifier = dragHandle(state, onlyAfterLongPress) { state.onDragStart(index = index) }

/**
 * Defines the area that will act as the handle for dragging operations.
 *
 * If you use this version of the [dragHandle], you must implement the key parameter on the
 * [draggableItems] or [draggableItemsIndexed].
 *
 * @param state The draggable state of the list where the handler is being used.
 * @param key The unique identifier for the item within the list for which the handler is being used.
 * @param onlyAfterLongPress Indicates whether dragging should begin only after a long press.
 */
fun Modifier.dragHandle(
	state: DraggableListState,
	key: Any,
	onlyAfterLongPress: Boolean = false
): Modifier = dragHandle(state, onlyAfterLongPress) { state.onDragStart(key = key) }

private fun Modifier.dragHandle(
	state: DraggableListState,
	onlyAfterLongPress: Boolean,
	onDragStart: () -> Unit
): Modifier = this
	.pointerInput(state) {
		awaitEachGesture {
			awaitFirstDown(requireUnconsumed = false)
			state.isHandlePressed = true
			try {
				while (currentEvent.changes.any { it.pressed }) awaitPointerEvent()
			} finally {
				state.isHandlePressed = false
			}
		}
	}
	.pointerInput(state) {
		val onDrag = { change: PointerInputChange, offset: Offset ->
			change.consume()
			state.onDrag(offset = offset)
		}
		if (onlyAfterLongPress) {
			detectDragGesturesAfterLongPress(
				onDrag = onDrag,
				onDragStart = { onDragStart() },
				onDragEnd = state::onDragInterrupted,
				onDragCancel = state::onDragInterrupted
			)
		} else {
			detectDragGestures(
				onDrag = onDrag,
				onDragStart = { onDragStart() },
				onDragEnd = state::onDragInterrupted,
				onDragCancel = state::onDragInterrupted
			)
		}
	}

/**
 * Adds a list of draggable items.
 *
 * To the items to be draggable, it's also necessary to add the modifier [dragHandle] to
 * the preferable UI element of the item which will be the target of the drag operation.
 *
 * @param items the data list
 * @param key a factory of stable and unique keys representing the item. Using the same key
 * for multiple items in the list is not allowed. Type of the key should be saveable
 * via Bundle on Android. If null is passed the position in the list will represent the key.
 * When you specify the key the scroll position will be maintained based on the key, which
 * means if you add/remove items before the current visible item the item with the given key
 * will be kept as the first visible one. This can be overridden by calling 'requestScrollToItem'
 * on the 'LazyListState'.
 * @param contentType a factory of the content types for the item. The item compositions of
 * the same type could be reused more efficiently. Note that null is a valid type and items of such
 * type will be considered compatible.
 * @param itemContent the content displayed by a single item
 */
inline fun <T> LazyListScope.draggableItemsIndexed(
	state: DraggableListState,
	items: List<T>,
	noinline key: ((index: Int, item: T) -> Any)? = null,
	crossinline contentType: (index: Int, item: T) -> Any? = { _, _ -> null },
	crossinline itemContent: @Composable LazyItemScope.(index: Int, item: T, isDragging: Boolean) -> Unit
) = itemsIndexed(
	items = items,
	key = key,
	contentType = contentType
) { index, item ->

	val isDragging = index == state.draggingItemIndex
	if (isDragging) {
		// kept composed while scrolled out of view, or its drag gesture would be cancelled
		val pinnable = LocalPinnableContainer.current
		DisposableEffect(pinnable) {
			val handle = pinnable?.pin()
			onDispose { handle?.release() }
		}
	}
	val draggingModifier = if (isDragging) {
		Modifier
			.zIndex(1f)
			.graphicsLayer { translationY = state.draggingItemOffset }
	} else if (index == state.previousIndexOfDraggedItem) {
		Modifier
			.zIndex(1f)
			.graphicsLayer { translationY = state.previousItemOffset.value }
	} else if (state.draggingItemIndex == null) {
		// Placement animation during a drag lets rows moving into their new slots repeatedly
		// cross the held row's midpoint, advancing it again without any pointer movement.
		loakAnimateItem()
	} else {
		Modifier
	}
	Box(modifier = draggingModifier) {
		itemContent(index, item, isDragging)
	}
}

/**
 * Adds a list of draggable items.
 *
 * To the items to be draggable, it's also necessary to add the modifier [dragHandle] to
 * the preferable UI element of the item which will be the target of the drag operation.
 *
 * @param items the data list
 * @param key a factory of stable and unique keys representing the item. Using the same key
 * for multiple items in the list is not allowed. Type of the key should be saveable
 * via Bundle on Android. If null is passed the position in the list will represent the key.
 * When you specify the key the scroll position will be maintained based on the key, which
 * means if you add/remove items before the current visible item the item with the given key
 * will be kept as the first visible one. This can be overridden by calling 'requestScrollToItem'
 * on the 'LazyListState'.
 * @param contentType a factory of the content types for the item. The item compositions of
 * the same type could be reused more efficiently. Note that null is a valid type and items of such
 * type will be considered compatible.
 * @param itemContent the content displayed by a single item
 */
inline fun <T> LazyListScope.draggableItems(
	state: DraggableListState,
	items: List<T>,
	noinline key: ((item: T) -> Any)? = null,
	crossinline contentType: (item: T) -> Any? = { _ -> null },
	crossinline itemContent: @Composable LazyItemScope.(item: T, isDragging: Boolean) -> Unit
) = draggableItemsIndexed(
	state = state,
	items = items,
	key = key?.let { block -> { _, item -> block(item) } },
	contentType = { _, item -> contentType(item) },
	itemContent = { _, item, isDragging -> itemContent(item, isDragging) }
)
