package eu.depau.loak.ui.screens.nowPlaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import eu.depau.loak.ui.screens.queue.QueueScreen
import eu.depau.loak.ui.screens.lyrics.LyricsScreen
import eu.depau.loak.domain.models.DomainSong
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import eu.depau.loak.ui.util.LocalWindowChrome
import eu.depau.loak.ui.util.PaneWindowControls
import eu.depau.loak.ui.util.WindowChromeHost
import eu.depau.loak.ui.util.windowDragArea
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

	BoxWithConstraints(Modifier.fillMaxSize()) {
		val layout = playerPaneLayout(maxWidth, maxHeight)
		val paneWidth = if (maxWidth < 960.dp) 340.dp else 400.dp
		val isLandscape = maxWidth > maxHeight
		val chrome = LocalWindowChrome.current
		// desktop: the player covers the app's title bar, so it brings its own: the toolbar
		// moves to the top and the window controls follow (into the side pane when it shows)
		WindowChromeHost(paneOpen = layout == PlayerPaneLayout.Beside) {
			Box(Modifier.fillMaxSize()) {
				SheetScaffold(
					toolbarPosition = if (chrome != null) ToolbarPosition.Top else null,
					toolbar = { windowInsets ->
						SheetToolbar(
							modifier = Modifier
								.alpha(if (isPlayerCurrent) 1f else 0f)
								// the side pane runs up to the window top, next to the toolbar
								.then(
									if (layout == PlayerPaneLayout.Beside) Modifier.padding(end = paneWidth + 16.dp)
									else Modifier
								)
								.then(if (chrome != null) Modifier.height(chrome.barHeight) else Modifier),
							verticalPadding = if (chrome != null) 0.dp else null,
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
								if (layout == PlayerPaneLayout.None) {
									SheetActionButton(
										icon = Icons.Outlined.Lyrics,
										contentDescription = stringResource(Res.string.action_lyrics),
										onClick = dropUnlessResumed { backStack.add(Screen.Lyrics) },
										isStartRounded = true
									)
									SheetActionButton(
										icon = Icons.Outlined.List,
										contentDescription = stringResource(Res.string.action_queue),
										onClick = dropUnlessResumed { backStack.add(Screen.Queue) },
										isEndRounded = true
									)
								}
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
						val block = @Composable { modifier: Modifier ->
							NowPlayingBlock(
								modifier = modifier,
								songIsStarred = songIsStarred,
								onSetSongIsStarred = { viewModel.starSong(it) }
							)
						}
						when (layout) {
							PlayerPaneLayout.Beside -> block(
								Modifier
									.fillMaxSize()
									.padding(contentPadding)
									.padding(start = 24.dp, end = paneWidth + 40.dp, bottom = 16.dp)
							)

							PlayerPaneLayout.Below -> Column(
								Modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
								verticalArrangement = Arrangement.spacedBy(16.dp)
							) {
								block(Modifier.weight(0.55f).fillMaxWidth())
								NowPlayingSidePane(song = song, modifier = Modifier.weight(0.45f).fillMaxWidth())
							}

							PlayerPaneLayout.None -> NowPlayingCompact(
								contentPadding = contentPadding,
								isLandscape = isLandscape,
								songIsStarred = songIsStarred,
								onSetSongIsStarred = { viewModel.starSong(it) }
							)
						}
					}
				}
				if (layout == PlayerPaneLayout.Beside && isPlayerCurrent) {
					// Windows: flush in the corner, under the caption buttons
					val docked = chrome?.controlsInCorner == true
					NowPlayingSidePane(
						song = song,
						docked = docked,
						modifier = Modifier
							.align(Alignment.TopEnd)
							.windowInsetsPadding(WindowInsets.systemBars)
							.then(
								if (docked) Modifier
								else Modifier.padding(top = 8.dp, end = 16.dp, bottom = 16.dp)
							)
							.width(paneWidth)
							.fillMaxHeight()
					)
				}
			}
		}
	}
}

/** Where the player's Queue / Lyrics pane goes, by window size. */
private enum class PlayerPaneLayout { Beside, Below, None }

/**
 * Beside the cover and controls when the window is wide enough and not much taller than
 * wide; below them in tall windows (not phones); otherwise not at all: the toolbar's
 * buttons open them as sheets.
 */
private fun playerPaneLayout(width: Dp, height: Dp) = when {
	width >= 760.dp && width >= height * 0.85f -> PlayerPaneLayout.Beside
	width >= 600.dp && height >= 960.dp -> PlayerPaneLayout.Below
	else -> PlayerPaneLayout.None
}

/** Cover above the song info and controls, as one centred block at most 520 dp wide. */
@Composable
private fun NowPlayingBlock(
	modifier: Modifier,
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit
) {
	// the toolbar may be at the bottom, leaving the status bar to keep clear of here
	Box(
		modifier.windowInsetsPadding(
			WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
		),
		contentAlignment = Alignment.Center
	) {
		Column(
			modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			NowPlayingArtworkPager(
				// square, as large as the room left by the controls allows
				modifier = Modifier.weight(1f, fill = false).aspectRatio(1f),
				isLandscape = false
			)
			Spacer(Modifier.height(16.dp))
			NowPlayingControlsRow(
				isLandscape = false,
				songIsStarred = songIsStarred,
				onSetSongIsStarred = onSetSongIsStarred
			)
		}
	}
}

/** Phones and small windows: no pane; cover and controls stacked, or side by side. */
@Composable
private fun NowPlayingCompact(
	contentPadding: PaddingValues,
	isLandscape: Boolean,
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit
) {
	val toolbarPosition = koinInject<PreferenceManager>().nowPlayingToolbarPosition
	val padding = when {
		isLandscape -> contentPadding
		toolbarPosition == ToolbarPosition.Top -> contentPadding.plus(PaddingValues(bottom = 40.dp))
		else -> contentPadding.plus(PaddingValues(top = 40.dp))
	}
	if (isLandscape) {
		Row(
			modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(padding),
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
				onSetSongIsStarred = onSetSongIsStarred
			)
		}
	} else {
		Column(
			modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(padding),
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
				onSetSongIsStarred = onSetSongIsStarred
			)
		}
	}
}

@Composable
private fun NowPlayingSidePane(
	song: DomainSong?,
	modifier: Modifier = Modifier,
	docked: Boolean = false
) {
	var tab by rememberSaveable { mutableStateOf(0) }
	Surface(
		modifier = modifier,
		shape = if (docked) ContinuousRoundedRectangle(topStart = 28.dp, bottomStart = 28.dp)
		else ContinuousRoundedRectangle(28.dp),
		color = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)
	) {
		Column {
			// the tab row is the title bar here: it moves the window and holds its controls.
			// Its height is fixed: the controls fill whatever height they're given
			Row(Modifier.height(if (docked) 56.dp else 48.dp).windowDragArea()) {
				// no full-width divider, and the M3 primary indicator: short, with rounded ends
				PrimaryTabRow(
					modifier = Modifier.weight(1f).padding(top = if (docked) 8.dp else 0.dp),
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
				PaneWindowControls(
					if (docked) Modifier else Modifier.align(Alignment.CenterVertically).padding(end = 8.dp)
				)
			}
			Box(Modifier.weight(1f).padding(top = 8.dp)) {
				if (tab == 0) QueueScreen(pane = true) else LyricsScreen(song, pane = true)
			}
		}
	}
}
