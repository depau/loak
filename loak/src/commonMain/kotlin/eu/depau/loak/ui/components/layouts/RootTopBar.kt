package eu.depau.loak.ui.components.layouts

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
import eu.depau.loak.domain.models.settings.NavbarConfig
import eu.depau.loak.domain.models.settings.NavbarTab
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.AccountCircle
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.components.common.TooltipBox
import eu.depau.loak.ui.components.sheets.AccountSheet
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.viewmodels.NavtabsViewModel

@Composable
fun RootTopBar(
	title: @Composable () -> Unit,
	scrollBehavior: TopAppBarScrollBehavior,
	actions: @Composable RowScope.() -> Unit = {},
) {
	val navViewModel = koinViewModel<NavtabsViewModel>()
	val navState by navViewModel.state.collectAsState()
	val navConfig = (navState as? UiState.Success)?.data

	MediumFlexibleTopAppBar(
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
		actions = {
			actions()
			Actions(navConfig = navConfig)
		},
		scrollBehavior = scrollBehavior,
		colors = TopAppBarDefaults.topAppBarColors(
			scrolledContainerColor = MaterialTheme.colorScheme.surface
		),
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

	var accountSheetOpen by rememberSaveable { mutableStateOf(false) }

	if (!isSearchEnabled) {
		TooltipBox(stringResource(Res.string.title_search)) {
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

	// wider windows have it at the bottom of the navigation rail
	if (!LocalPlatformContext.current.isLandscape()) TooltipBox(stringResource(Res.string.title_account)) {
		IconButton(onClick = {
			accountSheetOpen = true
		}) {
			Icon(
				imageVector = Icons.Outlined.AccountCircle,
				contentDescription = stringResource(Res.string.title_account)
			)
		}
	}

	if (accountSheetOpen) {
		AccountSheet(onDismissRequest = { accountSheetOpen = false })
	}
}
