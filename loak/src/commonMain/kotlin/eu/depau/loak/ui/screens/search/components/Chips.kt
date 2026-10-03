package eu.depau.loak.ui.screens.search.components

import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import eu.depau.loak.icons.filled.Sparkle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Check
import eu.depau.loak.ui.screens.search.SearchCategory

@Composable
fun SearchScreenChips(
	selectedCategory: SearchCategory,
	onCategorySelect: (SearchCategory) -> Unit,
	/** AudioMuse-AI's search by sound, after a divider. */
	soundAvailable: Boolean = false
) {
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState())
			.padding(horizontal = 16.dp)
			.selectableGroup(),
		horizontalArrangement = Arrangement.spacedBy(8.dp)
	) {
		SearchCategory.entries.filter { it != SearchCategory.SOUND || soundAvailable }.forEach { category ->
			if (category == SearchCategory.SOUND) VerticalDivider(Modifier.height(24.dp).align(Alignment.CenterVertically))
			val isSelected = category == selectedCategory
			FilterChip(
				modifier = Modifier
					.animateContentSize(
						if (isSelected)
							MaterialTheme.motionScheme.fastSpatialSpec()
						else MaterialTheme.motionScheme.defaultEffectsSpec()
					),
				selected = isSelected,
				onClick = {
					onCategorySelect(category)
				},
				label = {
					Text(
						stringResource(category.res),
						maxLines = 1
					)
				},
				shape = MaterialTheme.shapes.small,
				leadingIcon = if (isSelected) {
					{
						Icon(
							imageVector = Icons.Outlined.Check,
							contentDescription = null,
							modifier = Modifier.size(FilterChipDefaults.IconSize)
						)
					}
				} else if (category == SearchCategory.SOUND) {
					{ Icon(Icons.Filled.Sparkle, null, Modifier.size(FilterChipDefaults.IconSize)) }
				} else {
					null
				}
			)
		}
	}
}
