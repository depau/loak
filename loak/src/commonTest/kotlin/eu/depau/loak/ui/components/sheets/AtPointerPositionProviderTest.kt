package eu.depau.loak.ui.components.sheets

import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals

class AtPointerPositionProviderTest {
	private val window = IntSize(1000, 800)
	private val menu = IntSize(300, 400)

	private fun place(
		x: Int,
		y: Int,
		direction: LayoutDirection = LayoutDirection.Ltr,
		size: IntSize = menu
	) = AtPointerPositionProvider(IntOffset(x, y)).let {
		it.calculatePosition(IntRect.Zero, window, direction, size) to it.transformOrigin
	}

	@Test
	fun opensDownRightOfThePointer() {
		assertEquals(IntOffset(100, 100) to TransformOrigin(0f, 0f), place(100, 100))
	}

	@Test
	fun flipsLeftAndUpAtTheEdges() {
		assertEquals(IntOffset(600, 300) to TransformOrigin(1f, 1f), place(900, 700))
	}

	@Test
	fun opensLeftInRightToLeftLayouts() {
		assertEquals(
			IntOffset(200, 100) to TransformOrigin(1f, 0f),
			place(500, 100, LayoutDirection.Rtl)
		)
	}

	@Test
	fun staysInsideWhenItFitsNeitherWay() {
		// too tall to open up or down from here: opens down, pushed up to fit
		assertEquals(IntOffset(100, 200), place(100, 450, size = IntSize(300, 600)).first)
	}
}
