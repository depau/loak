package eu.depau.loak.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Flask
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.icons.outlined.SonicPath
import eu.depau.loak.ui.navigation.Screen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Shortcuts to AudioMuse-AI's mix makers. */
@Composable
fun MakeSomethingNew() {
	val info by koinInject<AudioMuseManager>().info.collectAsState()
	val backStack = LocalNavStack.current
	val i = info ?: return
	val colors = MaterialTheme.colorScheme
	val tools = listOfNotNull(
		Tool(Icons.Outlined.Flask, Res.string.title_song_alchemy, Res.string.info_tool_alchemy, colors.tertiaryContainer, colors.onTertiaryContainer, Screen.Alchemy()),
		Tool(Icons.Outlined.Search, Res.string.title_describe_mix, Res.string.info_tool_describe, colors.primaryContainer, colors.onPrimaryContainer, Screen.DescribeMix())
			.takeIf { (i.soundSearch || i.lyricsSearch) && koinInject<PreferenceManager>().audioMuseDescribe },
		Tool(Icons.Outlined.SonicPath, Res.string.title_song_path, Res.string.info_tool_path, colors.secondaryContainer, colors.onSecondaryContainer, Screen.SongPath)
	)
	Column {
		ShelfHeader(stringResource(Res.string.title_make_something_new))
		LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
			items(tools, key = { it.title.key }) { tool ->
				Surface(
					onClick = dropUnlessResumed { backStack.add(tool.screen) },
					modifier = Modifier.width(170.dp),
					shape = RoundedCornerShape(20.dp),
					color = tool.container,
					contentColor = tool.content
				) {
					Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
						Icon(tool.icon, null, Modifier.size(26.dp))
						Column {
							Text(stringResource(tool.title), style = MaterialTheme.typography.titleSmall)
							Text(stringResource(tool.subtitle), style = MaterialTheme.typography.bodySmall, maxLines = 2)
						}
					}
				}
			}
		}
	}
}

private class Tool(
	val icon: ImageVector,
	val title: StringResource,
	val subtitle: StringResource,
	val container: Color,
	val content: Color,
	val screen: Screen
)
