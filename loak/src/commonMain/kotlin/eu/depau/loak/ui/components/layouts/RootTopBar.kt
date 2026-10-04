package eu.depau.loak.ui.components.layouts

import eu.depau.loak.ui.components.common.LocalAvailability
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.OfflineMode
import eu.depau.loak.icons.outlined.Offline
import eu.depau.loak.generated.resources.info_offline
import eu.depau.loak.generated.resources.action_go_online
import org.koin.compose.koinInject
import eu.depau.loak.icons.outlined.Settings
import eu.depau.loak.generated.resources.title_settings
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.title_account
import eu.depau.loak.generated.resources.title_search
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.di.isLandscape
import eu.depau.loak.di.isExpanded
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import eu.depau.loak.generated.resources.action_search_library
import eu.depau.loak.domain.models.settings.NavbarConfig
import eu.depau.loak.domain.models.settings.NavbarTab
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.AccountCircle
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.components.common.TooltipBox
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.viewmodels.NavtabsViewModel
import androidx.compose.foundation.layout.add
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import eu.depau.loak.di.PlatformType
import eu.depau.loak.ui.util.windowControlsInsets
import eu.depau.loak.ui.screens.queue.QueuePaneToggle
import eu.depau.loak.ui.util.windowDragArea
import eu.depau.loak.generated.resources.action_refresh
import eu.depau.loak.util.withShortcut

/**
 * The scroll behaviour for [RootTopBar]. On desktop the bar is the window's title bar, under
 * the window controls, so it stays pinned: a collapsing state would shrink it even when
 * wrapped in a pinned behaviour, as the bar sizes itself from the state's height offset.
 */
@Composable
fun rootTopBarScrollBehavior(): TopAppBarScrollBehavior =
	if (LocalPlatformContext.current.platformType == PlatformType.Desktop)
		TopAppBarDefaults.pinnedScrollBehavior()
	else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

@Composable
fun RootTopBar(
	title: @Composable () -> Unit,
	scrollBehavior: TopAppBarScrollBehavior,
	actions: @Composable RowScope.() -> Unit = {},
	subtitle: (@Composable () -> Unit)? = null,
) {
	val navViewModel = koinViewModel<NavtabsViewModel>()
	val navState by navViewModel.state.collectAsState()
	val navConfig = (navState as? UiState.Success)?.data
	// offline (by choice or not): say so under the title of every tab
	val subtitle = subtitle ?: if (LocalAvailability.current.online) null else {
		{ Text(stringResource(Res.string.info_offline)) }
	}

	val barActions: @Composable RowScope.() -> Unit = {
		actions()
		Actions(navConfig = navConfig)
	}
	val modifier = Modifier.windowDragArea()
	val windowInsets = TopAppBarDefaults.windowInsets.add(windowControlsInsets())
	val colors = TopAppBarDefaults.topAppBarColors(
		scrolledContainerColor = MaterialTheme.colorScheme.surface
	)

	// desktop: the title stays pinned in the title bar row; the large title that collapses
	// on scroll is a touch idiom
	if (LocalPlatformContext.current.platformType == PlatformType.Desktop) {
		TopAppBar(
			modifier = modifier,
			title = title,
			subtitle = subtitle ?: {},
			actions = barActions,
			// pinned (see rootTopBarScrollBehavior): an unpinned bar is also draggable, which
			// would swallow the mouse drags that move the window
			scrollBehavior = scrollBehavior,
			colors = colors,
			windowInsets = windowInsets,
		)
		return
	}

	MediumFlexibleTopAppBar(
		modifier = modifier,
		title = {
			CompositionLocalProvider(
				LocalTextStyle provides when (LocalTextStyle.current) {
					MaterialTheme.typography.headlineMedium -> MaterialTheme.typography.headlineSmall
					else -> MaterialTheme.typography.titleLarge
				}
			) {
				title()
			}
		},
		subtitle = subtitle,
		actions = barActions,
		scrollBehavior = scrollBehavior,
		colors = colors,
		windowInsets = windowInsets,
	)
}

@Composable
private fun Actions(
	navConfig: NavbarConfig?,
) {
	val backStack = LocalNavStack.current

	val isSearchEnabled = navConfig?.tabs?.any {
		it.id == NavbarTab.Id.SEARCH && it.visible
	} == true


	refreshButtonSlot()?.let { slot ->
		TooltipBox("${stringResource(Res.string.action_refresh)} (F5)") {
			IconButton(onClick = { slot.refresh?.invoke() }) { RefreshIcon(slot) }
		}
	}

	// wide windows: a search field look-alike in every tab's bar, opening the search page
	if (LocalPlatformContext.current.isExpanded()) {
		Surface(
			onClick = dropUnlessResumed { backStack.add(Screen.Search(nested = true)) },
			modifier = Modifier.padding(end = 4.dp).width(360.dp).height(48.dp),
			shape = CircleShape,
			color = MaterialTheme.colorScheme.surfaceContainerHigh,
			contentColor = MaterialTheme.colorScheme.onSurfaceVariant
		) {
			Row(
				modifier = Modifier.padding(horizontal = 16.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				Icon(Icons.Outlined.Search, contentDescription = null)
				Text(
					stringResource(Res.string.action_search_library),
					style = MaterialTheme.typography.bodyLarge,
					maxLines = 1
				)
			}
		}
	} else if (!isSearchEnabled) {
		TooltipBox(withShortcut(stringResource(Res.string.title_search), "F")) {
			IconButton(
				onClick = dropUnlessResumed {
					backStack.add(Screen.Search(nested = true))
				}
			) {
				Icon(
					imageVector = Icons.Outlined.Search,
					contentDescription = stringResource(Res.string.title_search)
				)
			}
		}
	}

	OfflineButton()

	QueuePaneToggle()

	// wider windows have it at the bottom of the navigation rail
	if (!LocalPlatformContext.current.isLandscape()) TooltipBox(stringResource(Res.string.title_settings)) {
		IconButton(onClick = dropUnlessResumed { backStack.add(Screen.Settings.Root) }) {
			Icon(
				imageVector = Icons.Outlined.Settings,
				contentDescription = stringResource(Res.string.title_settings)
			)
		}
	}
}

/** Shown while offline; when that's forced (Settings), a tap goes back online. */
@Composable
private fun OfflineButton() {
	if (LocalAvailability.current.online) return
	val preferenceManager = koinInject<PreferenceManager>()
	val forced = preferenceManager.offlineMode == OfflineMode.Forced
	val label = stringResource(if (forced) Res.string.action_go_online else Res.string.info_offline)
	TooltipBox(label) {
		IconButton(
			onClick = { preferenceManager.offlineMode = OfflineMode.Auto },
			enabled = forced
		) {
			Icon(Icons.Outlined.Offline, contentDescription = label)
		}
	}
}
