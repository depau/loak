package eu.depau.loak.ui.components.layouts

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import eu.depau.loak.di.LocalMouseInUse
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_refresh
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Refresh
import org.jetbrains.compose.resources.stringResource
import kotlin.math.ceil
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox as M3PullToRefreshBox

/**
 * A screen's refresh action, filled in by its [PullToRefreshBox], so the top bar's refresh
 * button and F5 can run it without pulling.
 */
class RefreshSlot {
	var refresh by mutableStateOf<(() -> Unit)?>(null)
	var isRefreshing by mutableStateOf(false)
}

/** The screen's own slot; null outside a nav entry. */
val LocalRefreshSlot = staticCompositionLocalOf<RefreshSlot?> { null }

/** Every screen on display's slot, the newest entry last: F5 refreshes the newest that can. */
val refreshSlots = mutableStateListOf<RefreshSlot>()

/** Gives each nav entry a [RefreshSlot]. */
fun <T : Any> refreshNavEntryDecorator() = NavEntryDecorator<T> { entry ->
	val slot = remember { RefreshSlot() }
	DisposableEffect(slot) {
		refreshSlots += slot
		onDispose { refreshSlots -= slot }
	}
	CompositionLocalProvider(LocalRefreshSlot provides slot) { entry.Content() }
}

/** The screen's slot when its top bar shows a refresh button: it can refresh, with a mouse. */
@Composable
fun refreshButtonSlot(): RefreshSlot? =
	LocalRefreshSlot.current?.takeIf { it.refresh != null && LocalMouseInUse.current }

/** The refresh button's icon, turning while the screen refreshes. */
@Composable
fun RefreshIcon(slot: RefreshSlot) {
	val angle = remember { Animatable(0f) }
	LaunchedEffect(slot.isRefreshing) {
		if (slot.isRefreshing) while (true) {
			angle.animateTo(angle.value + 360f, tween(1000, easing = LinearEasing))
		}
		else angle.animateTo(ceil(angle.value / 360f) * 360f) // finish the turn
	}
	Icon(
		Icons.Outlined.Refresh,
		contentDescription = stringResource(Res.string.action_refresh),
		modifier = Modifier.graphicsLayer { rotationZ = angle.value }
	)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PullToRefreshBox(
	isRefreshing: Boolean,
	onRefresh: () -> Unit,
	modifier: Modifier = Modifier,
	state: PullToRefreshState = rememberPullToRefreshState(),
	content: @Composable BoxScope.() -> Unit
) {
	LocalRefreshSlot.current?.let { slot ->
		val currentOnRefresh by rememberUpdatedState(onRefresh)
		DisposableEffect(slot) {
			slot.refresh = { currentOnRefresh() }
			onDispose { slot.refresh = null }
		}
		SideEffect { slot.isRefreshing = isRefreshing }
	}
	M3PullToRefreshBox(
		modifier = modifier,
		state = state,
		isRefreshing = isRefreshing,
		onRefresh = onRefresh,
		indicator = {
			Box(
				Modifier.align(Alignment.TopCenter).graphicsLayer {
					val scaleFraction = if (isRefreshing) 1f
					else LinearOutSlowInEasing.transform(state.distanceFraction).coerceIn(0f, 1f)
					scaleX = scaleFraction
					scaleY = scaleFraction
				}
			) {
				PullToRefreshDefaults.LoadingIndicator(state = state, isRefreshing = isRefreshing)
			}
		},
		content = content
	)
}

@Composable
fun PullToRefreshBox(
	onRefresh: () -> Unit,
	finished: Boolean,
	modifier: Modifier = Modifier,
	key: Any? = finished,
	state: PullToRefreshState = rememberPullToRefreshState(),
	content: @Composable BoxScope.() -> Unit
) {
	var isRefreshing by remember { mutableStateOf(false) }

	LaunchedEffect(key) {
		if (finished) {
			isRefreshing = false
		}
	}

	PullToRefreshBox(
		isRefreshing = isRefreshing,
		onRefresh = {
			isRefreshing = true
			onRefresh()
		},
		modifier = modifier,
		state = state,
		content = content
	)
}
