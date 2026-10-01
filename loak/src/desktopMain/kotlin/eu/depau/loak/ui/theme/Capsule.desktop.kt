package eu.depau.loak.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

// Desktop uses the analytic RoundedCornerShape family (same as wasm) — the
// continuous variant's SkPathOp hit-testing can be costly on some GPUs.
actual fun ContinuousRoundedRectangle(size: Dp): CornerBasedShape = RoundedCornerShape(size)

actual fun ContinuousRoundedRectangle(corner: CornerSize): CornerBasedShape = RoundedCornerShape(corner)

actual fun ContinuousRoundedRectangle(
	topStart: Dp,
	topEnd: Dp,
	bottomEnd: Dp,
	bottomStart: Dp
): CornerBasedShape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

actual fun ContinuousRoundedRectangle(
	topStart: CornerSize,
	topEnd: CornerSize,
	bottomEnd: CornerSize,
	bottomStart: CornerSize
): CornerBasedShape = RoundedCornerShape(topStart, topEnd, bottomEnd, bottomStart)

actual val ContinuousCapsule: Shape = CircleShape
