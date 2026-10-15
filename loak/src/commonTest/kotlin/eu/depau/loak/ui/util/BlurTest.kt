package eu.depau.loak.ui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlurTest {
	private val red = 0xffff0000.toInt()

	@Test
	fun clampedEdgesKeepAPlainPicturePlain() {
		val pixels = IntArray(20 * 10) { red }
		blurArgb(pixels, 20, 10, sigma = 4f, clampEdges = true)
		assertTrue(pixels.all { it == red })
	}

	@Test
	fun transparentEdgesFadeThePictureOutWithoutDarkeningIt() {
		val pixels = IntArray(21 * 21) { red }
		blurArgb(pixels, 21, 21, sigma = 4f, clampEdges = false)
		val corner = pixels[0] ushr 24
		val middle = pixels[10 * 21 + 10] ushr 24
		assertTrue(corner < middle, "corner alpha $corner, middle $middle")
		// still red wherever it shows: premultiplied, so transparency doesn't mix in black
		assertTrue(pixels.all { it ushr 24 == 0 || it and 0xffffff == 0xff0000 })
	}

	@Test
	fun aDotSpreadsEvenlyAndKeepsItsWeight() {
		val size = 41
		val pixels = IntArray(size * size)
		pixels[20 * size + 20] = red
		blurArgb(pixels, size, size, sigma = 3f, clampEdges = false)
		fun alpha(x: Int, y: Int) = pixels[y * size + x] ushr 24
		assertEquals(alpha(15, 20), alpha(25, 20))
		assertEquals(alpha(20, 15), alpha(20, 25))
		assertEquals(alpha(15, 20), alpha(20, 15))
		assertTrue(alpha(20, 20) > alpha(23, 20))
		// the dot's one opaque pixel, spread out: about 255 in all (rounding loses a little)
		val total = pixels.sumOf { it ushr 24 }
		assertTrue(total in 200..300, "total alpha $total")
	}
}
