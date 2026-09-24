package eu.depau.loak.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

actual fun ContinuousRoundedRectangle(size: Dp): CornerBasedShape =
	com.kyant.capsule.ContinuousRoundedRectangle(size)

actual fun ContinuousRoundedRectangle(corner: CornerSize): CornerBasedShape =
	com.kyant.capsule.ContinuousRoundedRectangle(corner)

actual fun ContinuousRoundedRectangle(
	topStart: Dp,
	topEnd: Dp,
	bottomEnd: Dp,
	bottomStart: Dp
): CornerBasedShape = com.kyant.capsule.ContinuousRoundedRectangle(topStart, topEnd, bottomEnd, bottomStart)

actual fun ContinuousRoundedRectangle(
	topStart: CornerSize,
	topEnd: CornerSize,
	bottomEnd: CornerSize,
	bottomStart: CornerSize
): CornerBasedShape = com.kyant.capsule.ContinuousRoundedRectangle(topStart, topEnd, bottomEnd, bottomStart)

actual val ContinuousCapsule: Shape = com.kyant.capsule.ContinuousCapsule
