package eu.depau.loak.ui.components.common

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Sort
import eu.depau.loak.ui.components.layouts.TopBarButton

@Composable
fun SortButton(
	nested: Boolean,
	sheet: @Composable (onDismissRequest: () -> Unit) -> Unit
) {
	var expanded by remember { mutableStateOf(false) }
	val icon: @Composable () -> Unit = {
		Icon(Icons.Outlined.Sort, contentDescription = null)
	}
	if (nested) {
		TopBarButton(onClick = { expanded = true }) { icon() }
	} else {
		IconButton(onClick = { expanded = true }, content = icon)
	}
	if (expanded) sheet { expanded = false }
}
