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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
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
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.theme.LoakTheme
import eu.depau.loak.ui.util.LocalWindowChrome
import eu.depau.loak.ui.util.rememberColorSchemeForCurrentSong
import eu.depau.loak.ui.util.rememberScreenCornerRadius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
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

	/**
	 * The Up next / Lyrics sheet under the player (compact layouts): 0 = peeking, .5 = split
	 * (when [splitAvailable]), 1 = covering the player, which shrinks into its header.
	 */
	var queue by mutableFloatStateOf(0f)
		private set
	private var queueJob: Job? = null
	/** The sheet shows the lyrics rather than the queue; the side pane's tab follows it too. */
	var lyricsTab by mutableStateOf(false)
	/** The lyrics, full screen. */
	var karaoke by mutableStateOf(false)
	/** Set by the player's layout: whether there's a sheet, a split stop, and how far the sheet travels. */
	var sheetEnabled = false
	var splitAvailable = false
	var queueTravel = 1000f
	// state: [queueFraction] only reads [scrub] once this is set, so the layout must see it flip
	private var backOnQueue by mutableStateOf(false)
	private var dragOnQueue: Boolean? = null

	/** How far up the sheet looks, a held back gesture included. */
	val queueFraction get() = if (backOnQueue) queue * (1f - .55f * FastOutSlowInEasing.transform(scrub)) else queue

	/** How open the player looks, a held back gesture included. */
	val fraction get() = if (backOnQueue) expand else expand * (1f - BackScrub * FastOutSlowInEasing.transform(scrub))

	/** Whether the player is drawn at all; the mini player hides its cover meanwhile. */
	val isVisible get() = expand > 0f

	val isOpen get() = target == 1f

	/** Distance the finger travels for a full open: from the pill's top to the window's top. */
	private val travel get() = pill.top.takeIf { it > 1f } ?: 1000f

	fun dragBy(deltaPx: Float) {
		job?.cancel()
		expand = (expand - deltaPx / travel).coerceIn(0f, 1f)
	}

	/**
	 * A drag on the open player: up raises the sheet, down lowers it and, once it's down,
	 * closes the player. The first movement decides which, for the whole drag.
	 */
	fun dragPlayer(deltaPx: Float) {
		val onQueue = dragOnQueue ?: (sheetEnabled && expand >= 1f && (queue > 0f || deltaPx < 0f)).also { dragOnQueue = it }
		if (onQueue) dragQueue(deltaPx) else dragBy(deltaPx)
	}

	fun settlePlayer(velocityPx: Float) {
		if (dragOnQueue == true) settleQueue(velocityPx) else settle(velocityPx)
		dragOnQueue = null
	}

	fun dragQueue(deltaPx: Float) {
		queueJob?.cancel()
		queue = (queue - deltaPx / queueTravel).coerceIn(0f, 1f)
	}

	/** Ends a drag of the sheet: a fling goes on to the next stop that way, otherwise the nearest one. */
	fun settleQueue(velocityPx: Float) {
		val stops = if (splitAvailable) listOf(0f, .5f, 1f) else listOf(0f, 1f)
		val to = when {
			velocityPx < -FlingVelocity -> stops.firstOrNull { it > queue + .01f } ?: 1f
			velocityPx > FlingVelocity -> stops.lastOrNull { it < queue - .01f } ?: 0f
			else -> stops.minBy { abs(it - queue) }
		}
		runQueue(to, velocityPx)
	}

	fun showSheet(lyrics: Boolean) {
		lyricsTab = lyrics
		if (sheetEnabled) runQueue(1f, 0f)
	}

	fun hideSheet() = runQueue(0f, 0f)

	private fun runQueue(to: Float, velocityPx: Float) {
		queueJob?.cancel()
		queueJob = scope.launch {
			animate(
				initialValue = queue,
				targetValue = to,
				initialVelocity = -velocityPx / queueTravel,
				animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
			) { v, _ -> queue = v.coerceIn(0f, 1f) }
		}
	}

	/** Hands the sheet's list scrolling over to the sheet: up raises it first, down lowers it once the list is at its top. */
	val sheetScroll = object : NestedScrollConnection {
		override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
			if (available.y < 0f && queue < 1f && source == NestedScrollSource.UserInput) {
				dragQueue(available.y)
				return Offset(0f, available.y)
			}
			return Offset.Zero
		}

		override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
			if (available.y > 0f && source == NestedScrollSource.UserInput) {
				dragQueue(available.y)
				return Offset(0f, available.y)
			}
			return Offset.Zero
		}

		override suspend fun onPreFling(available: Velocity): Velocity {
			if (queue > 0f && queue < 1f) {
				settleQueue(available.y)
				return available
			}
			return Velocity.Zero
		}
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
		backStack.removeAll { it is Screen.NowPlaying }
		karaoke = false
		queueJob?.cancel()
		queue = 0f
		run(0f, velocityPx)
	}

	/** Follows `Screen.NowPlaying` being added or removed elsewhere. */
	internal fun sync(open: Boolean) {
		val t = if (open) 1f else 0f
		if (t != target) run(t, 0f)
	}

	/** Back closes the sheet first, then the player. */
	internal fun backProgress(progress: Float, fromRight: Boolean) {
		if (scrub == 0f) backOnQueue = queue > 0f
		scrub = progress
		if (!backOnQueue) squeeze = progress
		backFromRight = fromRight
	}

	/** The back gesture let go: carry on from where it left the sheet or the player. */
	internal fun backCompleted() {
		if (backOnQueue || (scrub == 0f && queue > 0f)) {
			queue = queueFraction
			scrub = 0f
			backOnQueue = false
			hideSheet()
			return
		}
		expand = fraction
		scrub = 0f
		scope.launch { animate(squeeze, 0f, animationSpec = tween(350)) { v, _ -> squeeze = v } }
		close()
	}

	internal fun backCancelled() {
		scope.launch {
			animate(scrub, 0f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) { v, _ ->
				scrub = v
				if (!backOnQueue) squeeze = v
			}
			backOnQueue = false
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
		/** px/s: about Material's 125 dp/s fling threshold on common densities. */
		const val FlingVelocity = 400f
	}
}

val LocalPlayerSheet = staticCompositionLocalOf<PlayerSheetState> { error("No PlayerSheetState provided") }

/** Reports a composable as the mini player the open player grows out of. */
fun Modifier.playerPill(state: PlayerSheetState, corner: Dp, color: Color) = onGloballyPositioned {
	state.pill = it.unclippedBounds()
	state.pillCorner = corner
	state.pillColor = color
}

/** The mini player's cover: the morph starts there, and it hides while the player is out. */
fun Modifier.playerPillArt(state: PlayerSheetState) =
	onGloballyPositioned { state.pillArt = it.unclippedBounds() }
		.graphicsLayer { alpha = if (state.isVisible) 0f else 1f }

/** The open player's cover: the morph ends there, and it shows once the player is fully open. */
fun Modifier.playerArt(state: PlayerSheetState) =
	onGloballyPositioned { state.art = it.unclippedBounds() }
		.graphicsLayer { alpha = if (state.fraction >= 1f) 1f else 0f }

/** Bounds in root coordinates, not clipped by the ancestors (the cover can sit outside the growing surface). */
private fun LayoutCoordinates.unclippedBounds() = Rect(positionInRoot(), size.toSize())

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
		isBackEnabled = state.isOpen && !state.karaoke && backStack.lastOrNull() is Screen.NowPlaying,
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
	val drag = rememberDraggableState { state.dragPlayer(it) }
	// The full-window dismiss gesture would compete with native title-bar dragging on desktop.
	val playerDragEnabled = LocalWindowChrome.current == null

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
			// no Surface draws the player, so nothing else sets its text and icon colour
			CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
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
						enabled = playerDragEnabled,
						onDragStopped = { velocity -> state.settlePlayer(velocity) }
					)
			) {
				// the open player at full size, seen through the growing surface
				Box(
					Modifier
						.layout { measurable, _ ->
							val placeable = measurable.measure(Constraints.fixed(fullW.coerceAtLeast(0), fullH.coerceAtLeast(0)))
							layout(rect.width.toInt().coerceAtLeast(0), rect.height.toInt().coerceAtLeast(0)) {
								placeable.place(-rect.left.toInt(), -rect.top.toInt())
							}
						}
						.graphicsLayer { alpha = seg(state.fraction, .45f, 1f) }
				) {
					NowPlayingScreen()
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
			// karaoke covers everything, the player included, in the song's colours
			Karaoke(state)
			}
		}
	}
}
