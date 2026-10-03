package eu.depau.loak.ui.screens.nowPlaying.components.controls

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.CoverArtTapAction
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.screens.nowPlaying.LocalPlayerSheet
import eu.depau.loak.ui.screens.nowPlaying.components.NowPlayingArtwork

@Composable
fun NowPlayingArtworkPager(
	modifier: Modifier = Modifier,
	isLandscape: Boolean
) {
	val playerSheet = LocalPlayerSheet.current
	val preferenceManager = koinInject<PreferenceManager>()
	val player = koinInject<MediaPlayerViewModel>()
	val playerState by player.uiState.collectAsState()

	val pagerState = rememberPagerState(
		initialPage = playerState.currentIndex.coerceAtLeast(0),
		pageCount = { playerState.queue.size }
	)
	val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
	var userSwiped by remember { mutableStateOf(false) }

	LaunchedEffect(isDragged) {
		if (isDragged) userSwiped = true
	}

	LaunchedEffect(playerState.currentIndex) {
		if (!isDragged && playerState.currentIndex != -1 && playerState.currentIndex != pagerState.currentPage) {
			pagerState.animateScrollToPage(playerState.currentIndex)
		}
	}

	LaunchedEffect(pagerState) {
		snapshotFlow { pagerState.settledPage }.collect { page ->
			if (userSwiped && page != playerState.currentIndex && page in playerState.queue.indices) {
				val wasPaused = playerState.isPaused
				player.playAt(page)
				if (wasPaused) {
					player.pause()
				}
			}
			userSwiped = false
		}
	}

	HorizontalPager(
		modifier = modifier,
		state = pagerState,
		contentPadding = PaddingValues(horizontal = if (isLandscape) 0.dp else 8.dp),
		userScrollEnabled = preferenceManager.swipeToSkip,
		overscrollEffect = null
	) { page ->
		val song = playerState.queue.getOrNull(page) ?: return@HorizontalPager
		val tapAction = preferenceManager.nowPlayingCoverArtAction
		val enabled = pagerState.settledPage == page
			&& tapAction != CoverArtTapAction.Disabled
		Box(
			modifier = Modifier.fillMaxSize(),
			contentAlignment = Alignment.Center
		) {
			NowPlayingArtwork(
				song = song,
				isLandscape = isLandscape,
				onClick = if (enabled) dropUnlessResumed {
					when (tapAction) {
						CoverArtTapAction.ShowLyrics -> playerSheet.showSheet(lyrics = true)
						CoverArtTapAction.Disabled -> {}
					}
				} else null
			)
		}
	}
}
