package eu.depau.loak.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.domain.models.settings.NavbarConfig
import eu.depau.loak.domain.models.settings.NavbarTab
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.DragHandle
import eu.depau.loak.icons.outlined.LibraryMusic
import eu.depau.loak.icons.outlined.Lock
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.components.layouts.tabIcon
import eu.depau.loak.ui.components.layouts.tabLabel
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.screens.settings.viewmodels.NavtabsViewModel
import eu.depau.loak.ui.util.dragHandle
import eu.depau.loak.ui.util.draggableItems
import eu.depau.loak.ui.util.rememberDraggableListState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Which tabs the bar (or the rail, on wide screens) shows, in what order. Library is always
 * there, last; at most [NavbarConfig.MAX_VISIBLE] others.
 */
@Composable
fun TabsScreen() {
	val hideBack = LocalPlatformContext.current.sizeClass.widthSizeClass >= WindowWidthSizeClass.Medium
	val haptic = LocalHapticFeedback.current
	val viewModel = koinViewModel<NavtabsViewModel>()
	val state by viewModel.state.collectAsState()
	val config = (state as? UiState.Success)?.data ?: return
	val visible = config.tabs.count { it.visible }
	val full = visible >= NavbarConfig.MAX_VISIBLE
	val draggable = rememberDraggableListState { from, to ->
		// the list's first two items are the preview and the heading
		viewModel.move(from - 2, to - 2)
		haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
	}

	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(stringResource(Res.string.title_tabs)) },
				navigationAction = { if (!hideBack) NestedTopBarDefaults.NavigationAction() }
			)
		}
	) { innerPadding ->
		LazyColumn(
			state = draggable.listState,
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(16.dp, innerPadding.calculateTopPadding(), 16.dp, innerPadding.calculateBottomPadding() + 16.dp),
			verticalArrangement = Arrangement.spacedBy(4.dp)
		) {
			item(key = "preview") {
				Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
					Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
						config.tabs.filter { it.visible }.forEach { BarItem(it.id.tabIcon(), stringResource(it.id.tabLabel())) }
						BarItem(Icons.Outlined.LibraryMusic, stringResource(Res.string.title_library))
					}
				}
			}
			item(key = "heading") {
				Column(Modifier.padding(4.dp, 16.dp, 4.dp, 8.dp)) {
					Row {
						Text(stringResource(Res.string.title_tabs_on_bar), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
						Text(stringResource(Res.string.info_tabs_count, visible, NavbarConfig.MAX_VISIBLE), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
					}
					Text(
						stringResource(Res.string.info_tabs_limit, NavbarConfig.MAX_VISIBLE),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
			}
			draggableItems(state = draggable, items = config.tabs, key = { it.id }) { tab, isDragging ->
				val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
				// Home can't go: something has to open first
				val enabled = tab.id != NavbarTab.Id.HOME && (tab.visible || !full)
				Surface(shape = MaterialTheme.shapes.large, shadowElevation = elevation, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
					ListItem(
						colors = ListItemDefaults.colors(containerColor = Color.Transparent),
						leadingContent = {
							IconButton(onClick = {}, modifier = Modifier.dragHandle(state = draggable, key = tab.id)) {
								Icon(Icons.Outlined.DragHandle, stringResource(Res.string.action_reorder))
							}
						},
						headlineContent = {
							Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
								Icon(tab.id.tabIcon(), null, Modifier.size(20.dp))
								Text(stringResource(tab.id.tabLabel()))
							}
						},
						supportingContent = if (!tab.visible && full) {
							{ Text(stringResource(Res.string.info_tabs_turn_off)) }
						} else null,
						trailingContent = {
							Switch(checked = tab.visible, enabled = enabled, onCheckedChange = { viewModel.toggleVisibility(tab.id) })
						}
					)
				}
			}
			item(key = "library") {
				Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
					ListItem(
						colors = ListItemDefaults.colors(containerColor = Color.Transparent),
						leadingContent = { Icon(Icons.Outlined.Lock, null, Modifier.padding(12.dp)) },
						headlineContent = {
							Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
								Icon(Icons.Outlined.LibraryMusic, null, Modifier.size(20.dp))
								Text(stringResource(Res.string.title_library))
							}
						},
						supportingContent = { Text(stringResource(Res.string.info_library_fixed)) }
					)
				}
			}
		}
	}
}

@Composable
private fun BarItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
	Column(horizontalAlignment = Alignment.CenterHorizontally) {
		Icon(icon, null)
		Text(label, style = MaterialTheme.typography.labelSmall)
	}
}
