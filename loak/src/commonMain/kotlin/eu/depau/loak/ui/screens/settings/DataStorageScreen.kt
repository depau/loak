package eu.depau.loak.ui.screens.settings

import eu.depau.loak.util.IoDispatcher
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.MutableState
import eu.depau.loak.generated.resources.title_downloads_storage
import eu.depau.loak.generated.resources.title_on_this_device
import eu.depau.loak.generated.resources.subtitle_download_over_cellular
import eu.depau.loak.generated.resources.option_check_new_songs
import eu.depau.loak.generated.resources.action_check_now
import eu.depau.loak.generated.resources.title_going_offline
import eu.depau.loak.generated.resources.option_go_offline_auto
import eu.depau.loak.generated.resources.info_offline_mode_on
import eu.depau.loak.generated.resources.title_cache

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_cancel_download
import eu.depau.loak.generated.resources.count_songs
import eu.depau.loak.generated.resources.info_library_download
import eu.depau.loak.generated.resources.info_library_download_warning
import eu.depau.loak.generated.resources.info_not_available_offline
import eu.depau.loak.generated.resources.info_progress
import eu.depau.loak.generated.resources.info_status_calculating
import eu.depau.loak.generated.resources.info_status_downloading
import eu.depau.loak.generated.resources.option_cover_art_quality
import eu.depau.loak.generated.resources.option_downloaded_songs
import eu.depau.loak.generated.resources.pref_download_over_cellular
import eu.depau.loak.generated.resources.pref_download_over_roaming
import eu.depau.loak.generated.resources.pref_download_only_while_charging
import eu.depau.loak.generated.resources.pref_download_only_while_charging_desc
import eu.depau.loak.generated.resources.pref_download_refresh_now_desc
import eu.depau.loak.generated.resources.option_image_cache_size
import eu.depau.loak.generated.resources.title_downloads_settings
import eu.depau.loak.generated.resources.title_library_download
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.manager.DownloadManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import eu.depau.loak.util.toFileSize
import eu.depau.loak.generated.resources.info_audio_storage_usage
import eu.depau.loak.generated.resources.option_audio_cache
import eu.depau.loak.generated.resources.option_audio_cache_max_size
import eu.depau.loak.generated.resources.option_audio_storage
import eu.depau.loak.generated.resources.subtitle_audio_cache
import eu.depau.loak.domain.models.settings.CoverArtQuality
import eu.depau.loak.domain.models.settings.OfflineMode
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Offline
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.dialogs.BulkDownloadDialog
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsChoiceItem
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.viewmodels.SettingsDataStorageViewModel

/** What gets downloaded and when, going offline on its own, and the streaming cache. */
@Composable
fun SettingsDataStorageScreen() {
	val viewModel = koinViewModel<SettingsDataStorageViewModel>()

	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val preferenceManager = koinInject<PreferenceManager>()
	val downloadManager = koinInject<DownloadManager>()
	val scope = rememberCoroutineScope()
	val imageLoader = koinInject<ImageLoader>()

	val downloadCount by viewModel.downloadCount.collectAsStateWithLifecycle(0)
	val downloadSize by viewModel.downloadSize.collectAsStateWithLifecycle(0L)

	var showLibraryDownloadDialog by remember { mutableStateOf(false) }
	val isDownloadingLibrary by viewModel.isDownloadingLibrary.collectAsStateWithLifecycle()
	val libraryDownloadProgress by viewModel.libraryDownloadProgress.collectAsStateWithLifecycle()

	val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
	val audioStoreUsage by viewModel.audioStoreUsage.collectAsStateWithLifecycle()
	var imageCacheSize by rememberImageCacheSize()

	val smoothLibraryDownloadProgress by animateFloatAsState(
		targetValue = libraryDownloadProgress.coerceIn(0f, 1f),
		animationSpec = tween(durationMillis = 500, easing = EaseOut)
	)

	BulkDownloadDialog(
		title = stringResource(Res.string.title_library_download),
		message = stringResource(Res.string.info_library_download_warning),
		showDialog = showLibraryDownloadDialog,
		onDismissRequest = { showLibraryDownloadDialog = false },
		onConfirm = {
			showLibraryDownloadDialog = false
			viewModel.downloadEntireLibrary()
		}
	)

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_downloads_storage)) },
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
				SettingsGroup(title = { Text(stringResource(Res.string.title_on_this_device)) }) {
					// the song store counts downloads too; without it, just the downloads
					if (viewModel.audioStoreAvailable) {
						SegmentedListItem(
							onClick = {},
							content = { Text(stringResource(Res.string.option_audio_storage)) },
							supportingContent = {
								val cache = pluralStringResource(
									Res.plurals.count_songs, audioStoreUsage.cacheCount, audioStoreUsage.cacheCount
								) + " · " + audioStoreUsage.cacheBytes.toFileSize()
								val pinned = pluralStringResource(
									Res.plurals.count_songs, audioStoreUsage.pinnedCount, audioStoreUsage.pinnedCount
								) + " · " + audioStoreUsage.pinnedBytes.toFileSize()
								Text(stringResource(Res.string.info_audio_storage_usage, cache, pinned))
							},
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
						)
					} else {
						SegmentedListItem(
							onClick = {},
							content = { Text(stringResource(Res.string.option_downloaded_songs)) },
							supportingContent = {
								Text(
									pluralStringResource(Res.plurals.count_songs, downloadCount, downloadCount)
										+ " · " + downloadSize.toFileSize()
								)
							},
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
						)
					}
					SegmentedListItem(
						onClick = {},
						content = { Text(stringResource(Res.string.option_image_cache_size)) },
						supportingContent = { Text(imageCacheSize) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_downloads_settings)) }) {
					val overCellular by downloadManager.overCellular.collectAsStateWithLifecycle()
					val overRoaming by downloadManager.overRoaming.collectAsStateWithLifecycle()
					val whileCharging by downloadManager.whileCharging.collectAsStateWithLifecycle()
					DownloadQualityItem(SegmentedListItemDefaults.segmentedShapes(index = 0, count = 6))
					SettingsToggleItem(
						checked = whileCharging,
						onCheckedChange = downloadManager::setWhileCharging,
						content = { Text(stringResource(Res.string.pref_download_only_while_charging)) },
						supportingContent = { Text(stringResource(Res.string.pref_download_only_while_charging_desc)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 6)
					)
					SettingsToggleItem(
						checked = overCellular,
						onCheckedChange = downloadManager::setOverCellular,
						content = { Text(stringResource(Res.string.pref_download_over_cellular)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_download_over_cellular)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 6)
					)
					SettingsToggleItem(
						checked = overCellular && overRoaming,
						enabled = overCellular,
						onCheckedChange = downloadManager::setOverRoaming,
						content = { Text(stringResource(Res.string.pref_download_over_roaming)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 6)
					)
					SegmentedListItem(
						onClick = { downloadManager.kickAll() },
						enabled = isOnline,
						content = { Text(stringResource(Res.string.option_check_new_songs)) },
						supportingContent = { Text(stringResource(Res.string.pref_download_refresh_now_desc)) },
						trailingContent = {
							if (isOnline) {
								FilledTonalButton(onClick = { downloadManager.kickAll() }) {
									Text(stringResource(Res.string.action_check_now))
								}
							} else {
								OfflineIcon()
							}
						},
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 4, count = 6)
					)
					LibraryDownloadItem(
						isOnline = isOnline,
						isDownloading = isDownloadingLibrary,
						progress = smoothLibraryDownloadProgress,
						onClick = { if (!isDownloadingLibrary) showLibraryDownloadDialog = true },
						onCancel = viewModel::cancelLibraryDownload,
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 5, count = 6)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_going_offline)) }) {
					val forced = preferenceManager.offlineMode == OfflineMode.Forced
					SettingsChoiceItem(
						// Forced is the toggle on the settings list
						choices = listOf(OfflineMode.Auto, OfflineMode.NoWiFi).toImmutableList(),
						selectedChoice = preferenceManager.offlineMode,
						onChoiceSelected = { preferenceManager.offlineMode = it },
						content = { Text(stringResource(Res.string.option_go_offline_auto)) },
						label = {
							if (forced) stringResource(Res.string.info_offline_mode_on)
							else stringResource(it.displayName)
						},
						enabled = !forced,
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 1)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_cache)) }) {
					val cacheRows = if (viewModel.audioStoreAvailable) {
						if (preferenceManager.audioCacheEnabled) 3 else 2
					} else 1
					if (viewModel.audioStoreAvailable) {
						AudioCacheItems(
							preferenceManager = preferenceManager,
							count = cacheRows,
							onLimitsChanged = viewModel::trimAudioCache
						)
					}
					SettingsChoiceItem(
						choices = CoverArtQuality.entries.toImmutableList(),
						selectedChoice = preferenceManager.coverArtQuality,
						onChoiceSelected = {
							preferenceManager.coverArtQuality = it
							imageLoader.memoryCache?.clear()
							scope.launch(IoDispatcher) {
								imageLoader.diskCache?.clear()
								imageCacheSize = "0 MB"
							}
						},
						content = { Text(stringResource(Res.string.option_cover_art_quality)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = cacheRows - 1, count = cacheRows)
					)
				}
			}
		}
	}
}

/** The image cache's size on disk, measured once; set it after clearing the cache. */
@Composable
internal fun rememberImageCacheSize(): MutableState<String> {
	val imageLoader = koinInject<ImageLoader>()
	val calculating = stringResource(Res.string.info_status_calculating)
	val size = remember { mutableStateOf(calculating) }
	LaunchedEffect(Unit) {
		withContext(IoDispatcher) {
			val sizeBytes = imageLoader.diskCache?.size ?: 0L
			size.value = "${sizeBytes / (1024 * 1024)} MB"
		}
	}
	return size
}

/** Shown on rows that need the network while there's none. */
@Composable
internal fun OfflineIcon() {
	Icon(
		Icons.Outlined.Offline,
		stringResource(Res.string.info_not_available_offline),
		modifier = Modifier.size(20.dp)
	)
}

@Composable
private fun LibraryDownloadItem(
	isOnline: Boolean,
	isDownloading: Boolean,
	progress: Float,
	onClick: () -> Unit,
	onCancel: () -> Unit,
	shapes: ListItemShapes
) {
	SegmentedListItem(
		onClick = onClick,
		enabled = isOnline,
		content = { Text(stringResource(Res.string.title_library_download)) },
		supportingContent = {
			Column(Modifier.fillMaxWidth()) {
				Text(
					text = stringResource(
						if (isDownloading) Res.string.info_status_downloading
						else Res.string.info_library_download
					)
				)
				AnimatedVisibility(
					visible = isDownloading,
					enter = fadeIn() + expandVertically(clip = false),
					exit = fadeOut() + shrinkVertically(clip = false)
				) {
					Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = Arrangement.SpaceBetween,
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								text = stringResource(Res.string.info_progress),
								style = MaterialTheme.typography.labelMedium,
								color = MaterialTheme.colorScheme.primary
							)

							Row(verticalAlignment = Alignment.CenterVertically) {
								TextButton(
									onClick = onCancel,
									contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
									modifier = Modifier.padding(end = 8.dp)
								) {
									Text(
										stringResource(Res.string.action_cancel_download),
										style = MaterialTheme.typography.labelLarge,
										color = MaterialTheme.colorScheme.error
									)
								}

								Text(
									text = "${(progress * 100).toInt()}%",
									style = MaterialTheme.typography.labelMedium,
									color = MaterialTheme.colorScheme.primary
								)
							}
						}

						LinearProgressIndicator(
							progress = { progress },
							modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
						)
					}
				}
			}
		},
		trailingContent = { if (!isOnline) OfflineIcon() },
		shapes = shapes
	)
}

private const val MB = 1024L * 1024
private val cacheSizes = listOf(512 * MB, 1024 * MB, 2048 * MB, 5120 * MB, 10240 * MB, 20480 * MB)
	.toImmutableList()

private fun cacheSizeLabel(bytes: Long) =
	if (bytes >= 1024 * MB) "${bytes / (1024 * MB)} GB" else "${bytes / MB} MB"

/** The streaming cache's toggle and size, the first rows of a [count]-row group. */
@Composable
private fun AudioCacheItems(
	preferenceManager: PreferenceManager,
	count: Int,
	onLimitsChanged: () -> Unit
) {
	val enabled = preferenceManager.audioCacheEnabled
	SettingsToggleItem(
		checked = enabled,
		onCheckedChange = {
			preferenceManager.audioCacheEnabled = it
			onLimitsChanged()
		},
		content = { Text(stringResource(Res.string.option_audio_cache)) },
		supportingContent = { Text(stringResource(Res.string.subtitle_audio_cache)) },
		shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
	)
	if (enabled) {
		SettingsChoiceItem(
			choices = cacheSizes,
			selectedChoice = preferenceManager.audioCacheMaxBytes,
			onChoiceSelected = {
				preferenceManager.audioCacheMaxBytes = it
				onLimitsChanged()
			},
			content = { Text(stringResource(Res.string.option_audio_cache_max_size)) },
			label = { cacheSizeLabel(it) },
			shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = count)
		)
	}
}
