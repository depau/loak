package eu.depau.loak

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.SnackbarDuration
import kotlinx.coroutines.CancellationException
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.detailPane
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy.Companion.listPane
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplay.popTransitionSpec
import androidx.navigation3.ui.NavDisplay.predictivePopTransitionSpec
import androidx.navigation3.ui.NavDisplay.transitionSpec
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.LocalSharedTransitionScope
import eu.depau.loak.di.LocalSnackBarState
import eu.depau.loak.di.PlatformType
import eu.depau.loak.di.isLandscape
import eu.depau.loak.di.rememberPlatformContext
import eu.depau.loak.domain.manager.BottomBarScrollManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.generated.BuildInfo
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.sheets.ChangelogSheet
import eu.depau.loak.ui.components.snackbars.LoakSnackBar
import eu.depau.loak.ui.navigation.BottomSheetSceneStrategy
import eu.depau.loak.ui.navigation.NowPlayingSceneStrategy
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.album.AlbumListScreen
import eu.depau.loak.ui.screens.artist.ArtistDetailScreen
import eu.depau.loak.ui.screens.artist.ArtistListScreen
import eu.depau.loak.ui.screens.collection.CollectionDetailScreen
import eu.depau.loak.ui.screens.genre.GenreDetailScreen
import eu.depau.loak.ui.screens.genre.GenreListScreen
import eu.depau.loak.ui.screens.imageView.ImageViewScreen
import eu.depau.loak.ui.screens.library.LibraryScreen
import eu.depau.loak.ui.screens.login.LoginScreen
import eu.depau.loak.ui.screens.lyrics.LyricsScreen
import eu.depau.loak.ui.screens.nowPlaying.NowPlayingScreen
import eu.depau.loak.ui.screens.nowPlaying.PlaybackSpeedScreen
import eu.depau.loak.ui.screens.playlist.PlaylistListScreen
import eu.depau.loak.ui.screens.queue.QueueScreen
import eu.depau.loak.ui.screens.radio.RadioListScreen
import eu.depau.loak.ui.screens.search.SearchScreen
import eu.depau.loak.ui.screens.settings.AudioEffectsScreen
import eu.depau.loak.ui.screens.settings.BottomBarScreen
import eu.depau.loak.ui.screens.settings.FontsScreen
import eu.depau.loak.ui.screens.settings.SettingsAboutScreen
import eu.depau.loak.ui.screens.settings.SettingsAppIconScreen
import eu.depau.loak.ui.screens.settings.SettingsAppearanceScreen
import eu.depau.loak.ui.screens.settings.SettingsCustomHeadersScreen
import eu.depau.loak.ui.screens.settings.SettingsDataStorageScreen
import eu.depau.loak.ui.screens.settings.SettingsDeveloperScreen
import eu.depau.loak.ui.screens.settings.SettingsDownloadQualityScreen
import eu.depau.loak.ui.screens.settings.SettingsEqualiserScreen
import eu.depau.loak.ui.screens.settings.SettingsLogsScreen
import eu.depau.loak.ui.screens.settings.SettingsNowPlayingScreen
import eu.depau.loak.ui.screens.settings.SettingsPlaybackScreen
import eu.depau.loak.ui.screens.settings.SettingsScreen
import eu.depau.loak.ui.screens.settings.SettingsStreamingQualityScreen
import eu.depau.loak.ui.screens.settings.SettingsThemesScreen
import eu.depau.loak.ui.screens.share.ShareListScreen
import eu.depau.loak.ui.screens.song.SongDetailScreen
import eu.depau.loak.ui.screens.song.SongDetailSheet
import eu.depau.loak.ui.screens.song.SongListScreen
import eu.depau.loak.ui.screens.starred.StarredScreen
import eu.depau.loak.ui.components.layouts.AppNavigationRail
import eu.depau.loak.ui.theme.LoakTheme
import eu.depau.loak.ui.util.Material3Transitions

@OptIn(ExperimentalSerializationApi::class)
private val config = SavedStateConfiguration {
	serializersModule = SerializersModule {
		polymorphic(NavKey::class) {
			subclassesOfSealed<Screen>()
		}
	}
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun App() {
	val platformContext = rememberPlatformContext()
	val sessionManager = koinInject<SessionManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val isLoggedIn by sessionManager.isLoggedIn.collectAsStateWithLifecycle()
	val backStack = rememberNavBackStack(
		config, if (isLoggedIn) {
			Screen.Library()
		} else {
			Screen.Login
		}
	)
	val snackBarState = remember { SnackbarHostState() }
	val snackBarManager = koinInject<SnackBarManager>()

	LaunchedEffect(Unit) {
		snackBarManager.events.collectLatest { event ->
			val result = try {
				snackBarState.showSnackbar(
					message = getString(event.resource, *event.args.toTypedArray()),
					actionLabel = event.action?.let { getString(it) },
					// Short (4 s) is the Material default; Compose extends it for users who set a
					// longer accessibility timeout
					duration = SnackbarDuration.Short,
					withDismissAction = event.dismissible
				)
			} catch (e: CancellationException) {
				// replaced by a newer snackbar or the app went away
				event.onDismiss?.invoke()
				throw e
			}
			if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
			else event.onDismiss?.invoke()
		}
	}

	val density = LocalDensity.current
	val layoutDirection = LocalLayoutDirection.current
	val scrollManager = remember {
		BottomBarScrollManager(with(density) { 50.dp.toPx() })
	}

	var appStarted by rememberSaveable { mutableStateOf(false) }

	LaunchedEffect(Unit) {
		if (!appStarted) {
			appStarted = true
			if (preferenceManager.explicitContentPlayback == ExplicitContentPlayback.SkipForThisSession) {
				preferenceManager.explicitContentPlayback = ExplicitContentPlayback.Allowed
			}
		}
	}

	SharedTransitionLayout {
		CompositionLocalProvider(
			LocalPlatformContext provides platformContext,
			LocalNavStack provides backStack,
			LocalSnackBarState provides snackBarState,
			LocalSharedTransitionScope provides this@SharedTransitionLayout,
			LocalBottomBarScrollManager provides scrollManager
		) {
			LoakTheme {
				Scaffold(
					modifier = Modifier.nestedScroll(scrollManager.connection),
					snackbarHost = {
						// sit above the mini player and nav bar when a screen shows them
						val barHeight = scrollManager.barHeights.values.maxOrNull() ?: 0.dp
						SnackbarHost(
							hostState = snackBarState,
							modifier = Modifier
								.padding(bottom = barHeight)
								.consumeWindowInsets(PaddingValues(bottom = barHeight))
						) { snackBarData ->
							LoakSnackBar(snackBarData = snackBarData)
						}
					},
					contentWindowInsets = WindowInsets()
				) { contentPadding ->
					Row {
						// wider windows: the tabs move from each screen's bottom bar into a rail
						if (isLoggedIn && platformContext.isLandscape()) AppNavigationRail()
						NavDisplay(
							modifier = Modifier
								.weight(1f)
								.padding(
									start = contentPadding
										.calculateStartPadding(layoutDirection),
									end = contentPadding
										.calculateEndPadding(layoutDirection)
								)
								.fillMaxSize()
								.background(MaterialTheme.colorScheme.surface),
							backStack = backStack,
							sceneStrategies = listOf(
								remember { NowPlayingSceneStrategy() },
								remember { BottomSheetSceneStrategy() },
								rememberListDetailSceneStrategy()
							),
							entryDecorators = listOf(
								rememberSaveableStateHolderNavEntryDecorator(),

								// makes it so that ViewModels get destroyed if their
								// associated screen is removed from the back stack
								//
								// this might not always be desirable, so the
								// `PersistentViewModelStoreOwner` class is used for
								// certain ViewModels to work around this
								rememberViewModelStoreNavEntryDecorator()
							),
							onBack = {
								if (backStack.size >= 2) {
									backStack.removeLastOrNull()
								}
							},
							entryProvider = entryProvider(backStack),
							sharedTransitionScope = this@SharedTransitionLayout,
							transitionSpec = {
								if (platformContext.platformType == PlatformType.Web) {
									// ponytail: instant transitions on web avoid LookaheadPass crashes & stuck layout
									ContentTransform(EnterTransition.None, ExitTransition.None)
								} else {
									Material3Transitions.SharedXAxisEnterTransition(
										density
									) togetherWith Material3Transitions.SharedXAxisExitTransition(
										density
									)
								}
							},
							popTransitionSpec = {
								if (platformContext.platformType == PlatformType.Web) {
									// ponytail: instant transitions on web avoid LookaheadPass crashes & stuck layout
									ContentTransform(EnterTransition.None, ExitTransition.None)
								} else {
									Material3Transitions.SharedXAxisPopEnterTransition(
										density
									) togetherWith Material3Transitions.SharedXAxisPopExitTransition(
										density
									)
								}
							},
							predictivePopTransitionSpec = {
								if (preferenceManager.enablePredictiveBackAnimations) {
									slideInHorizontally(
										animationSpec = tween(300, easing = EaseOutQuart),
										initialOffsetX = { -it }
									) togetherWith slideOutHorizontally(
										animationSpec = tween(300, easing = EaseOutQuart),
										targetOffsetX = { it }
									)
								} else {
									ContentTransform(EnterTransition.None, ExitTransition.None)
								}
							}
						)
					}
				}
				// version check is annoying to do on iOS
				if (preferenceManager.checkForUpdates
					&& platformContext.platformType == PlatformType.Android
					&& !BuildInfo.FDROID
				) {
					ChangelogSheet()
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
private fun entryProvider(
	backStack: NavBackStack<NavKey>
): (NavKey) -> (NavEntry<NavKey>) {
	val fadeSpec = ContentTransform(fadeIn(), fadeOut())

	val navtabMetadata = if (backStack.size == 1)
		transitionSpec { fadeSpec }
			.plus(popTransitionSpec { fadeSpec })
			.plus(predictivePopTransitionSpec { fadeSpec })
	else emptyMap()
	val imageViewMetadata = transitionSpec { ContentTransform(fadeIn(), ExitTransition.None) }
		.plus(popTransitionSpec { ContentTransform(EnterTransition.None, fadeOut()) })
		.plus(predictivePopTransitionSpec { ContentTransform(EnterTransition.None, fadeOut()) })

	return androidx.navigation3.runtime.entryProvider {
		// tabs
		entry<Screen.Library>(metadata = navtabMetadata) {
			LibraryScreen()
		}
		entry<Screen.Starred>(metadata = navtabMetadata) {
			StarredScreen()
		}
		entry<Screen.AlbumList>(metadata = navtabMetadata) { key ->
			AlbumListScreen(key.nested, key.listType)
		}
		entry<Screen.PlaylistList>(metadata = navtabMetadata) { key ->
			PlaylistListScreen(key.nested)
		}
		entry<Screen.ArtistList>(metadata = navtabMetadata) { key ->
			ArtistListScreen(key.nested, key.listType)
		}
		entry<Screen.GenreList>(metadata = navtabMetadata) { key ->
			GenreListScreen(key.nested)
		}
		entry<Screen.GenreDetail> { key ->
			GenreDetailScreen(key.genreName)
		}
		entry<Screen.SongList>(metadata = navtabMetadata) { key ->
			SongListScreen(key.nested, key.listType)
		}

		entry<Screen.RadioList>(metadata = navtabMetadata) { key ->
			RadioListScreen(key.nested)
		}

		// misc
		entry<Screen.Login> {
			LoginScreen()
		}
		entry<Screen.ImageView>(metadata = imageViewMetadata) { key ->
			ImageViewScreen(
				coverArtId = key.coverArtId,
				title = key.title,
				sharedTransitionKey = key.sharedTransitionKey
			)
		}
		entry<Screen.NowPlaying>(
			metadata = NowPlayingSceneStrategy.bottomSheet(maxWidth = Dp.Unspecified)
		) {
			NowPlayingScreen()
		}
		entry<Screen.Lyrics>(metadata = NowPlayingSceneStrategy.bottomSheet(isTransparent = true)) {
			val player = koinInject<MediaPlayerViewModel>()
			val playerState by player.uiState.collectAsState()
			val song = playerState.currentSong
			LyricsScreen(song)
		}
		entry<Screen.Queue>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
			QueueScreen()
		}
		entry<Screen.PlaybackSpeed>(metadata = BottomSheetSceneStrategy.bottomSheet()) {
			PlaybackSpeedScreen()
		}
		entry<Screen.CollectionDetail> { key ->
			CollectionDetailScreen(key.collectionId, key.tab)
		}
		entry<Screen.SongDetailScreen> { key ->
			SongDetailScreen(
				songId = key.songId,
				initialCoverArtId = key.coverArtId
			)
		}
		entry<Screen.SongDetailSheet>(
			metadata = { key ->
				BottomSheetSceneStrategy.bottomSheet(coverArtId = key.coverArtId)
			}
		) { key ->
			SongDetailSheet(
				songId = key.songId,
				initialCoverArtId = key.coverArtId
			)
		}
		entry<Screen.Search>(metadata = navtabMetadata) { key ->
			SearchScreen(key.nested)
		}
		entry<Screen.ShareList> {
			ShareListScreen()
		}
		entry<Screen.ArtistDetail> { key ->
			ArtistDetailScreen(key.artist)
		}

		// settings
		entry<Screen.Settings.Root>(metadata = listPane("settings")) {
			SettingsScreen()
		}
		entry<Screen.Settings.Appearance>(metadata = detailPane("settings")) {
			SettingsAppearanceScreen()
		}
		entry<Screen.Settings.BottomAppBar>(metadata = detailPane("settings")) {
			BottomBarScreen()
		}
		entry<Screen.Settings.NowPlaying>(metadata = detailPane("settings")) {
			SettingsNowPlayingScreen()
		}
		entry<Screen.Settings.Playback>(metadata = detailPane("settings")) {
			SettingsPlaybackScreen()
		}
		entry<Screen.Settings.Effects>(metadata = detailPane("settings")) {
			AudioEffectsScreen()
		}
		entry<Screen.Settings.Developer>(metadata = detailPane("settings")) {
			SettingsDeveloperScreen()
		}
		entry<Screen.Settings.About>(metadata = detailPane("settings")) {
			SettingsAboutScreen()
		}
		entry<Screen.Settings.DataStorage>(metadata = detailPane("settings")) {
			SettingsDataStorageScreen()
		}
		entry<Screen.Settings.Fonts> {
			FontsScreen()
		}
		entry<Screen.Settings.Themes> {
			SettingsThemesScreen()
		}
		entry<Screen.Settings.CustomHeaders> {
			SettingsCustomHeadersScreen()
		}
		entry<Screen.Settings.StreamingQuality> {
			SettingsStreamingQualityScreen()
		}
		entry<Screen.Settings.DownloadQuality> {
			SettingsDownloadQualityScreen()
		}
		entry<Screen.Settings.Logs> {
			SettingsLogsScreen()
		}
		entry<Screen.Settings.AppIcon>(metadata = detailPane("settings")) {
			SettingsAppIconScreen()
		}
		entry<Screen.Settings.Equaliser> {
			SettingsEqualiserScreen()
		}
	}
}
