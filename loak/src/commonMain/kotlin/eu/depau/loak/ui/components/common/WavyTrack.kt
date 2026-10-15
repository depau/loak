package eu.depau.loak.ui.components.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val WaveThickness = 4.dp
private val TrackThickness = 16.dp
private val TrackInsideCornerSize = 2.dp
/** Material's slider handle height: the track is never shorter. */
private val HandleHeight = 44.dp

/**
 * A wavy slider track: wavy-slider's (ir.mahozad.multiplatform:wavy-slider, Apache-2.0) material3
 * `SliderDefaults.Track`, with the wave scrolling [waveVelocity] toward the tail, minus the
 * library's wave clock: that one is an endless `withFrameNanos` loop that keeps going when the
 * wave is flat, so a paused player redrew every frame, and UI tests never saw the screen settle.
 * This one runs on the infinite-animation clock, and only while there's a wave to move.
 *
 * [thumbWidth] is the thumb's, for the [thumbTrackGapSize] around it (wavy-slider reads it from
 * Material's internal `SliderState.thumbWidth`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WavyTrack(
	sliderState: SliderState,
	waveLength: Dp,
	waveHeight: Dp,
	waveVelocity: Dp,
	thumbWidth: Dp = 0.dp,
	thumbTrackGapSize: Dp = 0.dp,
) {
	val colors = SliderDefaults.colors()
	val activeColor = colors.activeTrackColor
	val inactiveColor = colors.inactiveTrackColor
	val height by animateDpAsState(waveHeight, tween(300, easing = FastOutSlowInEasing))
	val speed by animateDpAsState(waveVelocity, tween(2000, easing = LinearOutSlowInEasing))
	val shift = remember { mutableStateOf(0.dp) }
	val moving by remember { derivedStateOf { height != 0.dp } }
	LaunchedEffect(waveVelocity, moving) {
		if (!moving) return@LaunchedEffect
		val startShift = shift.value
		val startTime = withInfiniteAnimationFrameNanos { it }
		while (true) {
			val playTime = (withInfiniteAnimationFrameNanos { it } - startTime) / 1_000_000_000f
			shift.value = startShift + speed * playTime
		}
	}
	Canvas(
		Modifier
			.fillMaxWidth()
			.height(max(WaveThickness + waveHeight, HandleHeight))
			.rotate(if (LocalLayoutDirection.current == LayoutDirection.Rtl) 180f else 0f)
	) {
		val range = sliderState.valueRange
		val fraction = ((sliderState.value - range.start) / (range.endInclusive - range.start))
			.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
		val valueX = size.width * fraction
		val gap = if (thumbTrackGapSize > 0.dp) thumbWidth.toPx() / 2 + thumbTrackGapSize.toPx() else 0f
		val wave = WaveThickness.toPx()
		val inside = CornerRadius(TrackInsideCornerSize.toPx())
		/** A flat bar, rounded fully on its outer end and slightly next to the thumb. */
		fun bar(left: Float, right: Float, thickness: Float, color: Color, roundLeft: Boolean) {
			val outside = CornerRadius(thickness / 2)
			val (l, r) = if (roundLeft) outside to inside else inside to outside
			val rect = RoundRect(left, center.y - thickness / 2, right, center.y + thickness / 2, l, r, r, l)
			drawPath(Path().apply { addRoundRect(rect) }, color)
		}

		// the active part: the wave, from the start to the thumb
		val activeEnd = valueX - gap
		if (activeEnd > wave / 2) {
			if (height == 0.dp) bar(0f, activeEnd, wave, activeColor, roundLeft = true)
			else {
				val shiftPx = shift.value.toPx()
				val lengthPx = waveLength.toPx()
				val heightPx = height.toPx()
				val from = (wave / 2).roundToInt()
				val to = (activeEnd - wave / 2).toInt()
				fun y(x: Int) = ((sin((x - from + shiftPx) / lengthPx * 2 * PI) * heightPx + size.height) / 2).toFloat()
				drawPath(
					Path().apply {
						moveTo(from.toFloat(), y(from))
						for (x in from..to) lineTo(x.toFloat(), y(x))
					},
					activeColor,
					style = Stroke(width = wave, join = StrokeJoin.Round, cap = StrokeCap.Round)
				)
			}
		}

		// the inactive part: flat, from the thumb to the end, with the stop indicator
		val track = TrackThickness.toPx()
		if (valueX < size.width - gap - track / 2) {
			bar(valueX + gap, size.width, track, inactiveColor, roundLeft = false)
			with(SliderDefaults) {
				drawStopIndicator(Offset(size.width - track / 2, center.y), TrackStopIndicatorSize, activeColor)
			}
		}
	}
}
