package eu.depau.loak.domain.models.settings

import androidx.compose.ui.graphics.vector.ImageVector
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.option_list_view_mode_grid
import eu.depau.loak.generated.resources.option_list_view_mode_list
import org.jetbrains.compose.resources.StringResource
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Grid
import eu.depau.loak.icons.outlined.List

enum class ListViewMode(val displayName: StringResource, val icon: ImageVector) {
	Grid(Res.string.option_list_view_mode_grid, Icons.Outlined.Grid),
	List(Res.string.option_list_view_mode_list, Icons.Outlined.List)
}
