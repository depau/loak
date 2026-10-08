package eu.depau.loak.ui.screens.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.BottomBarCollapseMode
import eu.depau.loak.domain.models.settings.CoverArtTapAction
import eu.depau.loak.domain.models.settings.MiniPlayerStyle
import eu.depau.loak.domain.models.settings.NowPlayingBackgroundStyle
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_lyrics
import eu.depau.loak.generated.resources.option_animated_background
import eu.depau.loak.generated.resources.option_cover_art_action
import eu.depau.loak.generated.resources.option_hide_while_scrolling
import eu.depau.loak.generated.resources.option_lyrics_autoscroll
import eu.depau.loak.generated.resources.option_lyrics_blur
import eu.depau.loak.generated.resources.option_lyrics_bright_inactive
import eu.depau.loak.generated.resources.option_lyrics_keep_alive
import eu.depau.loak.generated.resources.option_lyrics_sources
import eu.depau.loak.generated.resources.option_mini_player_style
import eu.depau.loak.generated.resources.option_progress_bar_style
import eu.depau.loak.generated.resources.option_show_audio_format
import eu.depau.loak.generated.resources.option_swipe_to_skip
import eu.depau.loak.generated.resources.subtitle_animated_background
import eu.depau.loak.generated.resources.subtitle_configure_lyric_providers
import eu.depau.loak.generated.resources.subtitle_hide_while_scrolling
import eu.depau.loak.generated.resources.subtitle_lyrics_autoscroll
import eu.depau.loak.generated.resources.subtitle_lyrics_blur
import eu.depau.loak.generated.resources.subtitle_lyrics_bright_inactive
import eu.depau.loak.generated.resources.subtitle_lyrics_keep_alive
import eu.depau.loak.generated.resources.subtitle_show_audio_format
import eu.depau.loak.generated.resources.subtitle_swipe_to_skip
import eu.depau.loak.generated.resources.title_mini_player_tabs
import eu.depau.loak.generated.resources.title_now_playing
import eu.depau.loak.generated.resources.title_player
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsChoiceItem
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.settings.components.SettingsNavItem
import eu.depau.loak.ui.screens.settings.components.SettingsToggleItem
import eu.depau.loak.ui.screens.settings.dialogs.LyricsPrioritySheet
import eu.depau.loak.ui.screens.settings.dialogs.NowPlayingSliderStyleDialog
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The now playing screen, lyrics, and the mini player with the tabs under it. */
@Composable
fun SettingsPlayerScreen() {
	val platformContext = LocalPlatformContext.current
	val hideBack = platformContext.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val preferenceManager = koinInject<PreferenceManager>()
	var lyricProvidersSheetOpen by rememberSaveable { mutableStateOf(false) }
	var sliderStyleDialogOpen by rememberSaveable { mutableStateOf(false) }

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_player)) },
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
				SettingsGroup(title = { Text(stringResource(Res.string.title_now_playing)) }) {
					SettingsToggleItem(
						checked = preferenceManager.swipeToSkip,
						onCheckedChange = { preferenceManager.swipeToSkip = it },
						content = { Text(stringResource(Res.string.option_swipe_to_skip)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_swipe_to_skip)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 5)
					)
					SettingsChoiceItem(
						content = { Text(stringResource(Res.string.option_cover_art_action)) },
						choices = CoverArtTapAction.entries.toImmutableList(),
						selectedChoice = preferenceManager.nowPlayingCoverArtAction,
						onChoiceSelected = { preferenceManager.nowPlayingCoverArtAction = it },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 5)
					)
					SettingsToggleItem(
						checked = preferenceManager.nowPlayingSongInfo,
						onCheckedChange = { preferenceManager.nowPlayingSongInfo = it },
						content = { Text(stringResource(Res.string.option_show_audio_format)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_show_audio_format)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 5)
					)
					SettingsToggleItem(
						checked = preferenceManager.nowPlayingBackgroundStyle == NowPlayingBackgroundStyle.Dynamic,
						onCheckedChange = {
							preferenceManager.nowPlayingBackgroundStyle =
								if (it) NowPlayingBackgroundStyle.Dynamic else NowPlayingBackgroundStyle.Static
						},
						content = { Text(stringResource(Res.string.option_animated_background)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_animated_background)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 5)
					)
					SegmentedListItem(
						onClick = { sliderStyleDialogOpen = true },
						content = { Text(stringResource(Res.string.option_progress_bar_style)) },
						supportingContent = { Text(stringResource(preferenceManager.nowPlayingSliderStyle.displayName)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 4, count = 5)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.action_lyrics)) }) {
					SettingsNavItem(
						onClick = { lyricProvidersSheetOpen = true },
						content = { Text(stringResource(Res.string.option_lyrics_sources)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_configure_lyric_providers)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 5)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsAutoscroll,
						onCheckedChange = { preferenceManager.lyricsAutoscroll = it },
						content = { Text(stringResource(Res.string.option_lyrics_autoscroll)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_lyrics_autoscroll)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 5)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsBlur,
						onCheckedChange = { preferenceManager.lyricsBlur = it },
						content = { Text(stringResource(Res.string.option_lyrics_blur)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_lyrics_blur)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 2, count = 5)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsBrightInactive,
						onCheckedChange = { preferenceManager.lyricsBrightInactive = it },
						content = { Text(stringResource(Res.string.option_lyrics_bright_inactive)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_lyrics_bright_inactive)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 3, count = 5)
					)
					SettingsToggleItem(
						checked = preferenceManager.lyricsKeepAlive,
						onCheckedChange = { preferenceManager.lyricsKeepAlive = it },
						content = { Text(stringResource(Res.string.option_lyrics_keep_alive)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_lyrics_keep_alive)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 4, count = 5)
					)
				}

				SettingsGroup(title = { Text(stringResource(Res.string.title_mini_player_tabs)) }) {
					SettingsChoiceItem(
						choices = MiniPlayerStyle.entries.toImmutableList(),
						selectedChoice = preferenceManager.miniPlayerStyle,
						onChoiceSelected = { preferenceManager.miniPlayerStyle = it },
						content = { Text(stringResource(Res.string.option_mini_player_style)) },
						label = { stringResource(it.displayName) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 0, count = 2)
					)
					SettingsToggleItem(
						checked = preferenceManager.bottomBarCollapseMode == BottomBarCollapseMode.OnScroll,
						onCheckedChange = {
							preferenceManager.bottomBarCollapseMode =
								if (it) BottomBarCollapseMode.OnScroll else BottomBarCollapseMode.Never
						},
						content = { Text(stringResource(Res.string.option_hide_while_scrolling)) },
						supportingContent = { Text(stringResource(Res.string.subtitle_hide_while_scrolling)) },
						shapes = SegmentedListItemDefaults.segmentedShapes(index = 1, count = 2)
					)
				}
			}
		}
	}

	LyricsPrioritySheet(
		presented = lyricProvidersSheetOpen,
		onDismissRequest = { lyricProvidersSheetOpen = false }
	)
	NowPlayingSliderStyleDialog(
		presented = sliderStyleDialogOpen,
		onDismissRequest = { sliderStyleDialogOpen = false }
	)
}
