package eu.depau.loak.ui.screens.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import eu.depau.loak.di.LocalQueuePaneOpen
import eu.depau.loak.generated.resources.action_hide_queue
import eu.depau.loak.generated.resources.action_show_queue
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.RightPanel
import eu.depau.loak.icons.outlined.RightPanel
import eu.depau.loak.ui.components.common.TooltipBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_queue
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.ui.util.LocalWindowChrome
import eu.depau.loak.ui.util.PaneWindowControls
import eu.depau.loak.ui.util.windowDragArea
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/** Window width below which the queue pane would leave the content too little room. */
private val QueuePaneMinWindowWidth = 1080.dp

/** Whether the window is wide enough for the queue pane next to the content. */
@Composable
fun queuePaneFits(): Boolean =
	with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >=
		QueuePaneMinWindowWidth

/** The queue beside the content on expanded windows, on its own surface. */
@Composable
fun QueuePane(modifier: Modifier = Modifier) {
	val chrome = LocalWindowChrome.current?.takeIf { it.controlsInPane }
	// Windows: the caption buttons stay flush in the window corner, so the pane docks there
	val docked = chrome?.controlsInCorner == true
	Surface(
		modifier = modifier
			.windowInsetsPadding(WindowInsets.systemBars)
			.then(
				if (docked) Modifier
				else Modifier.padding(top = 8.dp, end = 16.dp, bottom = 16.dp)
			)
			.width(360.dp)
			.fillMaxHeight(),
		shape = if (docked) ContinuousRoundedRectangle(topStart = 28.dp, bottomStart = 28.dp)
		else ContinuousRoundedRectangle(28.dp),
		color = MaterialTheme.colorScheme.surfaceContainer
	) {
		Column {
			// with the window controls on this side, this row is the title bar: it holds them
			// and moves the window, centred on the top bars' row
			Box(
				Modifier
					.fillMaxWidth()
					.height(if (docked) chrome.barHeight else 48.dp)
					.windowDragArea()
			) {
				Text(
					stringResource(Res.string.action_queue),
					style = MaterialTheme.typography.titleLarge,
					modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
				)
				PaneWindowControls(
					Modifier
						.align(if (docked) Alignment.TopEnd else Alignment.CenterEnd)
						.padding(end = if (docked) 0.dp else 8.dp)
				)
			}
			QueueScreen()
		}
	}
}

/** Shows or hides the [QueuePane], for the top bars; nothing where the pane can't fit. */
@Composable
fun QueuePaneToggle() {
	if (!queuePaneFits()) return
	var open by LocalQueuePaneOpen.current
	val label = stringResource(if (open) Res.string.action_hide_queue else Res.string.action_show_queue)
	TooltipBox(label) {
		IconButton(onClick = { open = !open }) {
			Icon(if (open) Icons.Filled.RightPanel else Icons.Outlined.RightPanel, label)
		}
	}
}
