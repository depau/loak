package eu.depau.loak.ui.screens.lyrics.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager

/** The lyrics' line size: karaoke mode makes it as large as the window allows. */
val LocalLyricsFontSize = compositionLocalOf { 32.sp }

/** Karaoke on wide windows: the lines are centred. */
val LocalLyricsCentered = compositionLocalOf { false }

@Composable
fun LyricsScreenKaraokeText(
	text: String,
	progress: Float,
	isActive: Boolean,
	isSynced: Boolean,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
	/** Inside the tappable area, so the gaps between lines seek too. */
	contentPadding: PaddingValues = PaddingValues()
) {
	val preferenceManager = koinInject<PreferenceManager>()

	var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

	val smoothProgress by animateFloatAsState(
		targetValue = progress,
		animationSpec = spring(stiffness = Spring.StiffnessLow, visibilityThreshold = 0.001f)
	)

	val lyricsBeatByBeat = preferenceManager.lyricsBeatByBeat
	val lyricsBrightInactive = preferenceManager.lyricsBrightInactive

	val isRtl = textLayoutResult?.let { layout ->
		(0 until layout.lineCount).any { lineIndex ->
			layout.getBidiRunDirection(layout.getLineStart(lineIndex)) == ResolvedTextDirection.Rtl
		}
	} ?: false

	val inactiveAlpha = when {
		!isSynced -> 1f
		lyricsBrightInactive -> 0.9f
		else -> 0.35f
	}

	val alphaTransition by animateFloatAsState(
		targetValue = if (isActive) 1f else inactiveAlpha,
		animationSpec = spring(stiffness = Spring.StiffnessLow)
	)

	Box(
		modifier = modifier
			.clip(MaterialTheme.shapes.medium)
			.clickable { onClick() }
			.padding(contentPadding)
	) {
		Text(
			text = text,
			fontSize = LocalLyricsFontSize.current,
			lineHeight = LocalLyricsFontSize.current * 1.2f,
			fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
			textAlign = if (LocalLyricsCentered.current) TextAlign.Center else if (isRtl) TextAlign.End else TextAlign.Start,
			style = MaterialTheme.typography.headlineLargeEmphasized,
			color = if (lyricsBrightInactive) Color.White.copy(alpha = alphaTransition * 0.4f)
			else MaterialTheme.colorScheme.onSurface.copy(alpha = alphaTransition * 0.4f),
			onTextLayout = { textLayoutResult = it },
			modifier = Modifier.fillMaxWidth()
		)

		if (isActive) {
			Text(
				text = text,
				fontSize = LocalLyricsFontSize.current,
			lineHeight = LocalLyricsFontSize.current * 1.2f,
				fontWeight = FontWeight.Bold,
				textAlign = if (LocalLyricsCentered.current) TextAlign.Center else if (isRtl) TextAlign.End else TextAlign.Start,
				style = MaterialTheme.typography.headlineLargeEmphasized,
				color = Color.White,
				modifier = Modifier
					.fillMaxWidth()
					.alpha(alphaTransition)
					.then(
						if (lyricsBeatByBeat) {
							Modifier
								.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
								.drawWithCache {
									onDrawWithContent {
										val layout = textLayoutResult ?: return@onDrawWithContent
										drawContent()

										val totalWidth = (0 until layout.lineCount).sumOf {
											(layout.getLineRight(it) - layout.getLineLeft(it)).toDouble()
										}.toFloat()

										val feather = 50f
										val adjustedTotalWidth = totalWidth + (feather * 2)
										val currentPixelTarget =
											(adjustedTotalWidth * smoothProgress) - feather

										var accumulatedWidth = 0f

										for (i in 0 until layout.lineCount) {
											val lineLeft = layout.getLineLeft(i)
											val lineRight = layout.getLineRight(i)
											val lineWidth = lineRight - lineLeft
											if (lineWidth <= 0f) continue

											val lineTop = layout.getLineTop(i)
											val lineBottom = layout.getLineBottom(i)
											val isRtl =
												layout.getBidiRunDirection(layout.getLineStart(i)) == ResolvedTextDirection.Rtl

											val startOffFadeIn =
												currentPixelTarget - accumulatedWidth - feather
											val endOfFadeIn =
												currentPixelTarget - accumulatedWidth + feather

											val startX = if (isRtl) lineRight else lineLeft
											val endX = if (isRtl) lineLeft else lineRight

											val brush = Brush.linearGradient(
												0.0f to Color.White,
												(startOffFadeIn / lineWidth).coerceIn(
													0f,
													1f
												) to Color.White,
												(endOfFadeIn / lineWidth).coerceIn(
													0f,
													1f
												) to Color.Transparent,
												1.0f to Color.Transparent,
												start = Offset(startX, 0f),
												end = Offset(endX, 0f)
											)

											// the mask covers everything the line may draw, not just its box: at
											// large sizes descenders and overhangs reach past it, and anything the
											// mask misses would show fully lit ahead of time
											val top = if (i == 0) 0f else lineTop
											val bottom = if (i == layout.lineCount - 1) size.height else lineBottom
											drawRect(
												brush = brush,
												topLeft = Offset(0f, top),
												size = Size(size.width, bottom - top),
												blendMode = BlendMode.SrcIn
											)
											accumulatedWidth += lineWidth
										}
									}
								}
						} else Modifier
					)
			)
		}
	}
}
