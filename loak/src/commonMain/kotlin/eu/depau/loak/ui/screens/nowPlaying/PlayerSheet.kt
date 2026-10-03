package eu.depau.loak.ui.screens.nowPlaying

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigationevent.NavigationEvent.Companion.EDGE_RIGHT
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalSnackBarState
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.snackbars.LoakSnackBar
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.theme.LoakTheme
import eu.depau.loak.ui.util.rememberColorSchemeForCurrentSong
import eu.depau.loak.ui.util.rememberScreenCornerRadius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Where the player is between the mini player (0) and fully open (1), plus what the morph
 * between the two needs: the mini player's bounds and the cover's bounds at both ends.
 * Everything that moves reads [fraction], so a drag, a fling, a held back gesture and the
 * buttons are one and the same animation.
 *
 * `Screen.NowPlaying` in the back stack still means "open": [open] and [close] keep it in step,
 * and removing it from anywhere else (navigating away from the player) closes it.
 */
@Stable
class PlayerSheetState(private val scope: CoroutineScope, private val backStack: NavBackStack<NavKey>) {
	/** 0 = mini player, 1 = open. */
	var expand by mutableFloatStateOf(0f)
		private set
	private var target = 0f
	private var job: Job? = null

	/** A held back gesture: how far it pulls the player toward the pill (scrub) and squeezes it. */
	var scrub by mutableFloatStateOf(0f)
		private set
	var squeeze by mutableFloatStateOf(0f)
		private set
	var backFromRight by mutableStateOf(false)
		private set

	/** The mini player (or the expanded windows' player bar), in root coordinates. */
	var pill by mutableStateOf(Rect.Zero)
	var pillArt by mutableStateOf(Rect.Zero)
	var pillCorner by mutableStateOf(16.dp)
	var pillColor by mutableStateOf(Color.Unspecified)

	/** The cover in the open player, measured by it. */
	var art by mutableStateOf(Rect.Zero)

	/** How open the player looks, a held back gesture included. */
	val fraction get() = expand * (1f - BackScrub * FastOutSlowInEasing.transform(scrub))

	/** Whether the player is drawn at all; the mini player hides its cover meanwhile. */
	val isVisible get() = expand > 0f

	val isOpen get() = target == 1f

	/** Distance the finger travels for a full open: from the pill's top to the window's top. */
	private val travel get() = pill.top.takeIf { it > 1f } ?: 1000f

	fun dragBy(deltaPx: Float) {
		job?.cancel()
		expand = (expand - deltaPx / travel).coerceIn(0f, 1f)
	}

	/** Ends a drag: a fling decides, otherwise whichever end is nearer. */
	fun settle(velocityPx: Float) = when {
		velocityPx < -FlingVelocity -> open(velocityPx)
		velocityPx > FlingVelocity -> close(velocityPx)
		expand > .5f -> open(velocityPx)
		else -> close(velocityPx)
	}

	fun open(velocityPx: Float = 0f) {
		if (backStack.none { it is Screen.NowPlaying }) backStack.add(Screen.NowPlaying)
		run(1f, velocityPx)
	}

	fun close(velocityPx: Float = 0f) {
		backStack.removeAll { it is Screen.NowPlaying || it is Screen.Lyrics || it is Screen.Queue }
		run(0f, velocityPx)
	}

	/** Follows `Screen.NowPlaying` being added or removed elsewhere. */
	internal fun sync(open: Boolean) {
		val t = if (open) 1f else 0f
		if (t != target) run(t, 0f)
	}

	internal fun backProgress(progress: Float, fromRight: Boolean) {
		scrub = progress
		squeeze = progress
		backFromRight = fromRight
	}

	/** The back gesture let go: carry on from where it left the player, into the pill. */
	internal fun backCompleted() {
		expand = fraction
		scrub = 0f
		scope.launch { animate(squeeze, 0f, animationSpec = tween(350)) { v, _ -> squeeze = v } }
		close()
	}

	internal fun backCancelled() {
		scope.launch {
			animate(scrub, 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) { v, _ ->
				scrub = v
				squeeze = v
			}
		}
	}

	private fun run(to: Float, velocityPx: Float) {
		target = to
		job?.cancel()
		job = scope.launch {
			animate(
				initialValue = expand,
				targetValue = to,
				initialVelocity = -velocityPx / travel,
				animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
			) { v, _ -> expand = v.coerceIn(0f, 1f) }
		}
	}

	private companion object {
		/** How far toward the pill a full back swipe pulls the player before letting go. */
		const val BackScrub = .42f
		const val FlingVelocity = 1200f
	}
}

val LocalPlayerSheet = staticCompositionLocalOf<PlayerSheetState> { error("No PlayerSheetState provided") }

/** Reports a composable as the mini player the open player grows out of. */
fun Modifier.playerPill(state: PlayerSheetState, corner: Dp, color: Color) = onGloballyPositioned {
	state.pill = it.boundsInRoot()
	state.pillCorner = corner
	state.pillColor = color
}

/** The mini player's cover: the morph starts there, and it hides while the player is out. */
fun Modifier.playerPillArt(state: PlayerSheetState) =
	onGloballyPositioned { state.pillArt = it.boundsInRoot() }
		.graphicsLayer { alpha = if (state.isVisible) 0f else 1f }

/** The open player's cover: the morph ends there, and it shows once the player is fully open. */
fun Modifier.playerArt(state: PlayerSheetState) =
	onGloballyPositioned { state.art = it.boundsInRoot() }
		.graphicsLayer { alpha = if (state.fraction >= 1f) 1f else 0f }

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/**
 * The player, over the whole window. It grows out of the mini player as [PlayerSheetState]
 * opens: the surface from the pill's bounds to the window's, the cover from the pill's
 * thumbnail to its place in the player, the player's content fading in over the last part.
 */
@Composable
fun PlayerLayer(state: PlayerSheetState) {
	val backStack = LocalNavStack.current
	val present = backStack.any { it is Screen.NowPlaying }
	LaunchedEffect(present) { state.sync(present) }
	if (!state.isVisible) return

	val navState = rememberNavigationEventState(NavigationEventInfo.None)
	NavigationBackHandler(
		state = navState,
		isBackEnabled = state.isOpen && backStack.lastOrNull() is Screen.NowPlaying,
		onBackCompleted = state::backCompleted,
		onBackCancelled = state::backCancelled
	)
	LaunchedEffect(navState.transitionState) {
		val transition = navState.transitionState
		if (transition is NavigationEventTransitionState.InProgress) {
			state.backProgress(transition.latestEvent.progress, transition.latestEvent.swipeEdge == EDGE_RIGHT)
		}
	}

	val density = LocalDensity.current
	val screenCorner = rememberScreenCornerRadius()
	val appSurface = MaterialTheme.colorScheme.surface
	val drag = rememberDraggableState { state.dragBy(it) }

	BoxWithConstraints(Modifier.fillMaxSize()) {
		val fullW = constraints.maxWidth
		val fullH = constraints.maxHeight
		val full = Rect(0f, 0f, fullW.toFloat(), fullH.toFloat())
		val pill = state.pill.takeUnless { it.isEmpty }
			?: Rect(0f, full.bottom - with(density) { 80.dp.toPx() }, full.right, full.bottom)
		val f = state.fraction
		val rect = lerp(pill, full, f)
		val squeeze = FastOutSlowInEasing.transform(state.squeeze)
		val corner = if (f >= 1f && squeeze == 0f) 0.dp
		else maxOf(lerp(state.pillCorner, screenCorner, seg(f, 0f, .4f)), 28.dp * squeeze)

		// the app behind dims as the player rises
		Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f * f)))

		LoakTheme(rememberColorSchemeForCurrentSong()) {
			val surface = MaterialTheme.colorScheme.surface
			val from = state.pillColor.takeIf { it != Color.Unspecified } ?: appSurface
			Box(
				Modifier
					.layout { measurable, _ ->
						val placeable = measurable.measure(Constraints.fixed(rect.width.toInt().coerceAtLeast(0), rect.height.toInt().coerceAtLeast(0)))
						layout(fullW, fullH) { placeable.place(rect.left.toInt(), rect.top.toInt()) }
					}
					.graphicsLayer {
						// predictive back: shrink toward 90%, rounded, pushed away from the swiped edge
						val s = 1f - .1f * squeeze
						scaleX = s
						scaleY = s
						translationX = (if (state.backFromRight) -1f else 1f) * 8.dp.toPx() * squeeze
						shape = RoundedCornerShape(corner)
						clip = true
					}
					.background(lerp(from, surface, seg(f, .05f, .5f)))
					.draggable(
						state = drag,
						orientation = Orientation.Vertical,
						onDragStopped = { velocity -> state.settle(velocity) }
					)
			) {
				// the open player at full size, seen through the growing surface
				Box(
					Modifier
						.layout { measurable, _ ->
							val placeable = measurable.measure(Constraints.fixed(fullW, fullH))
							layout(rect.width.toInt().coerceAtLeast(0), rect.height.toInt().coerceAtLeast(0)) {
								placeable.place(-rect.left.toInt(), -rect.top.toInt())
							}
						}
						.graphicsLayer { alpha = seg(state.fraction, .45f, 1f) }
				) {
					NowPlayingScreen()
					SnackbarHost(
						hostState = LocalSnackBarState.current,
						modifier = Modifier
							.align(Alignment.BottomCenter)
							.windowInsetsPadding(WindowInsets.navigationBars)
							.padding(bottom = 16.dp)
					) { LoakSnackBar(snackBarData = it) }
				}
			}

			// the cover, travelling from the pill's thumbnail to its place in the player
			if (f < 1f && !state.art.isEmpty) {
				val player = koinInject<MediaPlayerViewModel>()
				val song = player.uiState.value.currentSong
				val from = state.pillArt.takeUnless { it.isEmpty } ?: pill
				val art = lerp(from, state.art, seg(f, 0f, .9f))
				run {
					CoverArt(
						coverArtId = song?.coverArtId,
						crossfadeMs = 0,
						shape = RoundedCornerShape(lerp(8.dp, 16.dp, f)),
						modifier = Modifier
							.layout { measurable, _ ->
								val placeable = measurable.measure(Constraints.fixed(art.width.toInt().coerceAtLeast(1), art.height.toInt().coerceAtLeast(1)))
								layout(placeable.width, placeable.height) { placeable.place(art.left.toInt(), art.top.toInt()) }
							}
					)
				}
			}
		}
	}
}
