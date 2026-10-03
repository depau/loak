package eu.depau.loak.ui.screens.nowPlaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import eu.depau.loak.ui.screens.queue.QueueScreen
import eu.depau.loak.ui.screens.lyrics.LyricsScreen
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.di.isExpanded
import eu.depau.loak.di.LocalPlatformContext
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.Tab
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import kotlinx.coroutines.launch
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_lyrics
import eu.depau.loak.generated.resources.action_navigate_back
import eu.depau.loak.generated.resources.action_queue
import eu.depau.loak.generated.resources.title_now_playing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalSheetState
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.NowPlayingBackgroundStyle
import eu.depau.loak.domain.models.settings.ToolbarPosition
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.KeyboardArrowDown
import eu.depau.loak.icons.outlined.List
import eu.depau.loak.icons.outlined.Lyrics
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.BlendBackground
import eu.depau.loak.ui.components.layouts.SheetScaffold
import eu.depau.loak.ui.components.layouts.TopBarButton
import eu.depau.loak.ui.components.toolbars.SheetActionButton
import eu.depau.loak.ui.components.toolbars.SheetToolbar
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.nowPlaying.components.controls.NowPlayingArtworkPager
import eu.depau.loak.ui.screens.nowPlaying.components.rows.NowPlayingControlsRow
import eu.depau.loak.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val backStack = LocalNavStack.current

	val currentScreen = backStack.lastOrNull()
	val isPlayerCurrent = currentScreen is Screen.NowPlaying
		|| currentScreen is Screen.Queue
		|| currentScreen is Screen.PlaybackSpeed
		|| currentScreen is Screen.SongDetailSheet

	val playerState by player.uiState.collectAsStateWithLifecycle()
	val song = playerState.currentSong
	// expanded windows: the queue and lyrics sit in a pane beside the player
	val expanded = LocalPlatformContext.current.isExpanded()

	val viewModel = koinViewModel<NowPlayingViewModel> { parametersOf(player) }
	val songIsStarred by viewModel.songIsStarred.collectAsStateWithLifecycle()

	val sheetState = LocalSheetState.current
	val closeScope = rememberCoroutineScope()
	val animateToDismiss = dropUnlessResumed {
		closeScope.launch {
			sheetState.hide()
		}.invokeOnCompletion {
			if (!sheetState.isVisible) {
				backStack.remove(Screen.NowPlaying)
			}
		}
	}

	SheetScaffold(
		toolbar = { windowInsets ->
			SheetToolbar(
				modifier = Modifier.alpha(if (isPlayerCurrent) 1f else 0f),
				windowInsets = windowInsets,
				title = {
					Text(stringResource(Res.string.title_now_playing))
				},
				navigationIcon = {
					TopBarButton(
						onClick = animateToDismiss,
						content = {
							Icon(
								imageVector = Icons.Outlined.KeyboardArrowDown,
								contentDescription = stringResource(Res.string.action_navigate_back)
							)
						}
					)
				},
				actions = {
					if (!expanded) SheetActionButton(
						icon = Icons.Outlined.Lyrics,
						contentDescription = stringResource(Res.string.action_lyrics),
						onClick = dropUnlessResumed { backStack.add(Screen.Lyrics) },
						isStartRounded = true
					)
					if (!expanded) SheetActionButton(
						icon = Icons.Outlined.List,
						contentDescription = stringResource(Res.string.action_queue),
						onClick = dropUnlessResumed { backStack.add(Screen.Queue) },
						isEndRounded = true
					)
				}
			)
		}
	) { contentPadding ->
		Box(Modifier.fillMaxSize()) {
			if (preferenceManager.nowPlayingBackgroundStyle
				== NowPlayingBackgroundStyle.Dynamic
			) {
				BlendBackground(
					coverArtId = song?.coverArtId,
					isPaused = playerState.isPaused
				)
			}
			if (!isPlayerCurrent) return@Box
			BoxWithConstraints(
				modifier = Modifier
					.padding(horizontal = 8.dp)
					.fillMaxSize()
			) {
				val isLandscape = maxWidth > maxHeight
				val toolbarPosition = preferenceManager.nowPlayingToolbarPosition
				val padding = when {
					isLandscape -> contentPadding
					toolbarPosition == ToolbarPosition.Top -> contentPadding.plus(
						PaddingValues(
							bottom = 40.dp
						)
					)

					toolbarPosition == ToolbarPosition.Bottom -> contentPadding.plus(
						PaddingValues(
							top = 40.dp
						)
					)

					else -> contentPadding
				}
				if (isLandscape && expanded) {
					// cover and controls on the left, up next / lyrics always visible on the right
					Row(
						modifier = Modifier.fillMaxSize().padding(contentPadding),
						horizontalArrangement = Arrangement.spacedBy(24.dp)
					) {
						Column(
							modifier = Modifier.weight(1f).fillMaxHeight(),
							horizontalAlignment = Alignment.CenterHorizontally,
							verticalArrangement = Arrangement.Center
						) {
							NowPlayingArtworkPager(
								modifier = Modifier.weight(1f).fillMaxWidth(),
								isLandscape = false
							)
							NowPlayingControlsRow(
								modifier = Modifier.weight(1f),
								isLandscape = false,
								songIsStarred = songIsStarred,
								onSetSongIsStarred = { viewModel.starSong(it) }
							)
						}
						NowPlayingSidePane(
							song = song,
							modifier = Modifier
								.weight(0.8f)
								.fillMaxHeight()
								.padding(end = 16.dp, bottom = 16.dp)
						)
					}
				} else if (isLandscape) {
					Row(
						modifier = Modifier.fillMaxSize().padding(padding),
						horizontalArrangement = Arrangement.SpaceEvenly,
						verticalAlignment = Alignment.CenterVertically
					) {
						NowPlayingArtworkPager(
							modifier = Modifier.weight(1f).fillMaxHeight(),
							isLandscape = true
						)
						NowPlayingControlsRow(
							modifier = Modifier.weight(1f).fillMaxHeight(),
							isLandscape = true,
							songIsStarred = songIsStarred,
							onSetSongIsStarred = { viewModel.starSong(it) }
						)
					}
				} else {
					Column(
						modifier = Modifier.fillMaxSize().padding(padding),
						horizontalAlignment = Alignment.CenterHorizontally,
						verticalArrangement = Arrangement.Center
					) {
						NowPlayingArtworkPager(
							modifier = Modifier.weight(1f).fillMaxWidth(),
							isLandscape = false
						)
						NowPlayingControlsRow(
							modifier = Modifier.weight(1f),
							isLandscape = false,
							songIsStarred = songIsStarred,
							onSetSongIsStarred = { viewModel.starSong(it) }
						)
					}
				}
			}
		}
	}
}

@Composable
private fun NowPlayingSidePane(song: DomainSong?, modifier: Modifier = Modifier) {
	var tab by rememberSaveable { mutableStateOf(0) }
	Surface(
		modifier = modifier,
		shape = ContinuousRoundedRectangle(28.dp),
		color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)
	) {
		Column {
			// no full-width divider, and the M3 primary indicator: short, with rounded ends
			PrimaryTabRow(
				selectedTabIndex = tab,
				containerColor = Color.Transparent,
				indicator = {
					TabRowDefaults.PrimaryIndicator(
						modifier = Modifier.tabIndicatorOffset(tab, matchContentSize = true),
						width = Dp.Unspecified,
						shape = CircleShape
					)
				},
				divider = {}
			) {
				Tab(
					selected = tab == 0,
					onClick = { tab = 0 },
					text = { Text(stringResource(Res.string.action_queue)) }
				)
				Tab(
					selected = tab == 1,
					onClick = { tab = 1 },
					text = { Text(stringResource(Res.string.action_lyrics)) }
				)
			}
			Box(Modifier.weight(1f).padding(top = 8.dp)) {
				if (tab == 0) QueueScreen(pane = true) else LyricsScreen(song, pane = true)
			}
		}
	}
}
