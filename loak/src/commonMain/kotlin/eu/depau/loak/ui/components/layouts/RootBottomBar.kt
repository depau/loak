package eu.depau.loak.ui.components.layouts

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isExpanded
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.BottomBarCollapseMode
import eu.depau.loak.domain.models.settings.MiniPlayerStyle
import eu.depau.loak.ui.util.easedVerticalGradient

@Composable
fun RootBottomBar(
	scrolled: Boolean,
	modifier: Modifier = Modifier,
	shadows: Boolean = true,
	hideMiniPlayer: Boolean = false,
	bottomBarWindowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val scrolled =
		scrolled && preferenceManager.bottomBarCollapseMode == BottomBarCollapseMode.OnScroll
	val progress by animateFloatAsState(
		targetValue = if (scrolled) 0f else 1f,
		animationSpec = spring(
			dampingRatio = Spring.DampingRatioLowBouncy,
			stiffness = Spring.StiffnessMediumLow
		)
	)
	val shadowFadeProgress by animateFloatAsState(
		targetValue = if (scrolled || !shadows) 0f else 1f,
		animationSpec = tween(durationMillis = 600)
	)
	val scrollManager = LocalBottomBarScrollManager.current
	val density = LocalDensity.current
	val owner = remember { Any() }
	var measuredHeight by remember { mutableStateOf(0.dp) }
	LaunchedEffect(scrolled, measuredHeight) {
		scrollManager.barHeights[owner] = if (scrolled) 0.dp else measuredHeight
	}
	DisposableEffect(owner) { onDispose { scrollManager.barHeights.remove(owner) } }

	Column(
		modifier = modifier.onSizeChanged {
			measuredHeight = with(density) { it.height.toDp() }
		}.then(
			if (preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached)
				Modifier.background(
					Brush.easedVerticalGradient(color = MaterialTheme.colorScheme.surface.copy(alpha = shadowFadeProgress))
				)
			else Modifier
		)
	) {
		if (!hideMiniPlayer && LocalPlatformContext.current.isExpanded()) PlayerBar(
			modifier = Modifier.graphicsLayer {
				alpha = progress.coerceIn(0f..1f)
				translationY = (1f - progress) * size.height * 2
			},
			enabled = !scrolled
		) else if (!hideMiniPlayer) MiniPlayer(
			modifier = Modifier.graphicsLayer {
				alpha = progress.coerceIn(0f..1f)
				translationY = ((1f - progress) * (size.height * 2)).coerceAtLeast(
					if (preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached) -2048f else 0f
				)
			},
			enabled = !scrolled
		)
		BottomBar(
			containerColor = if (preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached)
				NavigationBarDefaults.containerColor.copy(alpha = 0f)
			else NavigationBarDefaults.containerColor,
			windowInsets = bottomBarWindowInsets,
			modifier = Modifier.graphicsLayer {
				alpha = progress.coerceIn(0f..1f)
				translationY = ((1f - progress) * size.height).coerceAtLeast(
					if (preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached) -2048f else 0f
				)
			},
			enabled = !scrolled
		)
	}
}
