package eu.depau.loak.ui.util

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType

@Composable
fun Modifier.shimmerLoading(
	durationMillis: Int = 1100,
): Modifier {
	val platformContext = LocalPlatformContext.current
	if (platformContext.platformType == PlatformType.Web) {
		// ponytail: Static placeholder on Web avoids 96 concurrent infinite shader animations
		// and Skiko Wasm path ops / linear gradient memory exhaustion
		return background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
	}

	val transition = rememberInfiniteTransition(label = "")

	val translateAnimation by transition.animateFloat(
		initialValue = -200f,
		targetValue = 600f,
		animationSpec = infiniteRepeatable(
			animation = tween(
				durationMillis = durationMillis,
				easing = LinearEasing,
			),
			repeatMode = RepeatMode.Restart,
		),
		label = "",
	)

	return drawBehind {
		drawRect(
			brush = Brush.linearGradient(
				colors = listOf(
					Color.LightGray.copy(alpha = 0.1f),
					Color.LightGray.copy(alpha = 0.2f),
					Color.LightGray.copy(alpha = 0.1f),
				),
				start = Offset(x = translateAnimation, y = translateAnimation),
				end = Offset(x = translateAnimation + 200f, y = translateAnimation + 200f),
			)
		)
	}
}

/**
 * Item placement animation inside lazy lists.
 *
 * Disabled on Web: animating RenderNode graphics layers inside nested lazy lists
 * (e.g. LazyRow carousels hosted in a LazyVerticalGrid) triggers an infinite
 * recursion in Skiko's SkCanvas draw path, aborting the whole Wasm module. The default
 * specs mirror androidx `animateItem()` exactly.
 */
@Composable
fun LazyItemScope.loakAnimateItem(
	fadeInSpec: FiniteAnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
	placementSpec: FiniteAnimationSpec<IntOffset>? = spring(
		stiffness = Spring.StiffnessMediumLow,
		// androidx default is IntOffset(1, 1); VisibilityThreshold is internal to animation-core
		visibilityThreshold = IntOffset(1, 1)
	),
	fadeOutSpec: FiniteAnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow)
): Modifier {
	if (LocalPlatformContext.current.platformType == PlatformType.Web) {
		return Modifier
	}
	return Modifier.animateItem(fadeInSpec, placementSpec, fadeOutSpec)
}

/** [LazyGridItemScope] variant of [loakAnimateItem] — see the [LazyItemScope] overload for why. */
@Composable
fun LazyGridItemScope.loakAnimateItem(
	fadeInSpec: FiniteAnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
	placementSpec: FiniteAnimationSpec<IntOffset>? = spring(
		stiffness = Spring.StiffnessMediumLow,
		// androidx default is IntOffset(1, 1); VisibilityThreshold is internal to animation-core
		visibilityThreshold = IntOffset(1, 1)
	),
	fadeOutSpec: FiniteAnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow)
): Modifier {
	if (LocalPlatformContext.current.platformType == PlatformType.Web) {
		return Modifier
	}
	return Modifier.animateItem(fadeInSpec, placementSpec, fadeOutSpec)
}

/** [LazyStaggeredGridItemScope] variant of [loakAnimateItem] — see the [LazyItemScope] overload for why. */
@Composable
fun LazyStaggeredGridItemScope.loakAnimateItem(
	fadeInSpec: FiniteAnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow),
	placementSpec: FiniteAnimationSpec<IntOffset>? = spring(
		stiffness = Spring.StiffnessMediumLow,
		// androidx default is IntOffset(1, 1); VisibilityThreshold is internal to animation-core
		visibilityThreshold = IntOffset(1, 1)
	),
	fadeOutSpec: FiniteAnimationSpec<Float>? = spring(stiffness = Spring.StiffnessMediumLow)
): Modifier {
	if (LocalPlatformContext.current.platformType == PlatformType.Web) {
		return Modifier
	}
	return Modifier.animateItem(fadeInSpec, placementSpec, fadeOutSpec)
}
