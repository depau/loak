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
import androidx.compose.runtime.collectAsState
import eu.depau.loak.domain.discover.PreviewManager
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.BottomBarCollapseMode
import eu.depau.loak.domain.models.settings.MiniPlayerStyle

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
	val detached = preferenceManager.miniPlayerStyle == MiniPlayerStyle.Detached
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
			if (detached)
				Modifier.background(
					Brush.verticalGradient(
						0f to Color.Transparent,
						1f to MaterialTheme.colorScheme.surface.copy(alpha = shadowFadeProgress)
					)
				)
			else Modifier
		)
	) {
		val preview = koinInject<PreviewManager>().activePreview.collectAsState().value
		val expanded = LocalPlatformContext.current.isExpanded()
		val playerModifier = Modifier.graphicsLayer {
			alpha = progress.coerceIn(0f..1f)
			val y = (1f - progress) * size.height * 2
			// the attached mini player doesn't bounce above the bottom bar
			translationY = if (expanded || detached) y else y.coerceAtLeast(0f)
		}
		when {
			hideMiniPlayer -> {}
			// a preview takes the player's place until it ends or is closed
			preview != null -> PreviewBar(preview, playerModifier, enabled = !scrolled)
			expanded -> PlayerBar(playerModifier, enabled = !scrolled)
			else -> MiniPlayer(playerModifier, enabled = !scrolled)
		}
		BottomBar(
			containerColor = if (detached)
				NavigationBarDefaults.containerColor.copy(alpha = 0f)
			else NavigationBarDefaults.containerColor,
			windowInsets = bottomBarWindowInsets,
			modifier = Modifier.graphicsLayer {
				alpha = progress.coerceIn(0f..1f)
				translationY = ((1f - progress) * size.height).coerceAtLeast(
					if (detached) -2048f else 0f
				)
			},
			enabled = !scrolled
		)
	}
}
