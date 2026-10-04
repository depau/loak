package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.data.database.entities.NetworkStatsEntity
import eu.depau.loak.data.database.entities.SyncRunEntity
import eu.depau.loak.data.database.entities.TransferCategory
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_no_data
import eu.depau.loak.generated.resources.info_sync_failed
import eu.depau.loak.generated.resources.info_sync_run
import eu.depau.loak.generated.resources.info_sync_succeeded
import eu.depau.loak.generated.resources.info_transfer_split
import eu.depau.loak.generated.resources.label_transfer_api
import eu.depau.loak.generated.resources.label_transfer_cache_hit
import eu.depau.loak.generated.resources.label_transfer_download
import eu.depau.loak.generated.resources.label_transfer_image
import eu.depau.loak.generated.resources.label_transfer_stream
import eu.depau.loak.generated.resources.title_last_24h
import eu.depau.loak.generated.resources.title_last_7d
import eu.depau.loak.generated.resources.title_network_stats
import eu.depau.loak.generated.resources.title_per_hour
import eu.depau.loak.generated.resources.title_sync_runs
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.viewmodels.NetworkStatsViewModel
import eu.depau.loak.ui.util.timeAgo
import eu.depau.loak.util.toFileSize
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

@Composable
fun SettingsNetworkStatsScreen() {
	val viewModel = koinViewModel<NetworkStatsViewModel>()
	val state by viewModel.state.collectAsStateWithLifecycle()
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_network_stats)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				}
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				val s = state ?: return@Column
				val day = s.week.filter { it.hour >= s.lastDayStart }
				TotalsGroup(stringResource(Res.string.title_last_24h), day)
				TotalsGroup(stringResource(Res.string.title_last_7d), s.week)
				HourlyGroup(day)
				SyncRunsGroup(s.syncRuns)
			}
		}
	}
}

@Composable
private fun TotalsGroup(title: String, rows: List<NetworkStatsEntity>) {
	val byCategory = rows.groupBy { it.category }.entries.sortedBy { it.key }
	SettingsGroup(title = { Text(title) }) {
		if (byCategory.isEmpty()) NoData()
		byCategory.forEachIndexed { index, (category, categoryRows) ->
			SegmentedListItem(
				onClick = {},
				content = { Text(category.label()) },
				supportingContent = { Text(categoryRows.summary()) },
				shapes = SegmentedListItemDefaults.segmentedShapes(index, byCategory.size)
			)
		}
	}
}

@Composable
private fun HourlyGroup(rows: List<NetworkStatsEntity>) {
	val byHour = rows.groupBy { it.hour }.entries.sortedByDescending { it.key }
	val max = byHour.maxOfOrNull { (_, hourRows) -> hourRows.sumOf { it.bytes } }
		?.coerceAtLeast(1) ?: 1
	SettingsGroup(title = { Text(stringResource(Res.string.title_per_hour)) }) {
		if (byHour.isEmpty()) NoData()
		byHour.forEachIndexed { index, (hour, hourRows) ->
			SegmentedListItem(
				onClick = {},
				content = { Text(Instant.fromEpochMilliseconds(hour).timeAgo()) },
				supportingContent = {
					Column(Modifier.fillMaxWidth()) {
						Text(hourRows.summary())
						LinearProgressIndicator(
							progress = { hourRows.sumOf { it.bytes }.toFloat() / max },
							modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
						)
					}
				},
				shapes = SegmentedListItemDefaults.segmentedShapes(index, byHour.size)
			)
		}
	}
}

@Composable
private fun SyncRunsGroup(runs: List<SyncRunEntity>) {
	SettingsGroup(title = { Text(stringResource(Res.string.title_sync_runs)) }) {
		if (runs.isEmpty()) NoData()
		runs.forEachIndexed { index, run ->
			SegmentedListItem(
				onClick = {},
				content = { Text(run.start.timeAgo()) },
				supportingContent = {
					Text(
						stringResource(
							Res.string.info_sync_run,
							stringResource(
								if (run.ok) Res.string.info_sync_succeeded
								else Res.string.info_sync_failed
							),
							run.duration.inWholeSeconds,
							run.items
						)
					)
				},
				shapes = SegmentedListItemDefaults.segmentedShapes(index, runs.size)
			)
		}
	}
}

@Composable
private fun NoData() {
	SegmentedListItem(
		onClick = {},
		content = { Text(stringResource(Res.string.info_no_data)) },
		shapes = SegmentedListItemDefaults.segmentedShapes(0, 1)
	)
}

@Composable
private fun List<NetworkStatsEntity>.summary(): String {
	val (metered, unmetered) = partition { it.metered }
	return stringResource(
		Res.string.info_transfer_split,
		metered.sumOf { it.bytes }.toFileSize(),
		unmetered.sumOf { it.bytes }.toFileSize(),
		sumOf { it.requests }
	)
}

@Composable
private fun TransferCategory.label(): String = stringResource(
	when (this) {
		TransferCategory.STREAM -> Res.string.label_transfer_stream
		TransferCategory.DOWNLOAD -> Res.string.label_transfer_download
		TransferCategory.API -> Res.string.label_transfer_api
		TransferCategory.IMAGE -> Res.string.label_transfer_image
		TransferCategory.CACHE_HIT -> Res.string.label_transfer_cache_hit
	}
)
