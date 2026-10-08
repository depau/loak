package eu.depau.loak.ui.components.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import eu.depau.loak.ui.util.escapeToDismiss
import eu.depau.loak.ui.util.lastRightClick
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.paneTitle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isExpanded
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import eu.depau.loak.ui.util.SheetHideMotionSpec
import eu.depau.loak.ui.util.SheetShowMotionSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModalBottomSheet(
	onDismissRequest: () -> Unit,
	modifier: Modifier = Modifier,
	sheetState: SheetState = rememberBottomSheetState(SheetValue.Hidden),
	sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
	sheetGesturesEnabled: Boolean = true,
	shape: Shape = BottomSheetDefaults.ExpandedShape,
	containerColor: Color = BottomSheetDefaults.ContainerColor,
	contentColor: Color = contentColorFor(containerColor),
	tonalElevation: Dp = 0.dp,
	scrimColor: Color = BottomSheetDefaults.ScrimColor,
	dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
	contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
	properties: ModalBottomSheetProperties = ModalBottomSheetProperties(),
	sheetTitle: String? = null,
	/** Options sheets: on expanded windows, a menu anchored to the item instead. */
	menuOnWideWindows: Boolean = false,
	/** While set, back and Esc call it instead of closing: for a sheet showing a sub-page. */
	onBack: (() -> Unit)? = null,
	content: @Composable ColumnScope.() -> Unit,
) {
	if (menuOnWideWindows && LocalPlatformContext.current.isExpanded()) {
		// right-click menus: Esc closes them like the sheets
		val escape = Modifier.escapeToDismiss { (onBack ?: onDismissRequest)() }
		val menuShape = ContinuousRoundedRectangle(16.dp)
		// the sheets scroll their own content; the menu has to give them a bounded height
		val bounded = Modifier.width(340.dp).heightIn(max = 560.dp)
		// opened by a right click: at the pointer, like any desktop context menu
		val pointer = remember { lastRightClick }
		if (pointer != null) {
			DropdownMenuPopup(
				expanded = true,
				onDismissRequest = onDismissRequest,
				modifier = escape,
				popupPositionProvider = remember { AtPointerPositionProvider(pointer) }
			) {
				Surface(
					shape = menuShape,
					color = containerColor,
					tonalElevation = MenuDefaults.TonalElevation,
					shadowElevation = MenuDefaults.ShadowElevation
				) {
					SubPageBack(onBack)
					Column(Modifier.padding(vertical = 8.dp).then(bounded), content = content)
				}
			}
		} else {
			DropdownMenu(
				expanded = true,
				onDismissRequest = onDismissRequest,
				modifier = escape,
				shape = menuShape,
				containerColor = containerColor
			) {
				SubPageBack(onBack)
				Column(bounded, content = content)
			}
		}
		return
	}
	androidx.compose.material3.ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		modifier = modifier.semantics {
			sheetTitle?.let { sheetTitle ->
				paneTitle = sheetTitle
			}
		},
		sheetState = sheetState,
		sheetMaxWidth = sheetMaxWidth,
		sheetGesturesEnabled = sheetGesturesEnabled,
		shape = shape,
		containerColor = containerColor,
		contentColor = contentColor,
		tonalElevation = tonalElevation,
		scrimColor = scrimColor,
		dragHandle = dragHandle,
		contentWindowInsets = contentWindowInsets,
		properties = properties,
	) {
		val scope = rememberCoroutineScope()
		SubPageBack(onBack)
		Column(
			Modifier.escapeToDismiss {
				if (onBack != null) onBack()
				else scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
			},
			content = content
		)
	}

	@Suppress("INVISIBLE_REFERENCE")
	LaunchedEffect(Unit) {
		sheetState.showMotionSpec = SheetShowMotionSpec
		sheetState.hideMotionSpec = SheetHideMotionSpec
	}
}

/**
 * Puts a menu's corner on [pointer] (window coordinates): its top-left one, or top-right in
 * right-to-left layouts. Where the menu doesn't fit it opens the other way, then it's kept
 * inside the window.
 */
internal class AtPointerPositionProvider(private val pointer: IntOffset) :
	DropdownMenuPopupPositionProvider {
	// read by the menu's open animation, which may draw before the position is known
	override var transformOrigin by mutableStateOf(TransformOrigin(0f, 0f))
		private set

	override fun calculatePosition(
		anchorBounds: IntRect,
		windowSize: IntSize,
		layoutDirection: LayoutDirection,
		popupContentSize: IntSize
	): IntOffset {
		val (width, height) = popupContentSize
		val fitsRight = pointer.x + width <= windowSize.width
		val fitsLeft = pointer.x - width >= 0
		val toLeft =
			if (layoutDirection == LayoutDirection.Rtl) fitsLeft || !fitsRight else !fitsRight
		val up = pointer.y + height > windowSize.height && pointer.y - height >= 0
		transformOrigin = TransformOrigin(if (toLeft) 1f else 0f, if (up) 1f else 0f)
		val x = if (toLeft) pointer.x - width else pointer.x
		val y = if (up) pointer.y - height else pointer.y
		return IntOffset(
			x.coerceIn(0, (windowSize.width - width).coerceAtLeast(0)),
			y.coerceIn(0, (windowSize.height - height).coerceAtLeast(0))
		)
	}
}

/**
 * Back goes to the sheet's main page while it shows a sub-page. Composed inside the sheet, so it
 * registers with the back dispatcher of the sheet's own window, where back arrives.
 */
@Composable
private fun SubPageBack(onBack: (() -> Unit)?) {
	NavigationBackHandler(
		state = rememberNavigationEventState(NavigationEventInfo.None),
		isBackEnabled = onBack != null,
		onBackCompleted = { onBack?.invoke() }
	)
}
