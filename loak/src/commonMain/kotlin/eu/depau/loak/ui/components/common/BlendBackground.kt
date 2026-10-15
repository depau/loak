package eu.depau.loak.ui.components.common

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil3.compose.asPainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import eu.depau.loak.di.COVER_ART_SMALL
import eu.depau.loak.di.CoverArtId
import eu.depau.loak.di.CoverArtInterceptor
import eu.depau.loak.di.getStaticImageLoader
import eu.depau.loak.ui.util.blurArgb
import eu.depau.loak.ui.util.imageBitmapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import kotlin.math.max
import kotlin.math.roundToInt
import coil3.compose.LocalPlatformContext as LocalCoilPlatformContext

private val BLUR = 80.dp

// the blurred pictures are rendered this big (their longer side, in pixels) and scaled up:
// an 80 dp blur leaves no detail a bigger picture would keep
private const val BLURRED_SIZE = 128f

/** The cover, blurred: the whole background, and the two tiles turning over it. */
private class Blurred(val background: ImageBitmap, val topStart: ImageBitmap, val bottomEnd: ImageBitmap)

/**
 * The cover art, blurred, slowly turning behind the player: the cover filling the
 * background, and over it a quarter-sized tile of it in two opposite corners, each
 * turning on itself while the pair turns around the middle.
 *
 * The blur is rendered once per cover and size, into small pictures, on the CPU (the
 * same everywhere, Android before 12 included); each frame only turns them. Blurring
 * commutes with turning, so it looks as if the turning scene were blurred, without a
 * full-screen blur every frame.
 */
@Composable
fun BlendBackground(
	coverArtId: String?,
	modifier: Modifier = Modifier,
	isPaused: Boolean = false
) {
	val coilPlatformContext = LocalCoilPlatformContext.current
	val coverArtInterceptor = koinInject<CoverArtInterceptor>()
	val staticImageLoader = remember(coilPlatformContext) {
		getStaticImageLoader(coilPlatformContext, coverArtInterceptor)
	}
	var cover by remember { mutableStateOf<Painter?>(null) }
	var elapsedMs by remember { mutableFloatStateOf(0f) }

	LaunchedEffect(coverArtId) {
		cover = coverArtId?.let {
			staticImageLoader.execute(
				ImageRequest.Builder(coilPlatformContext)
					.data(CoverArtId(it, COVER_ART_SMALL))
					.diskCachePolicy(CachePolicy.ENABLED)
					.memoryCachePolicy(CachePolicy.ENABLED)
					.build()
			).image?.asPainter(coilPlatformContext)
		}
	}

	LaunchedEffect(isPaused) {
		if (isPaused) return@LaunchedEffect
		var last = -1L
		// the infinite-animation clock: UI tests skip it, or the screen would never settle
		while (true) {
			withInfiniteAnimationFrameNanos { now ->
				if (last >= 0) elapsedMs += (now - last) / 1_000_000f
				last = now
			}
		}
	}

	BlendBackground(cover, { elapsedMs }, modifier)
}

/** [cover], blurred and turned as it is [elapsedMs] into the animation. */
@Composable
private fun BlendBackground(cover: Painter?, elapsedMs: () -> Float, modifier: Modifier = Modifier) {
	val blurPx = with(LocalDensity.current) { BLUR.toPx() }
	val backgroundColor = MaterialTheme.colorScheme.background
	var size by remember { mutableStateOf(IntSize.Zero) }
	var blurred by remember { mutableStateOf<Blurred?>(null) }

	LaunchedEffect(cover, size, blurPx) {
		blurred = if (cover == null || size == IntSize.Zero) null else withContext(Dispatchers.Default) {
			val scale = BLURRED_SIZE / max(size.width, size.height)
			val full = size.toSize()
			val tile = full / 2f
			Blurred(
				background = blurred(cover, full, Alignment.Center, scale, blurPx, 0f),
				topStart = blurred(cover, tile, Alignment.TopStart, scale, blurPx, 2 * blurPx),
				bottomEnd = blurred(cover, tile, Alignment.BottomEnd, scale, blurPx, 2 * blurPx),
			)
		}
	}

	Spacer(
		modifier
			.fillMaxSize()
			.onSizeChanged { size = it }
			.drawBehind {
				drawRect(backgroundColor)
				blurred?.let { drawTurning(it, elapsedMs(), 2 * blurPx) }
				// bright covers (saturated reds, whites) need this much for light text on top
				drawRect(Color.Black.copy(alpha = 0.5f))
			}
	)
}

private fun DrawScope.drawTurning(blurred: Blurred, elapsedMs: Float, padding: Float) {
	drawImage(blurred.background, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
	val tile = size / 2f
	rotate(-360f / 24_000f * elapsedMs) {
		drawTile(blurred.topStart, Offset.Zero, tile, padding, 360f / 12_000f * elapsedMs)
		drawTile(
			blurred.bottomEnd, Offset(tile.width, tile.height), tile, padding,
			360f / 20_000f * elapsedMs
		)
	}
}

/** A tile (blurred past its edges by [padding]) at [topLeft], turned [degrees] on its centre. */
private fun DrawScope.drawTile(
	image: ImageBitmap,
	topLeft: Offset,
	tile: Size,
	padding: Float,
	degrees: Float,
) = withTransform({ rotate(degrees, pivot = topLeft + Offset(tile.width / 2, tile.height / 2)) }) {
	drawImage(
		image,
		dstOffset = IntOffset((topLeft.x - padding).roundToInt(), (topLeft.y - padding).roundToInt()),
		dstSize = IntSize(
			(tile.width + 2 * padding).roundToInt(),
			(tile.height + 2 * padding).roundToInt()
		),
	)
}

private val saturated = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(1.5f) })

/**
 * [cover] cropped to fill [area] (keeping the part at [alignment]), drawn at [scale] and
 * blurred by [blur]: with [padding] of transparency around it for the blur to fade it out
 * into, or (none) with the edges clamped, as a blurred full-screen picture looks.
 */
private fun blurred(
	cover: Painter,
	area: Size,
	alignment: Alignment,
	scale: Float,
	blur: Float,
	padding: Float,
): ImageBitmap {
	val inner = area * scale
	val pad = padding * scale
	val width = (inner.width + 2 * pad).roundToInt().coerceAtLeast(1)
	val height = (inner.height + 2 * pad).roundToInt().coerceAtLeast(1)
	val bitmap = ImageBitmap(width, height)
	CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(width.toFloat(), height.toFloat())) {
		clipRect(pad, pad, pad + inner.width, pad + inner.height) {
			val intrinsic = cover.intrinsicSize
			val fill = if (intrinsic.isUnspecified || intrinsic.width <= 0f || intrinsic.height <= 0f) {
				inner
			} else {
				intrinsic * max(inner.width / intrinsic.width, inner.height / intrinsic.height)
			}
			val offset = alignment.align(
				IntSize(fill.width.roundToInt(), fill.height.roundToInt()),
				IntSize(inner.width.roundToInt(), inner.height.roundToInt()),
				LayoutDirection.Ltr,
			)
			translate(pad + offset.x, pad + offset.y) {
				with(cover) { draw(fill, colorFilter = saturated) }
			}
		}
	}
	val pixels = IntArray(width * height)
	bitmap.readPixels(pixels)
	// Compose's blur radius, as a Gaussian's sigma
	blurArgb(pixels, width, height, sigma = 0.57735f * blur * scale + 0.5f, clampEdges = padding == 0f)
	return imageBitmapOf(pixels, width, height)
}
