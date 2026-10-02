package eu.depau.loak.ui.components.layouts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import org.koin.compose.koinInject
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.models.settings.ToolbarPosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.add
import androidx.compose.ui.Modifier
import eu.depau.loak.ui.util.windowControlsInsets
import eu.depau.loak.ui.util.windowDragArea

@Composable
fun SheetScaffold(
	toolbar: @Composable (windowInsets: WindowInsets) -> Unit,
	toolbarPosition: ToolbarPosition? = null,
	floatingActionButton: @Composable () -> Unit = {},
	content: @Composable (contentPadding: PaddingValues) -> Unit
) {
	val preferenceManager = koinInject<PreferenceManager>()
	val toolbarPosition = toolbarPosition ?: preferenceManager.nowPlayingToolbarPosition
	Scaffold(
		topBar = {
			if (toolbarPosition == ToolbarPosition.Top) Box(Modifier.windowDragArea()) {
				toolbar(
					WindowInsets.systemBars.only(
						WindowInsetsSides.Horizontal + WindowInsetsSides.Top
					).add(windowControlsInsets())
				)
			}
		},
		bottomBar = {
			if (toolbarPosition == ToolbarPosition.Bottom) {
				toolbar(
					WindowInsets.systemBars.only(
						WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
					)
				)
			}
		},
		floatingActionButton = floatingActionButton,
		containerColor = Color.Transparent,
		content = content
	)
}
