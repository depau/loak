package eu.depau.loak.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import coil3.ImageLoader
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_clear_audio_cache
import eu.depau.loak.generated.resources.action_clear_downloads
import eu.depau.loak.generated.resources.action_clear_image_cache
import eu.depau.loak.generated.resources.action_clear_pending_actions
import eu.depau.loak.generated.resources.action_rebuild_database
import eu.depau.loak.generated.resources.action_trigger_sync
import eu.depau.loak.generated.resources.info_sync_never
import eu.depau.loak.generated.resources.option_last_sync
import eu.depau.loak.generated.resources.subtitle_clear_pending_actions
import eu.depau.loak.generated.resources.subtitle_logs
import eu.depau.loak.generated.resources.subtitle_network_stats
import eu.depau.loak.generated.resources.subtitle_pending_actions
import eu.depau.loak.generated.resources.subtitle_rebuild_database
import eu.depau.loak.generated.resources.title_advanced
import eu.depau.loak.generated.resources.title_debugging
import eu.depau.loak.generated.resources.title_fix_problems
import eu.depau.loak.generated.resources.title_free_up_space
import eu.depau.loak.generated.resources.title_logs
import eu.depau.loak.generated.resources.title_network_stats
import eu.depau.loak.generated.resources.title_sync_control
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.ui.screens.settings.viewmodels.SettingsDataStorageViewModel
import eu.depau.loak.ui.util.timeAgo
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.toFileSize
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant

/** Rarely needed: library sync, data usage, freeing space, fixing a stuck library, logs. */
@Composable
fun SettingsAdvancedScreen() {
	val viewModel = koinViewModel<SettingsDataStorageViewModel>()
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val backStack = LocalNavStack.current
	val preferenceManager = koinInject<PreferenceManager>()
	val imageLoader = koinInject<ImageLoader>()
	val scope = rememberCoroutineScope()

	val syncState by viewModel.syncState.collectAsStateWithLifecycle()
	val pendingActionCount by viewModel.pendingActionCount.collectAsStateWithLifecycle()
	val downloadSize by viewModel.downloadSize.collectAsStateWithLifecycle(0L)
	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
	val audioStoreUsage by viewModel.audioStoreUsage.collectAsStateWithLifecycle()
	var imageCacheSize by rememberImageCacheSize()

	val smoothSyncProgress by animateFloatAsState(
		if (syncState.isSyncing) syncState.progress else 0f,
		animationSpec = tween(durationMillis = 250, easing = EaseOut)
	)

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_advanced)) },
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
				SettingsGroup(title = { Text(stringResource(Res.string.title_sync_control)) }) {
					SegmentedListItem(
						onClick = {},
						content = { Text(stringResource(Res.string.option_last_sync)) },
						supportingContent = {
							Column(Modifier.fillMaxWidth()) {
								val time = preferenceManager.lastFullSyncTime
								Text(
									if (syncState.isSyncing) stringResource(syncState.message)
									else if (time == 0L) stringResource(Res.string.info_sync_never)
									else Instant.fromEpochMilliseconds(time).timeAgo()
								)
								if (pendingActionCount > 0) {
									Text(stringResource(Res.string.subtitle_pending_actions, pendingActionCount))
								}
								AnimatedVisibility(
									syncState.isSyncing,
									enter = fadeIn() + expandVertically(clip = false),
									exit = fadeOut() + shrinkVertically(clip = false)
								) {
									LinearProgressIndicator(
										progress = { smoothSyncProgress.coerceIn(0f, 1f) },
										modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
									)
								}
							}
						},
						trailingContent = {
							if (isOnline) {
								Button(
									onClick = viewModel::triggerManualSync,
									enabled = !syncState.isSyncing
								) { Text(stringResource(Res.string.action_trigger_sync)) }
							} else {
								OfflineIcon()
							}
						},
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					SettingsNavItem(
						onClick = dropUnlessResumed { backStack.add(Screen.Settings.NetworkStats) },
						content = { Text(stringResource(Res.string.title_network_stats)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_network_stats)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_free_up_space)) }) {
					val first = if (viewModel.audioStoreAvailable) 1 else 0
					val count = first + 2
					if (viewModel.audioStoreAvailable) {
						SegmentedListItem(
							onClick = viewModel::clearAudioCache,
							content = { Text(stringResource(Res.string.action_clear_audio_cache)) },
							supportingContent = { Text(audioStoreUsage.cacheBytes.toFileSize()) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
						)
					}
					SegmentedListItem(
						onClick = {
							imageLoader.memoryCache?.clear()
							scope.launch(IoDispatcher) {
								imageLoader.diskCache?.clear()
								imageCacheSize = "0 MB"
							}
						},
						content = { Text(stringResource(Res.string.action_clear_image_cache)) },
						supportingContent = { Text(imageCacheSize) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = first, count = count)
					)
					SegmentedListItem(
						onClick = viewModel::clearAllDownloads,
						content = { Text(stringResource(Res.string.action_clear_downloads)) },
						supportingContent = { Text(downloadSize.toFileSize()) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = first + 1, count = count),
						colors = SegmentedListItemDefaults.segmentedErrorColors()
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_fix_problems)) }) {
					SegmentedListItem(
						onClick = viewModel::removeAllActions,
						content = { Text(stringResource(Res.string.action_clear_pending_actions)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_clear_pending_actions)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2),
						colors = SegmentedListItemDefaults.segmentedErrorColors()
					)
					SegmentedListItem(
						onClick = viewModel::rebuildDatabase,
						enabled = isOnline,
						content = { Text(stringResource(Res.string.action_rebuild_database)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_rebuild_database)) },
						trailingContent = { if (!isOnline) OfflineIcon() },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2),
						colors = SegmentedListItemDefaults.segmentedErrorColors()
					)
				}

				// the log viewer reads Android's logcat
				if (platformContext.platformType == PlatformType.Android) {
					SettingsGroup(title = { Text(stringResource(Res.string.title_debugging)) }) {
						SettingsNavItem(
							onClick = dropUnlessResumed { backStack.add(Screen.Settings.Logs) },
							content = { Text(stringResource(Res.string.title_logs)) },
							supportingContent = { Text(stringResource(Res.string.subtitle_logs)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
						)
					}
				}
			}
		}
	}
}
