package eu.depau.loak.ui.components.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import eu.depau.loak.ui.util.escapeToDismiss
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.paneTitle
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
	content: @Composable ColumnScope.() -> Unit,
) {
	if (menuOnWideWindows && LocalPlatformContext.current.isExpanded()) {
		DropdownMenu(
			expanded = true,
			onDismissRequest = onDismissRequest,
			// right-click menus: Esc closes them like the sheets
			modifier = Modifier.escapeToDismiss(onDismissRequest),
			shape = ContinuousRoundedRectangle(16.dp),
			containerColor = containerColor
		) {
			// the sheets scroll their own content; the menu has to give them a bounded height
			Column(Modifier.width(340.dp).heightIn(max = 560.dp), content = content)
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
		Column(
			Modifier.escapeToDismiss {
				scope.launch { sheetState.hide() }.invokeOnCompletion { onDismissRequest() }
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
