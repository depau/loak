package eu.depau.loak.ui.screens.settings.components

import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemShapes
import androidx.compose.runtime.Composable
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.ChevronForward
import eu.depau.loak.ui.components.common.SegmentedListItem

@Composable
fun SettingsNavItem(
	onClick: () -> Unit,
	enabled: Boolean = true,
	shapes: ListItemShapes,
	leadingContent: @Composable (() -> Unit)? = null,
	supportingContent: @Composable (() -> Unit)? = null,
	content: @Composable () -> Unit
) {
	SegmentedListItem(
		onClick = onClick,
		enabled = enabled,
		shapes = shapes,
		supportingContent = supportingContent,
		leadingContent = leadingContent,
		trailingContent = { Icon(Icons.Outlined.ChevronForward, null) },
		content = content
	)
}
