package eu.depau.loak.ui.util

import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Blurs ARGB (not premultiplied) [pixels] in place, near enough a Gaussian of [sigma]: three
 * box blurs each way. Past the edges the picture repeats its edge pixels ([clampEdges]), or
 * is transparent, so the blur fades it out there.
 */
internal fun blurArgb(pixels: IntArray, width: Int, height: Int, sigma: Float, clampEdges: Boolean) {
	// three boxes of this half-width have the variance of the Gaussian
	val radius = ((sqrt(4f * sigma * sigma + 1f) - 1f) / 2f).roundToInt()
	if (radius < 1 || width < 1 || height < 1) return
	// premultiplied, or transparent pixels would darken their neighbours' colours
	val channels = Array(4) { FloatArray(pixels.size) }
	for (i in pixels.indices) {
		val p = pixels[i]
		val a = (p ushr 24) / 255f
		channels[0][i] = a
		channels[1][i] = ((p shr 16) and 0xff) * a
		channels[2][i] = ((p shr 8) and 0xff) * a
		channels[3][i] = (p and 0xff) * a
	}
	val line = FloatArray(maxOf(width, height))
	for (channel in channels) repeat(3) {
		for (y in 0 until height) boxBlur(channel, y * width, 1, width, radius, clampEdges, line)
		for (x in 0 until width) boxBlur(channel, x, width, height, radius, clampEdges, line)
	}
	for (i in pixels.indices) {
		val a = channels[0][i]
		if (a <= 0f) {
			pixels[i] = 0
			continue
		}
		fun byte(value: Float) = value.roundToInt().coerceIn(0, 255)
		pixels[i] = (byte(a * 255f) shl 24) or
			(byte(channels[1][i] / a) shl 16) or
			(byte(channels[2][i] / a) shl 8) or
			byte(channels[3][i] / a)
	}
}

/** One box blur pass along the [count] values at [start], [stride] apart. */
private fun boxBlur(
	values: FloatArray,
	start: Int,
	stride: Int,
	count: Int,
	radius: Int,
	clampEdges: Boolean,
	line: FloatArray,
) {
	for (i in 0 until count) line[i] = values[start + i * stride]
	fun at(i: Int) = when {
		i in 0 until count -> line[i]
		clampEdges -> line[i.coerceIn(0, count - 1)]
		else -> 0f
	}
	var sum = 0f
	for (i in -radius..radius) sum += at(i)
	val size = 2 * radius + 1
	for (i in 0 until count) {
		values[start + i * stride] = sum / size
		sum += at(i + radius + 1) - at(i - radius)
	}
}
