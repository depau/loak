package eu.depau.loak.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalBottomBarScrollManager
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_search_discover
import eu.depau.loak.generated.resources.option_download_queue
import eu.depau.loak.generated.resources.title_discover
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Download
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.RootBottomBar
import eu.depau.loak.ui.components.layouts.RootTopBar
import eu.depau.loak.ui.components.layouts.rootTopBarScrollBehavior
import eu.depau.loak.ui.navigation.Screen
import org.jetbrains.compose.resources.stringResource

/**
 * Discover: search, charts and recommendations for music you don't have. The shelves fill the
 * list in later milestones; for now it holds the search pill only.
 */
@Composable
fun DiscoverScreen() {
	val backStack = LocalNavStack.current
	val scrollBehavior = rootTopBarScrollBehavior()
	// opened from Library rather than as a tab
	val nested = backStack.firstOrNull() != Screen.Discover
	val title = @Composable { Text(stringResource(Res.string.title_discover)) }
	// ponytail: inert until Lidarr's queue screen lands with Discover's downloads
	val queue = @Composable {
		IconButton(onClick = {}, enabled = false) {
			Icon(Icons.Outlined.Download, stringResource(Res.string.option_download_queue))
		}
	}

	Scaffold(
		// only the root bar collapses: without it laid out, the behavior would swallow every scroll
		modifier = if (nested) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
		topBar = {
			// its own search below, not the library's
			if (nested) NestedTopBar(title, actions = { queue() })
			else RootTopBar(title = title, scrollBehavior = scrollBehavior, actions = { queue() }, searchCategory = null)
		},
		bottomBar = { if (!nested) RootBottomBar(scrolled = LocalBottomBarScrollManager.current.isTriggered) }
	) { innerPadding ->
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(
				start = 16.dp, end = 16.dp,
				top = innerPadding.calculateTopPadding(),
				bottom = innerPadding.calculateBottomPadding() + 24.dp
			)
		) {
			item(key = "search") {
				SearchPill(onClick = dropUnlessResumed { backStack.add(Screen.DiscoverSearch) })
			}
		}
	}
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
	Surface(
		onClick = onClick,
		modifier = Modifier.fillMaxWidth().height(56.dp),
		shape = CircleShape,
		color = MaterialTheme.colorScheme.surfaceContainerHigh,
		contentColor = MaterialTheme.colorScheme.onSurfaceVariant
	) {
		Row(
			modifier = Modifier.padding(horizontal = 20.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Icon(Icons.Outlined.Search, contentDescription = null)
			Text(stringResource(Res.string.action_search_discover), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
		}
	}
}
