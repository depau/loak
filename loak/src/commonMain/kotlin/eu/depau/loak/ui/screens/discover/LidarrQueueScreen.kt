package eu.depau.loak.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.manager.LidarrManager
import eu.depau.loak.domain.manager.LidarrQueueItem
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_left
import eu.depau.loak.generated.resources.info_nothing_downloading
import eu.depau.loak.generated.resources.option_download_queue
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.util.toHoursMinutesSeconds
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** What Lidarr is downloading, polled while the screen is open. */
@Composable
fun LidarrQueueScreen() {
	val lidarr = koinInject<LidarrManager>()
	val catalog by lidarr.catalog.collectAsState()
	DisposableEffect(Unit) {
		lidarr.startQueuePolling()
		onDispose { lidarr.stopQueuePolling() }
	}

	Scaffold(topBar = { NestedTopBar(title = { Text(stringResource(Res.string.option_download_queue)) }) }) { padding ->
		if (catalog.queue.isEmpty()) ContentUnavailable(
			modifier = Modifier.fillMaxSize().padding(padding),
			icon = Icons.Outlined.Download,
			label = stringResource(Res.string.info_nothing_downloading)
		)
		else LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
			items(catalog.queue, key = { it.id }) { QueueItem(it) }
		}
	}
}

/** "03:12 left" until [LidarrQueueItem.eta] (an absolute ISO instant); null when unknown or past. */
@Composable
internal fun timeLeftOf(item: LidarrQueueItem): String? = item.eta
	?.let { runCatching { Instant.parse(it) - Clock.System.now() }.getOrNull() }
	?.takeIf { it > Duration.ZERO }
	?.let { stringResource(Res.string.info_left, it.toHoursMinutesSeconds()) }

@Composable
private fun QueueItem(item: LidarrQueueItem) {
	val left = timeLeftOf(item)
	ListItem(
		headlineContent = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
		supportingContent = {
			Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
				Text(
					listOfNotNull(item.artistName, item.quality, left).joinToString(" · "),
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
				LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
			}
		},
		trailingContent = { Text("${(item.progress * 100).toInt()}%") }
	)
}
