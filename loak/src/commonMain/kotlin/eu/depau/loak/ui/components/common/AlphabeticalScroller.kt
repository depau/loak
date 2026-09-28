package eu.depau.loak.ui.components.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.domain.manager.PreferenceManager
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun AlphabeticalScroller(
	modifier: Modifier = Modifier,
	state: LazyGridState,
	headers: ImmutableList<Pair<String, Int>>
) {
	val preferenceManager = koinInject<PreferenceManager>()
	if (!preferenceManager.alphabeticalScroll) return
	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	val offsets = remember { mutableStateMapOf<Int, Float>() }

	var lastSelectedIndex by remember { mutableStateOf(-1) }

	fun updateSelection(yCoordinate: Float) {
		val closestEntry = offsets.entries
			.minByOrNull { abs(it.value - yCoordinate) } ?: return
		val newIndex = closestEntry.key
		if (newIndex != lastSelectedIndex) {
			lastSelectedIndex = newIndex
			haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
			scope.launch {
				state.scrollToItem(headers[newIndex].second)
			}
		}
	}

	// stop above the mini player and nav bar, or the last letters can't be reached
	val bottomBarHeight = LocalBottomBarScrollManager.current.barHeights.values.maxOrNull() ?: 0.dp
	Box(modifier = modifier.fillMaxHeight().padding(bottom = bottomBarHeight)) {
		Column(
			verticalArrangement = Arrangement.Center,
			modifier = Modifier
				.fillMaxHeight()
				.pointerInput(headers) {
					// from the first touch, not after a drag starts, so a press alone shows the bubble
					awaitEachGesture {
						val down = awaitFirstDown()
						updateSelection(down.position.y)
						do {
							val event = awaitPointerEvent()
							event.changes.forEach { change ->
								if (change.pressed) {
									updateSelection(change.position.y)
									change.consume()
								}
							}
						} while (event.changes.any { it.pressed })
						lastSelectedIndex = -1
					}
				}
		) {
			// 28dp each, shrunk to an equal share of the height when they don't all fit
			headers.forEachIndexed { i, (letter, _) ->
				Box(
					modifier = Modifier.width(20.dp).weight(1f, fill = false).height(28.dp)
						.onGloballyPositioned {
							offsets[i] = it.positionInParent().y + (it.size.height / 2f)
						},
					contentAlignment = Alignment.Center
				) {
					Text(
						text = letter,
						textAlign = TextAlign.Center,
						style = MaterialTheme.typography.labelMedium,
						color = MaterialTheme.colorScheme.secondary
					)
				}
			}
		}

		// the letter being scrolled to, beside the finger, while the bar is held
		val selected = lastSelectedIndex
		val bubbleY = offsets[selected]
		if (selected in headers.indices && bubbleY != null) {
			val bubbleSize = 56.dp
			Surface(
				modifier = Modifier
					.size(bubbleSize)
					.offset {
						IntOffset(
							x = -(bubbleSize + 16.dp).roundToPx(),
							y = (bubbleY - (bubbleSize / 2).toPx()).roundToInt()
						)
					},
				shape = CircleShape,
				color = MaterialTheme.colorScheme.primaryContainer,
				contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
				shadowElevation = 4.dp
			) {
				Box(contentAlignment = Alignment.Center) {
					Text(headers[selected].first, style = MaterialTheme.typography.headlineMedium)
				}
			}
		}
	}
}
