package eu.depau.loak.ui.screens.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_close_queue
import eu.depau.loak.generated.resources.action_queue
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.ui.util.LocalWindowChrome
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
fun QueuePane(onClose: () -> Unit, modifier: Modifier = Modifier) {
	// window controls on the right: start below the title bar row they sit in
	val chrome = LocalWindowChrome.current?.takeUnless { it.controlsOnLeft }
	Surface(
		modifier = modifier
			.windowInsetsPadding(WindowInsets.systemBars)
			.padding(top = chrome?.barHeight ?: 8.dp, end = 16.dp, bottom = 16.dp)
			.width(360.dp)
			.fillMaxHeight(),
		shape = ContinuousRoundedRectangle(28.dp),
		color = MaterialTheme.colorScheme.surfaceContainer
	) {
		Column {
			Row(
				modifier = Modifier.fillMaxWidth().height(64.dp).padding(start = 24.dp, end = 8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					stringResource(Res.string.action_queue),
					style = MaterialTheme.typography.titleLarge,
					modifier = Modifier.weight(1f)
				)
				IconButton(onClick = onClose) {
					Icon(Icons.Outlined.Close, stringResource(Res.string.action_close_queue))
				}
			}
			QueueScreen(pane = true)
		}
	}
}
