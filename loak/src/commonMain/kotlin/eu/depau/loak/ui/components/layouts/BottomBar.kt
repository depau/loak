package eu.depau.loak.ui.components.layouts

import eu.depau.loak.icons.outlined.Explore
import eu.depau.loak.icons.filled.Explore
import eu.depau.loak.generated.resources.title_explore
import eu.depau.loak.icons.outlined.Settings
import eu.depau.loak.generated.resources.title_settings
import eu.depau.loak.generated.resources.title_library
import eu.depau.loak.icons.outlined.LibraryMusic
import eu.depau.loak.icons.filled.LibraryMusic
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.isLandscape
import eu.depau.loak.generated.resources.title_account
import eu.depau.loak.icons.outlined.AccountCircle
import eu.depau.loak.ui.components.common.TooltipBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.title_albums
import eu.depau.loak.generated.resources.title_artists
import eu.depau.loak.generated.resources.title_genres
import eu.depau.loak.generated.resources.title_home
import eu.depau.loak.generated.resources.title_playlists
import eu.depau.loak.generated.resources.title_radios
import eu.depau.loak.generated.resources.title_search
import eu.depau.loak.generated.resources.title_songs
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.models.settings.NavbarConfig
import eu.depau.loak.domain.models.settings.NavbarTab
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Album
import eu.depau.loak.icons.filled.Artist
import eu.depau.loak.icons.filled.Genre
import eu.depau.loak.icons.filled.Home
import eu.depau.loak.icons.filled.Radio
import eu.depau.loak.icons.outlined.Album
import eu.depau.loak.icons.outlined.Artist
import eu.depau.loak.icons.outlined.Genre
import eu.depau.loak.icons.outlined.Home
import eu.depau.loak.icons.outlined.Note
import eu.depau.loak.icons.outlined.PlaylistPlay
import eu.depau.loak.icons.outlined.Radio
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.viewmodels.NavtabsViewModel
import eu.depau.loak.ui.util.animatedTabIconPainter
import eu.depau.loak.ui.viewmodel.RootViewModel
import eu.depau.loak.ui.util.LocalWindowChrome

private enum class NavItem(
	val destination: Screen,
	val icon: ImageVector,
	val iconUnselected: ImageVector = icon,
	val label: StringResource
) {
	HOME(
		destination = Screen.Home(),
		icon = Icons.Filled.Home,
		iconUnselected = Icons.Outlined.Home,
		label = Res.string.title_home
	),
	ALBUMS(
		destination = Screen.AlbumList(),
		icon = Icons.Filled.Album,
		iconUnselected = Icons.Outlined.Album,
		label = Res.string.title_albums
	),
	EXPLORE(
		destination = Screen.Explore,
		icon = Icons.Filled.Explore,
		iconUnselected = Icons.Outlined.Explore,
		label = Res.string.title_explore
	),
	PLAYLISTS(
		destination = Screen.PlaylistList(),
		icon = Icons.Outlined.PlaylistPlay,
		label = Res.string.title_playlists
	),
	ARTISTS(
		destination = Screen.ArtistList(),
		icon = Icons.Filled.Artist,
		iconUnselected = Icons.Outlined.Artist,
		label = Res.string.title_artists
	),
	SEARCH(
		destination = Screen.Search(),
		icon = Icons.Outlined.Search,
		iconUnselected = Icons.Outlined.Search,
		label = Res.string.title_search
	),
	GENRES(
		destination = Screen.GenreList(),
		icon = Icons.Filled.Genre,
		iconUnselected = Icons.Outlined.Genre,
		label = Res.string.title_genres
	),
	SONGS(
		destination = Screen.SongList(),
		icon = Icons.Outlined.Note,
		iconUnselected = Icons.Outlined.Note,
		label = Res.string.title_songs
	),
	RADIOS(
		destination = Screen.RadioList(),
		icon = Icons.Filled.Radio,
		iconUnselected = Icons.Outlined.Radio,
		label = Res.string.title_radios
	),

	/** Always there, last: the catch-all for everything not on the bar. */
	LIBRARY(
		destination = Screen.Library,
		icon = Icons.Filled.LibraryMusic,
		iconUnselected = Icons.Outlined.LibraryMusic,
		label = Res.string.title_library
	)
}

@Composable
fun BottomBar(
	modifier: Modifier = Modifier,
	containerColor: Color = NavigationBarDefaults.containerColor,
	windowInsets: WindowInsets = NavigationBarDefaults.windowInsets,
	enabled: Boolean = true
) {
	val viewModel = koinViewModel<NavtabsViewModel>()
	val backStack = LocalNavStack.current
	val platformContext = LocalPlatformContext.current
	val state by viewModel.state.collectAsState()
	val containerColor by animateColorAsState(containerColor)
	val tabs = ((state as? UiState.Success)?.data ?: NavbarConfig.default)
		.tabs.filter { tab -> tab.visible }

	val onTabSelected = rememberOnTabSelected()

	// wider windows get the navigation rail instead (AppNavigationRail)
	if (platformContext.isLandscape()) return

	if (tabs.isEmpty()) return
	NavigationBar(
		modifier = modifier,
		containerColor = containerColor,
		windowInsets = windowInsets
	) {
		(tabs.map { it.id.navItem() } + NavItem.LIBRARY).forEach { item ->
			val selected = backStack.firstOrNull() == item.destination

			NavigationBarItem(
				selected = selected,
				enabled = enabled,
				onClick = dropUnlessResumed {
					onTabSelected(item.destination)
				},
				icon = {
					if (selected) {
						val painter = animatedTabIconPainter(item.destination)
						if (painter != null) {
							Icon(painter = painter, null)
						} else {
							Icon(item.icon, null)
						}
					} else {
						Icon(item.iconUnselected, null)
					}
				},
				label = {
					Text(
						stringResource(item.label),
						maxLines = 1,
						autoSize = TextAutoSize.StepBased(
							minFontSize = 1.sp,
							maxFontSize = MaterialTheme.typography.labelMedium.fontSize
						)
					)
				}
			)
		}
	}
}

/** The tab's label and icon, as the bar shows them. */
fun NavbarTab.Id.tabLabel() = navItem().label
fun NavbarTab.Id.tabIcon() = navItem().iconUnselected

private fun NavbarTab.Id.navItem() = when (this) {
	NavbarTab.Id.HOME -> NavItem.HOME
	NavbarTab.Id.ALBUMS -> NavItem.ALBUMS
	NavbarTab.Id.PLAYLISTS -> NavItem.PLAYLISTS
	NavbarTab.Id.ARTISTS -> NavItem.ARTISTS
	NavbarTab.Id.SEARCH -> NavItem.SEARCH
	NavbarTab.Id.GENRES -> NavItem.GENRES
	NavbarTab.Id.SONGS -> NavItem.SONGS
	NavbarTab.Id.RADIOS -> NavItem.RADIOS
	NavbarTab.Id.EXPLORE -> NavItem.EXPLORE
}

@Composable
private fun rememberOnTabSelected(): (Screen) -> Unit {
	val rootViewModel = koinInject<RootViewModel>()
	val backStack = LocalNavStack.current
	// A tab tap resets the stack to that tab, so the stack's root is the tab the user is in,
	// and it stays selected on the screens pushed from it
	return { destination: Screen ->
		if (backStack.lastOrNull() == destination) {
			rootViewModel.requestScrollToTop()
		} else {
			backStack.apply {
				clear()
				add(destination)
			}
		}
	}
}

/** The tabs in a navigation rail, for windows wider than a phone. Settings sits at the bottom. */
@Composable
fun AppNavigationRail(modifier: Modifier = Modifier) {
	val viewModel = koinViewModel<NavtabsViewModel>()
	val backStack = LocalNavStack.current
	val state by viewModel.state.collectAsState()
	val tabs = ((state as? UiState.Success)?.data ?: NavbarConfig.default)
		.tabs.filter { tab -> tab.visible }
	val onTabSelected = rememberOnTabSelected()
	NavigationRail(
		modifier = modifier,
		containerColor = MaterialTheme.colorScheme.surface
	) {
		// window controls on the left sit above the rail, in the title bar row
		val chrome = LocalWindowChrome.current
		if (chrome?.hasLeftControls == true) {
			Spacer(Modifier.height(chrome.barHeight))
		}
		Spacer(Modifier.height(12.dp))
		(tabs.map { it.id.navItem() } + NavItem.LIBRARY).forEach { item ->
			val selected = backStack.firstOrNull() == item.destination
			NavigationRailItem(
				selected = selected,
				onClick = dropUnlessResumed { onTabSelected(item.destination) },
				icon = { Icon(if (selected) item.icon else item.iconUnselected, null) },
				label = { Text(stringResource(item.label), maxLines = 1) }
			)
		}
		Spacer(Modifier.weight(1f))
		// Settings sits at the bottom and behaves like a tab
		val inSettings = backStack.firstOrNull() is Screen.Settings
		NavigationRailItem(
			selected = inSettings,
			onClick = dropUnlessResumed { onTabSelected(Screen.Settings.Root) },
			icon = { Icon(Icons.Outlined.Settings, null) },
			label = { Text(stringResource(Res.string.title_settings), maxLines = 1) }
		)
		Spacer(Modifier.height(16.dp))
	}
}
