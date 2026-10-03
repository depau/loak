package eu.depau.loak

import eu.depau.loak.ui.screens.alchemy.AlchemyScreen
import eu.depau.loak.ui.screens.alchemy.AskAiScreen
import eu.depau.loak.ui.screens.explore.ExploreScreen
import eu.depau.loak.ui.screens.explore.MoodScreen
import eu.depau.loak.ui.screens.explore.SoundMapScreen
import eu.depau.loak.ui.screens.alchemy.DescribeMixScreen
import eu.depau.loak.ui.screens.alchemy.SongPathScreen
import eu.depau.loak.ui.screens.login.INTEGRATIONS_VERSION
import eu.depau.loak.ui.screens.login.SetupIntegrationsScreen
import eu.depau.loak.ui.screens.library.LibraryScreen
import eu.depau.loak.ui.screens.library.DownloadsScreen
import eu.depau.loak.ui.screens.settings.AudioMuseSettingsScreen
import eu.depau.loak.ui.screens.settings.AudioMuseConnectScreen
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.runtime.snapshotFlow
import eu.depau.loak.ui.navigation.ForwardHistory
import eu.depau.loak.ui.navigation.AppActions
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.runtime.DisposableEffect
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import eu.depau.loak.di.LocalMouseInUse
import eu.depau.loak.ui.components.layouts.refreshNavEntryDecorator
import eu.depau.loak.util.isShortcutPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusTarget
import eu.depau.loak.ui.screens.queue.QueuePane
import eu.depau.loak.di.isExpanded
import eu.depau.loak.di.LocalQueuePaneOpen
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import org.jetbrains.compose.resources.stringResource
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
import eu.depau.loak.domain.manager.PermissionManager
import eu.depau.loak.domain.models.settings.ExplicitContentPlayback
import eu.depau.loak.shared.MediaPlayerViewModel
import eu.depau.loak.ui.components.dialogs.DialogButton
import eu.depau.loak.ui.components.dialogs.FormDialog
import eu.depau.loak.ui.components.sheets.ChangelogSheet
import eu.depau.loak.ui.components.sheets.DesktopUpdateSheet
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
import eu.depau.loak.ui.screens.home.HomeScreen
import eu.depau.loak.ui.screens.login.LoginScreen
import eu.depau.loak.ui.screens.lyrics.LyricsScreen
import eu.depau.loak.ui.screens.nowPlaying.PlaybackSpeedScreen
import eu.depau.loak.ui.screens.playlist.PlaylistListScreen
import eu.depau.loak.ui.screens.queue.QueueScreen
import eu.depau.loak.ui.screens.radio.RadioListScreen
import eu.depau.loak.ui.screens.search.SearchScreen
import eu.depau.loak.util.instanceHost
import eu.depau.loak.util.isPrivateHost
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Error
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_open_settings
import eu.depau.loak.generated.resources.notice_local_network_denied
import eu.depau.loak.generated.resources.subtitle_local_network_denied
import eu.depau.loak.ui.screens.settings.AudioEffectsScreen
import eu.depau.loak.ui.screens.settings.BottomBarScreen
import eu.depau.loak.ui.screens.settings.TabsScreen
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
import eu.depau.loak.ui.screens.settings.SettingsServerScreen
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
import eu.depau.loak.ui.util.WindowChromeHost
import eu.depau.loak.ui.screens.queue.queuePaneFits
import eu.depau.loak.ui.screens.nowPlaying.LocalPlayerSheet
import eu.depau.loak.ui.screens.nowPlaying.PlayerLayer
import eu.depau.loak.ui.util.LocalRootFocus
import eu.depau.loak.ui.screens.nowPlaying.PlayerSheetState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalSerializationApi::class)
private val config = SavedStateConfiguration {
	serializersModule = SerializersModule {
		polymorphic(NavKey::class) {
			subclassesOfSealed<Screen>()
		}
	}
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
/**
 * @param menuBar the platform's menu bar, given the app's actions (the macOS one on desktop).
 */
@Composable
fun App(menuBar: @Composable (AppActions) -> Unit = {}) {
	val platformContext = rememberPlatformContext()
	val sessionManager = koinInject<SessionManager>()
	val preferenceManager = koinInject<PreferenceManager>()
	val isLoggedIn by sessionManager.isLoggedIn.collectAsStateWithLifecycle()
	val backStack = rememberNavBackStack(
		config, if (isLoggedIn) {
			Screen.Home()
		} else {
			Screen.Login
		}
	)
	// the app's chrome (rail, queue pane) waits until the login screen is gone: it stays up,
	// syncing the library, for a while after the session is already valid
	val inApp = isLoggedIn && backStack.firstOrNull() != Screen.Login
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
	val queuePaneOpen = remember { mutableStateOf(preferenceManager.queuePaneOpen) }
	LaunchedEffect(queuePaneOpen.value) { preferenceManager.queuePaneOpen = queuePaneOpen.value }
	val mediaPlayer = koinInject<MediaPlayerViewModel>()
	val rootFocus = remember { FocusRequester() }
	// keyboard and mouse back: the same path as Esc and the system back gesture
	val backInput = remember { DirectNavigationEventInput() }
	val dispatcherOwner = rememberNavigationEventDispatcherOwner(
		parent = LocalNavigationEventDispatcherOwner.current
	)
	val backDispatcher = dispatcherOwner.navigationEventDispatcher
	DisposableEffect(backDispatcher) {
		backDispatcher.addInput(backInput)
		onDispose { backDispatcher.removeInput(backInput) }
	}
	val forwardHistory = remember { ForwardHistory() }
	LaunchedEffect(backStack) {
		var old = backStack.toList()
		snapshotFlow { backStack.toList() }.collect {
			forwardHistory.onChange(old, it)
			old = it
		}
	}
	val actions = AppActions(backStack, backInput, forwardHistory, mediaPlayer, inApp)
	val playerScope = rememberCoroutineScope()
	val playerSheet = remember(backStack) { PlayerSheetState(playerScope, backStack) }
	menuBar(actions)
	var mouseInUse by remember {
		mutableStateOf(platformContext.platformType.let { it == PlatformType.Desktop || it == PlatformType.Web })
	}
	LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

	LaunchedEffect(Unit) {
		if (!appStarted) {
			appStarted = true
			if (preferenceManager.explicitContentPlayback == ExplicitContentPlayback.SkipForThisSession) {
				preferenceManager.explicitContentPlayback = ExplicitContentPlayback.Allowed
			}
		}
	}

	// On a restored (already-logged-in) session the login screen never shows, so the
	// ACCESS_LOCAL_NETWORK prompt it triggers is skipped. Android 16+ requires that
	// permission to reach LAN hosts, so when the configured server is on a private
	// address ask for it on startup instead. The login path still handles the
	// fresh-login case on its own.
	val permissionManager = koinInject<PermissionManager>()
	var localNetworkDenied by rememberSaveable { mutableStateOf(false) }
	// something new among the integrations since this user last looked: show the page once
	LaunchedEffect(Unit) {
		if (isLoggedIn && preferenceManager.integrationsSeen < INTEGRATIONS_VERSION) {
			backStack.add(Screen.SetupIntegrations)
		}
	}
	LaunchedEffect(Unit) {
		if (platformContext.platformType != PlatformType.Android) return@LaunchedEffect
		if (!isLoggedIn) return@LaunchedEffect
		if (!isPrivateHost(instanceHost(sessionManager.instanceUrl))) return@LaunchedEffect
		if (!permissionManager.requestLocalNetworkPermission()) {
			localNetworkDenied = true
		}
	}

	SharedTransitionLayout {
		CompositionLocalProvider(
			LocalNavigationEventDispatcherOwner provides dispatcherOwner,
			LocalPlatformContext provides platformContext,
			LocalNavStack provides backStack,
			LocalSnackBarState provides snackBarState,
			LocalSharedTransitionScope provides this@SharedTransitionLayout,
			LocalBottomBarScrollManager provides scrollManager,
			LocalQueuePaneOpen provides queuePaneOpen,
			LocalMouseInUse provides mouseInUse,
			LocalPlayerSheet provides playerSheet,
			LocalRootFocus provides rootFocus
		) {
			LoakTheme {
				Box(Modifier.fillMaxSize()) {
				WindowChromeHost(paneOpen = inApp && queuePaneFits() && queuePaneOpen.value) {
					Scaffold(
						modifier = Modifier
							.nestedScroll(scrollManager.connection)
							// keyboards (desktop, web, tablets with one): F5 / Ctrl+R refresh the
							// screen, / and Ctrl+F search, Ctrl+, opens settings, Alt+Left and
							// Cmd+[ go back (Esc already does), Alt+Right and Cmd+] go forward,
							// Ctrl+arrows skip tracks; space and the media keys control playback
							// unless a text field or a focused button takes precedence. Media keys
							// are consumed on key-down so a key held down doesn't repeat-toggle.
							.focusRequester(rootFocus)
							// Esc is back, before focus handling gets it: otherwise it only clears focus and
							// nothing closes (the player, karaoke, the queue sheet). Popups (menus, sheets,
							// dialogs) get their own key events and close themselves.
							.onPreviewKeyEvent { event ->
								if (event.key != Key.Escape) return@onPreviewKeyEvent false
								if (event.type == KeyEventType.KeyDown) actions.back()
								true
							}
							.focusTarget()
							.onKeyEvent { event ->
								if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
								val shortcut = event.isShortcutPressed
								val key = event.key
								val action: (() -> Unit)? = when {
									key == Key.F5 || shortcut && key == Key.R -> actions::refresh
									event.isAltPressed && key == Key.DirectionLeft ||
										shortcut && key == Key.LeftBracket -> actions::back
									event.isAltPressed && key == Key.DirectionRight ||
										shortcut && key == Key.RightBracket ||
										key == Key.Forward -> actions::forward
									!inApp -> null
									// '/' by character: it's shifted on many layouts
									event.utf16CodePoint == '/'.code ||
										shortcut && key == Key.F -> actions::search
									shortcut && key == Key.Comma -> actions::settings
									mediaPlayer.uiState.value.currentSong == null -> null
									key == Key.Spacebar || key == Key.MediaPlayPause -> actions::playPause
									key == Key.MediaPlay -> actions::play
									key == Key.MediaPause || key == Key.MediaStop -> actions::pause
									key == Key.MediaNext ||
										shortcut && key == Key.DirectionRight -> actions::next
									key == Key.MediaPrevious ||
										shortcut && key == Key.DirectionLeft -> actions::previous
									else -> null
								}
								action?.invoke()
								action != null
							}
							// any mouse pointer, even just hovering, turns on mouse affordances
							.pointerInput(mouseInUse) {
								if (mouseInUse) return@pointerInput
								awaitPointerEventScope {
									while (true) {
										val event = awaitPointerEvent(PointerEventPass.Initial)
										if (event.changes.any { it.type == PointerType.Mouse }) mouseInUse = true
									}
								}
							}
							// desktop mice's back and forward buttons (Android turns them into keys)
							.pointerInput(Unit) {
								if (platformContext.platformType != PlatformType.Desktop) return@pointerInput
								awaitPointerEventScope {
									var handlingNavButton = false
									while (true) {
										val event = awaitPointerEvent(PointerEventPass.Initial)
										when (event.type) {
											PointerEventType.Press -> {
												if (event.buttons.isBackPressed) {
													handlingNavButton = true
													event.changes.forEach { it.consume() }
													actions.back()
												} else if (event.buttons.isForwardPressed) {
													handlingNavButton = true
													event.changes.forEach { it.consume() }
													actions.forward()
												}
											}
											PointerEventType.Move -> {
												if (handlingNavButton) {
													event.changes.forEach { it.consume() }
												}
											}
											PointerEventType.Release -> {
												if (handlingNavButton) {
													event.changes.forEach { it.consume() }
													if (!event.buttons.isBackPressed && !event.buttons.isForwardPressed) {
														handlingNavButton = false
													}
												}
											}
										}
									}
								}
							},
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
							if (inApp && platformContext.isLandscape()) AppNavigationRail()
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
									rememberViewModelStoreNavEntryDecorator(),
									remember { refreshNavEntryDecorator() }
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
							AnimatedVisibility(
								// narrower windows hide it but keep it open, so it comes back when widened
								inApp && queuePaneFits() && queuePaneOpen.value,
								enter = expandHorizontally(),
								exit = shrinkHorizontally()
							) {
								QueuePane()
							}
						}
					}
				}
				// the player covers the whole window, rail and queue pane included; it draws the
				// window controls itself
				if (inApp) PlayerLayer(playerSheet)
				// version check is annoying to do on iOS
					if (preferenceManager.checkForUpdates
						&& platformContext.platformType == PlatformType.Android
					) {
						ChangelogSheet()
					} else if (preferenceManager.checkForUpdates
						&& platformContext.platformType == PlatformType.Desktop
					) {
						DesktopUpdateSheet()
					}

					if (localNetworkDenied) {
						FormDialog(
							onDismissRequest = { localNetworkDenied = false },
							icon = { Icon(Icons.Outlined.Error, null) },
							title = { Text(stringResource(Res.string.notice_local_network_denied)) },
							content = { Text(stringResource(Res.string.subtitle_local_network_denied)) },
							buttons = {
								DialogButton(
									onClick = {
										localNetworkDenied = false
										permissionManager.openPermissionsSettings()
									},
								) {
									Text(stringResource(Res.string.action_open_settings))
								}
							}
						)
					}
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
		entry<Screen.Home>(metadata = navtabMetadata) {
			HomeScreen()
		}
		entry<Screen.Starred>(metadata = navtabMetadata) {
			StarredScreen()
		}
		entry<Screen.AlbumList>(metadata = navtabMetadata) { key ->
			AlbumListScreen(key.nested, key.listType)
		}
		entry<Screen.PlaylistList>(metadata = navtabMetadata) { key ->
			PlaylistListScreen(key.nested, key.kind)
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
		entry<Screen.SetupIntegrations> {
			SetupIntegrationsScreen()
		}
		entry<Screen.ImageView>(metadata = imageViewMetadata) { key ->
			ImageViewScreen(
				coverArtId = key.coverArtId,
				title = key.title,
				sharedTransitionKey = key.sharedTransitionKey
			)
		}
		// the player itself is PlayerLayer, over the whole window; this entry only marks it open
		entry<Screen.NowPlaying>(metadata = NowPlayingSceneStrategy.player()) {}
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
		entry<Screen.Alchemy> {
			AlchemyScreen(it.seed)
		}
		entry<Screen.AskAI> {
			AskAiScreen(it.prompt)
		}
		entry<Screen.DescribeMix> {
			DescribeMixScreen(it.prompt)
		}
		entry<Screen.SongPath> {
			SongPathScreen()
		}
		entry<Screen.Explore>(metadata = navtabMetadata) {
			ExploreScreen()
		}
		entry<Screen.Mood> {
			MoodScreen(it.mood)
		}
		entry<Screen.SoundMap> {
			SoundMapScreen()
		}
		entry<Screen.Library>(metadata = navtabMetadata) {
			LibraryScreen()
		}
		entry<Screen.Downloads> {
			DownloadsScreen(it.tab)
		}
		entry<Screen.Settings.Root>(metadata = listPane("settings")) {
			SettingsScreen()
		}
		entry<Screen.Settings.Appearance>(metadata = detailPane("settings")) {
			SettingsAppearanceScreen()
		}
		entry<Screen.Settings.BottomAppBar>(metadata = detailPane("settings")) {
			BottomBarScreen()
		}
		entry<Screen.Settings.Tabs>(metadata = detailPane("settings")) {
			TabsScreen()
		}
		entry<Screen.Settings.NowPlaying>(metadata = detailPane("settings")) {
			SettingsNowPlayingScreen()
		}
		entry<Screen.Settings.Playback>(metadata = detailPane("settings")) {
			SettingsPlaybackScreen()
		}
		entry<Screen.Settings.Server>(metadata = detailPane("settings")) {
			SettingsServerScreen()
		}
		entry<Screen.Settings.AudioMuse>(metadata = detailPane("settings")) {
			AudioMuseSettingsScreen()
		}
		entry<Screen.Settings.AudioMuseConnect>(metadata = detailPane("settings")) {
			AudioMuseConnectScreen()
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
