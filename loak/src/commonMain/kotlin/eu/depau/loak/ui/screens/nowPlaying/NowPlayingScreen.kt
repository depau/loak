package eu.depau.loak.ui.screens.nowPlaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.settings.NowPlayingBackgroundStyle
import eu.depau.loak.domain.models.settings.ToolbarPosition
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_lyrics
import eu.depau.loak.generated.resources.action_navigate_back
import eu.depau.loak.generated.resources.action_pause
import eu.depau.loak.generated.resources.action_play
import eu.depau.loak.generated.resources.title_now_playing
import eu.depau.loak.generated.resources.title_up_next
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Pause
import eu.depau.loak.icons.filled.Play
import eu.depau.loak.icons.outlined.KeyboardArrowDown
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.BlendBackground
import eu.depau.loak.ui.components.common.CoverArt
import eu.depau.loak.ui.components.common.MarqueeText
import eu.depau.loak.ui.components.layouts.SheetScaffold
import eu.depau.loak.ui.components.layouts.TopBarButton
import eu.depau.loak.ui.components.toolbars.SheetToolbar
import eu.depau.loak.ui.screens.lyrics.LyricsScreen
import eu.depau.loak.ui.screens.nowPlaying.components.controls.NowPlayingArtworkPager
import eu.depau.loak.ui.screens.nowPlaying.components.rows.NowPlayingControlsRow
import eu.depau.loak.ui.screens.nowPlaying.viewmodels.NowPlayingViewModel
import eu.depau.loak.ui.screens.queue.QueueScreen
import eu.depau.loak.ui.theme.ContinuousRoundedRectangle
import eu.depau.loak.ui.util.LocalWindowChrome
import eu.depau.loak.ui.util.PaneWindowControls
import eu.depau.loak.ui.util.WindowChromeHost
import eu.depau.loak.ui.util.windowDragArea
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen() {
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()

	val playerState by player.uiState.collectAsStateWithLifecycle()
	val song = playerState.currentSong

	val viewModel = koinViewModel<NowPlayingViewModel> { parametersOf(player) }
	val songIsStarred by viewModel.songIsStarred.collectAsStateWithLifecycle()

	val playerSheet = LocalPlayerSheet.current

	BoxWithConstraints(Modifier.fillMaxSize()) {
		val layout = playerPaneLayout(maxWidth, maxHeight)
		val paneWidth = (maxWidth * .42f).coerceIn(300.dp, 520.dp)
		val isLandscape = maxWidth > maxHeight
		val chrome = LocalWindowChrome.current
		playerSheet.sheetEnabled = layout == PlayerPaneLayout.Sheet
		// the toolbar is the player's title bar: on top when it has a sheet below (and on
		// desktop, where it moves the window and holds its controls)
		WindowChromeHost(paneOpen = layout == PlayerPaneLayout.Beside) {
			Box(Modifier.fillMaxSize()) {
				SheetScaffold(
					toolbarPosition = if (chrome != null || layout == PlayerPaneLayout.Sheet) ToolbarPosition.Top else null,
					toolbar = { windowInsets ->
						SheetToolbar(
							modifier = Modifier
								// the side pane runs up to the window top, next to the toolbar
								.then(
									if (layout == PlayerPaneLayout.Beside) Modifier.padding(end = paneWidth + 16.dp)
									else Modifier
								)
								.then(if (chrome != null) Modifier.height(chrome.barHeight) else Modifier),
							verticalPadding = if (chrome != null) 0.dp else null,
							windowInsets = windowInsets,
							title = { Text(stringResource(Res.string.title_now_playing)) },
							navigationIcon = {
								TopBarButton(
									onClick = { playerSheet.close() },
									content = {
										Icon(
											imageVector = Icons.Outlined.KeyboardArrowDown,
											contentDescription = stringResource(Res.string.action_navigate_back)
										)
									}
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
						when (layout) {
							PlayerPaneLayout.Beside -> NowPlayingBlock(
								modifier = Modifier
									.fillMaxSize()
									.padding(contentPadding)
									.padding(start = 24.dp, end = paneWidth + 40.dp, bottom = 16.dp),
								songIsStarred = songIsStarred,
								onSetSongIsStarred = { viewModel.starSong(it) }
							)

							PlayerPaneLayout.Sheet -> PlayerWithSheet(
								contentPadding = contentPadding,
								song = song,
								isLandscape = isLandscape,
								songIsStarred = songIsStarred,
								onSetSongIsStarred = { viewModel.starSong(it) }
							)
						}
					}
				}
				if (layout == PlayerPaneLayout.Beside) {
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

/** Where Up next and Lyrics go: a pane beside the player, or a sheet under it. */
private enum class PlayerPaneLayout { Beside, Sheet }

/**
 * A pane beside the cover and controls when the window is wide, or a wide landscape phone
 * (which would otherwise get a sheet with a few rows visible); otherwise a sheet under them.
 */
private fun playerPaneLayout(width: Dp, height: Dp) = when {
	width >= 840.dp || (width >= 640.dp && width > height * 1.5f) -> PlayerPaneLayout.Beside
	else -> PlayerPaneLayout.Sheet
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

/** Height of the peeking tabs and of the collapsed player's row over the raised sheet. */
private val PeekHeight = 56.dp
private val HeaderHeight = 72.dp

/** The smallest player that still works above a split: small cover, info and controls. */
private val SplitPlayerHeight = 380.dp

private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

/**
 * Phones and small windows: the cover and controls, with the Up next / Lyrics sheet peeking
 * under them. Dragging up raises the sheet: first to a split (the player shrinks above it,
 * when there's room), then over the player, which collapses into a row under the toolbar.
 */
@Composable
private fun PlayerWithSheet(
	contentPadding: PaddingValues,
	song: DomainSong?,
	isLandscape: Boolean,
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit
) {
	val sheet = LocalPlayerSheet.current
	val density = LocalDensity.current
	BoxWithConstraints(Modifier.fillMaxSize()) {
		val h = constraints.maxHeight.toFloat()
		val (top, peek, header, splitMin) = with(density) {
			listOf(
				contentPadding.calculateTopPadding().toPx(),
				PeekHeight.toPx() + WindowInsets.navigationBars.getBottom(this),
				HeaderHeight.toPx(),
				SplitPlayerHeight.toPx()
			)
		}
		val peekTop = h - peek
		val raisedTop = top + header
		// a split only where the queue still gets about half the screen
		val splitTop = maxOf(top + splitMin, h * .5f)
		sheet.splitAvailable = !isLandscape && peekTop - splitTop > h * .2f && h - splitTop >= h * .45f
		sheet.queueTravel = peekTop - raisedTop
		val sheetTop = { q: Float ->
			if (!sheet.splitAvailable) peekTop + (raisedTop - peekTop) * q
			else if (q <= .5f) peekTop + (splitTop - peekTop) * (q * 2f)
			else splitTop + (raisedTop - splitTop) * (q * 2f - 1f)
		}

		// the player, given the room above the sheet: it shrinks into the split, then fades
		// out as the sheet covers it
		Box(
			Modifier
				.layout { measurable, constraints ->
					val q = sheet.queueFraction
					val height = (sheetTop(minOf(q, .5f)) - top).toInt().coerceAtLeast(0)
					val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
					layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, top.toInt()) }
				}
				.graphicsLayer {
					val q = sheet.queueFraction
					alpha = 1f - seg(q, .55f, .85f)
					translationY = -seg(q, .5f, 1f) * 48.dp.toPx()
				}
		) {
			NowPlayingCompact(
				contentPadding = PaddingValues(vertical = 8.dp),
				isLandscape = isLandscape,
				songIsStarred = songIsStarred,
				onSetSongIsStarred = onSetSongIsStarred
			)
		}

		// the collapsed player, over the raised sheet
		CollapsedPlayerRow(
			song = song,
			modifier = Modifier
				.layout { measurable, constraints ->
					val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, header.toInt()))
					layout(constraints.maxWidth, header.toInt()) { placeable.place(0, top.toInt()) }
				}
				.graphicsLayer { alpha = seg(sheet.queueFraction, .7f, 1f) }
		)

		Surface(
			modifier = Modifier
				.layout { measurable, constraints ->
					val y = sheetTop(sheet.queueFraction)
					val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, (h - raisedTop).toInt()))
					layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, y.toInt()) }
				}
				.nestedScroll(sheet.sheetScroll),
			shape = ContinuousRoundedRectangle(topStart = 28.dp, topEnd = 28.dp),
			color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .94f)
		) {
			Column {
				Box(Modifier.fillMaxWidth().height(PeekHeight)) {
					// grabber
					Box(
						Modifier
							.align(Alignment.TopCenter)
							.padding(top = 6.dp)
							.size(width = 32.dp, height = 4.dp)
							.graphicsLayer { shape = CircleShape; clip = true }
							.background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .4f))
					)
					SheetTabs(
						lyrics = sheet.lyricsTab,
						onSelect = { lyrics ->
							if (sheet.queue < .25f) sheet.showSheet(lyrics) else sheet.lyricsTab = lyrics
						},
						modifier = Modifier.align(Alignment.BottomCenter)
					)
				}
				Box(Modifier.weight(1f)) {
					if (sheet.lyricsTab) LyricsScreen(song) else QueueScreen()
				}
			}
		}
	}
}

/** Up next / Lyrics: the sheet's handle and, on wide windows, the side pane's header. */
@Composable
private fun SheetTabs(lyrics: Boolean, onSelect: (Boolean) -> Unit, modifier: Modifier = Modifier) {
	val tab = if (lyrics) 1 else 0
	// no full-width divider, and the M3 primary indicator: short, with rounded ends
	PrimaryTabRow(
		modifier = modifier,
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
		Tab(selected = !lyrics, onClick = { onSelect(false) }, text = { Text(stringResource(Res.string.title_up_next)) })
		Tab(selected = lyrics, onClick = { onSelect(true) }, text = { Text(stringResource(Res.string.action_lyrics)) })
	}
}

/** The player shrunk to a row: what's playing, and play / pause. Tapping it lowers the sheet. */
@Composable
private fun CollapsedPlayerRow(song: DomainSong?, modifier: Modifier = Modifier) {
	val sheet = LocalPlayerSheet.current
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()
	Row(
		modifier = modifier
			.clickable(enabled = sheet.queue > .9f) { sheet.hideSheet() }
			.padding(horizontal = 16.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		CoverArt(
			coverArtId = song?.coverArtId,
			shape = ContinuousRoundedRectangle(10.dp),
			modifier = Modifier.size(48.dp)
		)
		Column(Modifier.weight(1f)) {
			MarqueeText(song?.title ?: "", style = MaterialTheme.typography.titleMedium)
			MarqueeText(
				song?.artistName ?: "",
				style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
			)
		}
		IconButton(onClick = { if (playerState.isPaused) player.resume() else player.pause() }) {
			Icon(
				if (playerState.isPaused) Icons.Filled.Play else Icons.Filled.Pause,
				stringResource(if (playerState.isPaused) Res.string.action_play else Res.string.action_pause)
			)
		}
	}
}

/** Phones and small windows: cover and controls stacked, or side by side. */
@Composable
private fun NowPlayingCompact(
	contentPadding: PaddingValues,
	isLandscape: Boolean,
	songIsStarred: Boolean,
	onSetSongIsStarred: (Boolean) -> Unit
) {
	if (isLandscape) {
		Row(
			modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(contentPadding),
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
			modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp).padding(contentPadding),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.Center
		) {
			NowPlayingArtworkPager(
				modifier = Modifier.weight(1f).fillMaxWidth(),
				isLandscape = false
			)
			NowPlayingControlsRow(
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
	val sheet = LocalPlayerSheet.current
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
				SheetTabs(
					lyrics = sheet.lyricsTab,
					onSelect = { sheet.lyricsTab = it },
					modifier = Modifier.weight(1f).padding(top = if (docked) 8.dp else 0.dp)
				)
				PaneWindowControls(
					if (docked) Modifier else Modifier.align(Alignment.CenterVertically).padding(end = 8.dp)
				)
			}
			Box(Modifier.weight(1f).padding(top = 8.dp)) {
				if (sheet.lyricsTab) LyricsScreen(song) else QueueScreen()
			}
		}
	}
}
