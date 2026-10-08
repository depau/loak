package eu.depau.loak.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainPlaylistListType
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.domain.models.settings.StartupQueue
import eu.depau.loak.domain.repositories.PlaylistRepository
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_queue
import eu.depau.loak.generated.resources.info_no_playlist_selected
import eu.depau.loak.generated.resources.info_scrobble_min_duration
import eu.depau.loak.generated.resources.option_auto_fill_queue
import eu.depau.loak.generated.resources.option_enable_scrobbling
import eu.depau.loak.generated.resources.option_queue_sync
import eu.depau.loak.generated.resources.option_scrobble_percentage
import eu.depau.loak.generated.resources.option_skip_explicit
import eu.depau.loak.generated.resources.option_startup_playlist
import eu.depau.loak.generated.resources.option_startup_queue
import eu.depau.loak.generated.resources.subtitle_auto_fill_queue
import eu.depau.loak.generated.resources.subtitle_enable_scrobbling
import eu.depau.loak.generated.resources.subtitle_queue_sync
import eu.depau.loak.generated.resources.subtitle_skip_explicit
import eu.depau.loak.generated.resources.title_listening_history
import eu.depau.loak.generated.resources.title_other_devices
import eu.depau.loak.generated.resources.title_playback
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.common.displayName
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsChoiceItem
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsSliderItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** What plays, what other devices see of it, and who hears about it. */
@Composable
fun SettingsPlaybackScreen() {
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val preferenceManager = koinInject<PreferenceManager>()

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_playback)) },
				navigationAction = {
					if (!hideBack) {
						NestedTopBarDefaults.NavigationAction()
					}
				}
			)
		}
	) { innerPadding ->
		CompositionLocalProvider(
			LocalMinimumInteractiveComponentSize provides 0.dp
		) {
			Column(
				modifier = Modifier
					.padding(innerPadding)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 16.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				SettingsGroup(title = { Text(stringResource(Res.string.action_queue)) }) {
					// autoplay is Android's player only
					val autoplay = platformContext.platformType == PlatformType.Android
					val startupQueue = preferenceManager.startupQueue
					val pickPlaylist = startupQueue == StartupQueue.Playlist
					val first = if (autoplay) 1 else 0
					val count = first + 2 + (if (pickPlaylist) 1 else 0)

					if (autoplay) {
						SettingsToggleItem(
							checked = preferenceManager.autoFillQueue,
							onCheckedChange = { preferenceManager.autoFillQueue = it },
							content = { Text(stringResource(Res.string.option_auto_fill_queue)) },
							supportingContent = { Text(stringResource(Res.string.subtitle_auto_fill_queue)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
						)
					}
					SettingsToggleItem(
						checked = preferenceManager.explicitContentPlayback == ExplicitContentPlayback.Skip,
						onCheckedChange = {
							preferenceManager.explicitContentPlayback =
								if (it) ExplicitContentPlayback.Skip else ExplicitContentPlayback.Allowed
						},
						content = { Text(stringResource(Res.string.option_skip_explicit)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_skip_explicit)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = first, count = count)
					)
					SettingsChoiceItem(
						choices = StartupQueue.entries.toImmutableList(),
						selectedChoice = startupQueue,
						onChoiceSelected = { preferenceManager.startupQueue = it },
						content = { Text(stringResource(Res.string.option_startup_queue)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = first + 1, count = count)
					)
					AnimatedVisibility(visible = pickPlaylist) {
						val playlistRepository = koinInject<PlaylistRepository>()
						val playlists by remember {
							playlistRepository.getPlaylistsFlow(
								fullRefresh = false,
								listType = DomainPlaylistListType.Name,
								reversed = false,
								withSongs = false
							)
						}.collectAsState(null)
						val choices = playlists?.data.orEmpty()
						SettingsChoiceItem(
							choices = choices.toImmutableList<DomainPlaylist?>(),
							selectedChoice = choices.find { it.id == preferenceManager.startupPlaylistId },
							onChoiceSelected = { preferenceManager.startupPlaylistId = it?.id.orEmpty() },
							content = { Text(stringResource(Res.string.option_startup_playlist)) },
							label = {
								it?.displayName()?.display ?: stringResource(Res.string.info_no_playlist_selected)
							},
							shapes = SegmentedListItemDefaults.segmentedShapes(index = first + 2, count = count)
						)
					}
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_other_devices)) }) {
					SettingsToggleItem(
						checked = preferenceManager.queueSyncEnabled,
						onCheckedChange = { preferenceManager.queueSyncEnabled = it },
						content = { Text(stringResource(Res.string.option_queue_sync)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_queue_sync)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					DeviceNameItem(SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2))
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_listening_history)) }) {
					val enableScrobbling = preferenceManager.enableScrobbling
					val count = if (enableScrobbling) 2 else 1

					SettingsToggleItem(
						checked = enableScrobbling,
						onCheckedChange = { preferenceManager.enableScrobbling = it },
						content = { Text(stringResource(Res.string.option_enable_scrobbling)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_enable_scrobbling)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
					)

					AnimatedVisibility(visible = enableScrobbling) {
						val percent = "${(preferenceManager.scrobblePercentage * 100).roundToInt()}%"
						SettingsSliderItem(
							value = preferenceManager.scrobblePercentage,
							valueRange = 0f..1f,
							onValueChange = { preferenceManager.scrobblePercentage = it },
							trailingContent = { Text(percent) },
							description = stringResource(Res.string.info_scrobble_min_duration),
							content = { Text(stringResource(Res.string.option_scrobble_percentage)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = count)
						)
					}
				}
			}
		}
	}
}
