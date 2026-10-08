package eu.depau.loak.ui.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

expect fun ContinuousRoundedRectangle(size: Dp): CornerBasedShape

expect fun ContinuousRoundedRectangle(corner: CornerSize): CornerBasedShape

expect fun ContinuousRoundedRectangle(
	topStart: Dp = 0.dp,
	topEnd: Dp = 0.dp,
	bottomEnd: Dp = 0.dp,
	bottomStart: Dp = 0.dp
): CornerBasedShape

expect fun ContinuousRoundedRectangle(
	topStart: CornerSize = CornerSize(0.dp),
	topEnd: CornerSize = CornerSize(0.dp),
	bottomEnd: CornerSize = CornerSize(0.dp),
	bottomStart: CornerSize = CornerSize(0.dp)
): CornerBasedShape

expect val ContinuousCapsule: Shape

/** Album, song and artist artwork. */
val CoverArtShape: Shape = ContinuousRoundedRectangle(10.dp)

/** The smaller artwork in list rows and sheet headers. */
val SmallCoverArtShape: Shape = ContinuousRoundedRectangle(8.dp)
