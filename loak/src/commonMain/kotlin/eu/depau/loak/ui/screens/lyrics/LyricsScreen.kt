package eu.depau.loak.ui.screens.lyrics

import androidx.compose.animation.AnimatedContent
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.Share
import eu.depau.loak.icons.Icons
import eu.depau.loak.generated.resources.action_cancel
import eu.depau.loak.generated.resources.action_share_lyrics
import eu.depau.loak.generated.resources.Res
import androidx.compose.ui.Alignment
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import eu.depau.loak.ui.screens.lyrics.components.LyricsScreenToolbar
import eu.depau.loak.ui.screens.lyrics.dialogs.LyricsShareSheet
import eu.depau.loak.ui.screens.lyrics.viewmodels.LyricsScreenViewModel
import eu.depau.loak.ui.util.KeepScreenOn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
	song: DomainSong?,
	/** In the now playing side pane: no toolbar, just the lines and a share button. */
	pane: Boolean = false
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

	var isSelecting by rememberSaveable { mutableStateOf(false) }
	val selectedIndices = rememberSaveable { mutableStateListOf<Int>() }
	var wasPlayingBeforeSelection by rememberSaveable { mutableStateOf(false) }
	var shareSheetOpen by rememberSaveable { mutableStateOf(false) }

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

	val sheetState = if (pane) null else LocalSheetState.current
	val closeScope = rememberCoroutineScope()
	val animateToDismiss = {
		if (sheetState != null) closeScope.launch {
			sheetState.hide()
		}.invokeOnCompletion {
			if (!sheetState.isVisible) {
				backStack.remove(Screen.Lyrics)
			}
		}
	}

	val preferenceManager = koinInject<PreferenceManager>()
	if (preferenceManager.lyricsKeepAlive) {
		KeepScreenOn()
	}

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
						data = lyricsState.data,
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
						contentPadding = contentPadding
					)
				}
			}
		}

		if (shareSheetOpen) {
			val lyricsList = lyricsState.data?.lines?.map { line ->
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

	if (pane) {
		Box(Modifier.fillMaxSize()) {
			body(PaddingValues())
			// like the toolbar: first pick the lines, then share them
			Row(Modifier.align(Alignment.TopEnd)) {
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
		}
		return
	}

	SheetScaffold(
		toolbar = { windowInsets ->
			LyricsScreenToolbar(
				onDismissRequest = { animateToDismiss() },
				onShare = { shareSheetOpen = true },
				isSelecting = isSelecting,
				toggleIsSelecting = toggleIsSelecting,
				windowInsets = windowInsets,
				selectedIndices = selectedIndices.toImmutableList()
			)
		},
		toolbarPosition = ToolbarPosition.Top
	) { contentPadding ->
		body(contentPadding)
	}
}
