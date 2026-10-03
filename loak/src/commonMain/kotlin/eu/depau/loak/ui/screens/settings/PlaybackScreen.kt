package eu.depau.loak.ui.screens.settings

import eu.depau.loak.ui.components.common.displayName
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.collections.immutable.toImmutableList
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_auto_fill_queue
import eu.depau.loak.generated.resources.option_enable_scrobbling
import eu.depau.loak.generated.resources.option_explicit_playback
import eu.depau.loak.generated.resources.option_min_duration_to_scrobble
import eu.depau.loak.generated.resources.option_scrobble_percentage
import eu.depau.loak.generated.resources.subtitle_audio_effects
import eu.depau.loak.generated.resources.subtitle_auto_fill_queue
import eu.depau.loak.generated.resources.subtitle_enable_scrobbling
import eu.depau.loak.generated.resources.subtitle_streaming_quality
import eu.depau.loak.generated.resources.title_audio_effects
import eu.depau.loak.generated.resources.title_behaviour
import eu.depau.loak.generated.resources.title_playback
import eu.depau.loak.generated.resources.title_streaming_quality
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsChoiceItem
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.ui.screens.settings.components.SettingsSliderItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainPlaylistListType
import eu.depau.loak.domain.models.settings.StartupQueue
import eu.depau.loak.domain.repositories.PlaylistRepository
import eu.depau.loak.generated.resources.info_no_playlist_selected
import eu.depau.loak.generated.resources.option_queue_sync
import eu.depau.loak.generated.resources.option_startup_playlist
import eu.depau.loak.generated.resources.option_startup_queue
import eu.depau.loak.generated.resources.subtitle_queue_sync
import eu.depau.loak.generated.resources.title_queue_sync
import kotlin.math.roundToInt

@Composable
fun SettingsPlaybackScreen() {
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val backStack = LocalNavStack.current
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
				SettingsGroup {
					val isAndroid = platformContext.platformType == PlatformType.Android
					val count = if (isAndroid) 4 else 2

					SettingsNavItem(
						onClick = dropUnlessResumed { backStack.add(Screen.Settings.StreamingQuality) },
						content = { Text(stringResource(Res.string.title_streaming_quality)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_streaming_quality)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
					)
					SettingsChoiceItem(
						choices = ExplicitContentPlayback.entries.toImmutableList(),
						selectedChoice = preferenceManager.explicitContentPlayback,
						onChoiceSelected = { preferenceManager.explicitContentPlayback = it },
						content = { Text(stringResource(Res.string.option_explicit_playback)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = count)
					)

					if (isAndroid) {
						SettingsNavItem(
							onClick = dropUnlessResumed { backStack.add(Screen.Settings.Effects) },
							content = { Text(stringResource(Res.string.title_audio_effects)) },
							supportingContent = { Text(stringResource(Res.string.subtitle_audio_effects)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(
								index = 2,
								count = count
							)
						)
						SettingsToggleItem(
							checked = preferenceManager.autoFillQueue,
							onCheckedChange = { preferenceManager.autoFillQueue = it },
							content = { Text(stringResource(Res.string.option_auto_fill_queue)) },
							supportingContent = { Text(stringResource(Res.string.subtitle_auto_fill_queue)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(
								index = 3,
								count = count
							)
						)
					}
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_behaviour)) }) {
					val enableScrobbling = preferenceManager.enableScrobbling
					val count = if (enableScrobbling) 3 else 1

					SettingsToggleItem(
						checked = enableScrobbling,
						onCheckedChange = { preferenceManager.enableScrobbling = it },
						content = { Text(stringResource(Res.string.option_enable_scrobbling)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_enable_scrobbling)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
					)

					AnimatedVisibility(visible = enableScrobbling) {
						SettingsSliderItem(
							value = preferenceManager.scrobblePercentage,
							valueRange = 0f..1f,
							onValueChange = { preferenceManager.scrobblePercentage = it },
							trailingContent = { Text("${(preferenceManager.scrobblePercentage * 100).roundToInt()}%") },
							content = { Text(stringResource(Res.string.option_scrobble_percentage)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = count)
						)
					}

					AnimatedVisibility(visible = enableScrobbling) {
						SettingsSliderItem(
							value = preferenceManager.minDurationToScrobble,
							valueRange = 0f..60f,
							onValueChange = { preferenceManager.minDurationToScrobble = it },
							trailingContent = { Text("${preferenceManager.minDurationToScrobble.toInt()}s") },
							content = { Text(stringResource(Res.string.option_min_duration_to_scrobble)) },
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = count)
						)
					}
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_queue_sync)) }) {
					val startupQueue = preferenceManager.startupQueue
					val pickPlaylist = startupQueue == StartupQueue.Playlist
					val count = if (pickPlaylist) 3 else 2

					SettingsToggleItem(
						checked = preferenceManager.queueSyncEnabled,
						onCheckedChange = { preferenceManager.queueSyncEnabled = it },
						content = { Text(stringResource(Res.string.option_queue_sync)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_queue_sync)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = count)
					)
					SettingsChoiceItem(
						choices = StartupQueue.entries.toImmutableList(),
						selectedChoice = startupQueue,
						onChoiceSelected = { preferenceManager.startupQueue = it },
						content = { Text(stringResource(Res.string.option_startup_queue)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = count)
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
							shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = count)
						)
					}
				}
			}
		}
	}
}
