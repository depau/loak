package eu.depau.loak.ui.screens.lyrics

import androidx.compose.animation.AnimatedContent
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Lyrics
import eu.depau.loak.icons.outlined.Share
import eu.depau.loak.icons.outlined.SyncAlt
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Fullscreen
import eu.depau.loak.generated.resources.action_karaoke
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_share_lyrics
import eu.depau.loak.generated.resources.action_sync_lyrics
import eu.depau.loak.generated.resources.action_change_lyrics_provider
import eu.depau.loak.generated.resources.Res
import androidx.compose.ui.Alignment
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalSheetState
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.settings.ToolbarPosition
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.common.ErrorBox
import eu.depau.loak.ui.components.layouts.SheetScaffold
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.PersistentViewModelStoreOwner
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.lyrics.components.LyricsScreenContent
import eu.depau.loak.ui.screens.lyrics.components.LyricsScreenLoadingView
import eu.depau.loak.ui.screens.lyrics.components.LyricsScreenPlaceholder
import eu.depau.loak.ui.screens.lyrics.components.LyricsSyncTool
import eu.depau.loak.ui.screens.lyrics.dialogs.LyricsShareSheet
import eu.depau.loak.ui.screens.lyrics.viewmodels.LyricsScreenViewModel
import eu.depau.loak.ui.util.KeepScreenOn
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
	song: DomainSong?,
	/** Shows the full-screen (karaoke) button. */
	onFullScreen: (() -> Unit)? = null
) {
	val backStack = LocalNavStack.current

	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsStateWithLifecycle()

	val viewModel = koinViewModel<LyricsScreenViewModel>(
		key = song?.id,
		parameters = { parametersOf(song) },
		viewModelStoreOwner = koinInject<PersistentViewModelStoreOwner>()
	)
	val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
	val selectedProviderIndex by viewModel.selectedProviderIndex.collectAsStateWithLifecycle()
	val lyricOffset by viewModel.lyricOffset.collectAsStateWithLifecycle()
	val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

	var isSelecting by rememberSaveable { mutableStateOf(false) }
	val selectedIndices = rememberSaveable { mutableStateListOf<Int>() }
	var wasPlayingBeforeSelection by rememberSaveable { mutableStateOf(false) }
	var shareSheetOpen by rememberSaveable { mutableStateOf(false) }
	var syncToolOpen by rememberSaveable { mutableStateOf(false) }
	var providerMenuOpen by rememberSaveable { mutableStateOf(false) }

	val song = song ?: return LyricsScreenPlaceholder(
		onRefresh = { viewModel.refreshResults() }
	)
	val duration = song.duration
	val progressState = playerState.progress
	val currentDuration = duration * progressState.toDouble()

	val spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
	val effectSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()

	val toggleIsSelecting = {
		if (isSelecting) {
			isSelecting = false
			selectedIndices.clear()
			if (wasPlayingBeforeSelection) {
				player.resume()
			}
		} else {
			wasPlayingBeforeSelection = !playerState.isPaused
			player.pause()
			isSelecting = true
		}
	}

	val preferenceManager = koinInject<PreferenceManager>()
	if (preferenceManager.lyricsKeepAlive) {
		KeepScreenOn()
	}

	// lyrics currently on screen + the alternatives
	val providerResults = (lyricsState as? UiState.Success)?.data.orEmpty()
	val currentData = providerResults.getOrNull(selectedProviderIndex)
	val canSync = currentData?.isSynced == true && providerResults.isNotEmpty()

	val body: @Composable (PaddingValues) -> Unit = { contentPadding ->
		AnimatedContent(
			targetState = lyricsState,
			modifier = Modifier.fillMaxSize(),
			transitionSpec = {
				ContentTransform(
					targetContentEnter = fadeIn(effectSpec) + scaleIn(spatialSpec, 0.8f),
					initialContentExit = fadeOut(effectSpec) + scaleOut(spatialSpec)
				)
			},
		) { lyricsState ->
			when (lyricsState) {
				is UiState.Error -> ErrorBox(
					error = lyricsState,
					modifier = Modifier.wrapContentSize(),
					onRetry = { viewModel.refreshResults() }
				)

				is UiState.Loading -> LyricsScreenLoadingView()
				is UiState.Success -> {
					LyricsScreenContent(
						data = currentData,
						onRefresh = { viewModel.refreshResults() },
						isSelecting = isSelecting,
						selectedIndices = selectedIndices.toImmutableList(),
						onAddSelectedIndex = { idx -> selectedIndices.add(idx) },
						onRemoveSelectedIndex = { idx -> selectedIndices.remove(idx) },
						onRestartAtIndex = { idx ->
							selectedIndices.clear()
							selectedIndices.add(idx)
						},
						duration = duration,
						currentDuration = currentDuration,
						lyricOffset = lyricOffset.milliseconds,
						contentPadding = contentPadding
					)
				}
			}
		}

		if (shareSheetOpen) {
			val lyricsList = currentData?.lines?.map { line ->
				(line.time?.inWholeMilliseconds ?: 0L) to line.text
			}

			if (lyricsList != null) {
				val sortedIndices = selectedIndices.sorted()
				val stringsToShare = sortedIndices.mapNotNull { index ->
					lyricsList.getOrNull(index)?.second
				}.toImmutableList()

				LyricsShareSheet(
					song = song,
					selectedLyrics = stringsToShare,
					onDismiss = { shareSheetOpen = false },
					onShare = {
						shareSheetOpen = false
						isSelecting = false
						selectedIndices.clear()
					}
				)
			}
		}
	}

	Box(Modifier.fillMaxSize()) {
		body(PaddingValues())
		// provider picker first, then full-screen, then cancel toggles, then share
		Row(Modifier.align(Alignment.TopStart)) {
			if (!isSelecting && providerResults.isNotEmpty()) {
				Box {
					IconButton(onClick = { providerMenuOpen = true }) {
						Icon(
							Icons.Outlined.Lyrics,
							stringResource(Res.string.action_change_lyrics_provider)
						)
					}
					DropdownMenu(
						expanded = providerMenuOpen,
						onDismissRequest = { providerMenuOpen = false }
					) {
						providerResults.forEachIndexed { index, result ->
							DropdownMenuItem(
								text = { Text(result.providerName) },
								leadingIcon = {
									if (index == selectedProviderIndex) {
										Icon(
											Icons.Outlined.Check,
											contentDescription = null
										)
									}
								},
								onClick = {
									providerMenuOpen = false
									viewModel.selectProvider(index)
								}
							)
						}
					}
				}
			}
			if (onFullScreen != null && !isSelecting) IconButton(onClick = onFullScreen) {
				Icon(Icons.Outlined.Fullscreen, stringResource(Res.string.action_karaoke))
			}
			if (!isSelecting && canSync) IconButton(
				onClick = {
					syncToolOpen = true
					viewModel.beginSyncAdjustment()
				}
			) {
				Icon(Icons.Outlined.SyncAlt, stringResource(Res.string.action_sync_lyrics))
			}
			if (isSelecting) IconButton(onClick = toggleIsSelecting) {
				Icon(Icons.Outlined.Close, stringResource(Res.string.action_cancel))
			}
			IconButton(
				onClick = { if (isSelecting) shareSheetOpen = true else toggleIsSelecting() },
				enabled = !isSelecting || selectedIndices.isNotEmpty()
			) {
				Icon(
					if (isSelecting) Icons.Outlined.Check else Icons.Outlined.Share,
					stringResource(Res.string.action_share_lyrics)
				)
			}
		}

		// sync/offset tool: hidden by default, an inline bar bottom-center when triggered so the
		// lyrics stay fully readable. Cancel (X) is on the opposite side from Save.
		if (syncToolOpen) {
			Row(Modifier.align(Alignment.BottomCenter).padding(vertical = 12.dp)) {
				LyricsSyncTool(
					offsetMs = lyricOffset,
					onOffsetChange = { viewModel.setOffset(it) },
					onCancel = {
						syncToolOpen = false
						viewModel.discardOffset()
					},
					onSave = {
						syncToolOpen = false
						viewModel.saveLyrics()
					},
					saving = isSaving
				)
			}
		}
	}
}
