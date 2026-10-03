package eu.depau.loak.ui.components.layouts

import eu.depau.loak.ui.util.verticalWheelToParent
import eu.depau.loak.ui.util.pageBy
import eu.depau.loak.ui.util.HorizontalScrollArrows
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation3.runtime.NavKey
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_see_all
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.ui.core.UiState

fun <T> LazyGridScope.horizontalSection(
	seeAll: Boolean,
	title: StringResource,
	destination: NavKey,
	state: UiState<List<T>>,
	key: (T) -> Any,
	itemContent: @Composable LazyItemScope.(T) -> Unit,
) {
	// ponytail: limit preview carousel to 30 items to keep layout nodes and Skiko memory minimal
	val data = state.data.orEmpty().let { if (seeAll) it.take(30) else it }

	if (data.isEmpty() && state !is UiState.Loading) return

	header(title, destination = destination, active = seeAll)

	item(span = { GridItemSpan(maxLineSpan) }) {
		val rowState = rememberLazyListState()
		HorizontalScrollArrows(
			canScrollBackward = rowState.canScrollBackward,
			canScrollForward = rowState.canScrollForward,
			onBackward = { rowState.pageBy(-1) },
			onForward = { rowState.pageBy(1) }
		) {
			LazyRow(
				modifier = Modifier.verticalWheelToParent(),
				state = rowState,
				horizontalArrangement = Arrangement.spacedBy(12.dp),
				contentPadding = PaddingValues(horizontal = 16.dp)
			) {
				if (state is UiState.Loading && data.isEmpty()) {
					items(8) {
						ArtGridPlaceholder(Modifier.width(150.dp))
					}
				} else {
					items(data, key = key) { item ->
						itemContent(item)
					}
				}
			}
		}
	}
}

fun LazyGridScope.header(
	title: StringResource,
	vararg formatArgs: Any,
	destination: NavKey,
	active: Boolean
) {
	// one full-width row, so it works whatever the grid's column count
	item(span = { GridItemSpan(maxLineSpan) }) {
		Row(verticalAlignment = Alignment.Bottom) {
			Text(
				stringResource(title, formatArgs),
				style = MaterialTheme.typography.titleMediumEmphasized,
				fontWeight = FontWeight(600),
				modifier = Modifier
					.weight(1f)
					.heightIn(min = 32.dp)
					.padding(top = 12.dp, start = 16.dp)
					.semantics { heading() }
			)
			if (active) {
				val backStack = LocalNavStack.current
				Text(
					stringResource(Res.string.action_see_all),
					fontSize = 12.sp,
					color = MaterialTheme.colorScheme.primary,
					textAlign = TextAlign.Right,
					modifier = Modifier
						.heightIn(min = 32.dp)
						.padding(top = 12.dp, end = 16.dp)
						.clickable(
							interactionSource = null,
							indication = null,
							onClick = dropUnlessResumed {
								backStack.add(destination)
							}
						)
				)
			}
		}
	}
}
