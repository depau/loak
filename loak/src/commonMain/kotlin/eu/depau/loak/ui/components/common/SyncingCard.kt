package eu.depau.loak.ui.components.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.manager.SyncState
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_library_still_syncing
import eu.depau.loak.generated.resources.title_syncing_library
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The library pull's progress, for the first sync after logging in. */
@Composable
fun SyncingCard(sync: SyncState, modifier: Modifier = Modifier) {
	Surface(
		modifier = modifier.fillMaxWidth(),
		shape = MaterialTheme.shapes.large,
		color = MaterialTheme.colorScheme.secondaryContainer
	) {
		Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text(
				stringResource(Res.string.title_syncing_library),
				style = MaterialTheme.typography.titleMedium
			)
			Text(stringResource(sync.message), style = MaterialTheme.typography.bodyMedium)
			LinearProgressIndicator(progress = { sync.progress }, modifier = Modifier.fillMaxWidth())
		}
	}
}

/** An empty library list's [label], unless the first sync is still filling it. */
@Composable
fun libraryEmptyLabel(label: String): String {
	val sync by koinInject<SyncManager>().syncState.collectAsStateWithLifecycle()
	return if (sync.isSyncing && sync.initial) {
		stringResource(Res.string.info_library_still_syncing)
	} else label
}
