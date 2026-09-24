package eu.depau.loak.domain.models.settings

import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import eu.depau.loak.ui.theme.ContinuousCapsule
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle

enum class CoverArtShape(
	val shape: Shape,
	val decreasedShape: Shape = shape
) {
	Square(RectangleShape),
	Soft(ContinuousRoundedRectangle(10.dp), ContinuousRoundedRectangle(8.dp)),
	Curved(ContinuousRoundedRectangle(32.dp), ContinuousRoundedRectangle(10.dp)),
	Circle(ContinuousCapsule)
}
