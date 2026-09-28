package eu.depau.loak.domain.manager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Dp

// TODO: replace this because it's kind of finnicky
class BottomBarScrollManager(val thresholdPx: Float) {
	var isTriggered by mutableStateOf(false)

	/** Height of each bottom bar on screen, keyed by its owner; screens overlap during transitions. */
	val barHeights = mutableStateMapOf<Any, Dp>()
	private var accumulator = 0f

	val connection = object : NestedScrollConnection {
		override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
			val delta = available.y
			accumulator += delta

			if (accumulator < -thresholdPx && !isTriggered) {
				isTriggered = true
				accumulator = 0f
			} else if (accumulator > thresholdPx && isTriggered) {
				isTriggered = false
				accumulator = 0f
			}

			if ((delta > 0 && accumulator < 0) || (delta < 0 && accumulator > 0)) {
				accumulator = 0f
			}
			return Offset.Zero
		}
	}
}
