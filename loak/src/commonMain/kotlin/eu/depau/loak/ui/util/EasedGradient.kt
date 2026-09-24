package eu.depau.loak.ui.util

import androidx.compose.animation.core.EaseInCirc
import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush

// ponytail: Standard verticalGradient prevents Skia NaN / interval solver infinite recursion
fun Brush.Companion.easedGradient(
	easing: Easing = EaseInCirc,
	start: Offset = Offset.Zero,
	end: Offset = Offset.Zero,
	numStops: Int = 16,
	color: Color = Color.Black
): Brush = verticalGradient(
	0f to Color.Transparent,
	1f to color
)

fun Brush.Companion.easedVerticalGradient(
	easing: Easing = EaseInCirc,
	startY: Float = Float.POSITIVE_INFINITY,
	endY: Float = 0.0f,
	numStops: Int = 16,
	color: Color = Color.Black
): Brush = verticalGradient(
	0f to Color.Transparent,
	1f to color
)
