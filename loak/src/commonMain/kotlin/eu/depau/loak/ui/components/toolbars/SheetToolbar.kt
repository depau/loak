package eu.depau.loak.ui.components.toolbars

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalPlatformContext
import eu.depau.loak.ui.theme.defaultFont

@Composable
fun SheetToolbar(
	modifier: Modifier = Modifier,
	windowInsets: WindowInsets,
	title: @Composable () -> Unit = {},
	navigationIcon: @Composable () -> Unit,
	actions: @Composable () -> Unit = {},
	/** Overrides the default: none on wide windows, 24 dp on compact ones. */
	verticalPadding: Dp? = null
) {
	val platformContext = LocalPlatformContext.current
	val isLandscape = platformContext.sizeClass.widthSizeClass > WindowWidthSizeClass.Compact
	Row(
		modifier = modifier
			.fillMaxWidth()
			.padding(
				horizontal = 16.dp,
				vertical = verticalPadding ?: if (isLandscape) 0.dp else 24.dp
			)
			.windowInsetsPadding(windowInsets),
		horizontalArrangement = Arrangement.SpaceBetween,
		verticalAlignment = Alignment.CenterVertically
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			navigationIcon()
			CompositionLocalProvider(
				LocalTextStyle provides MaterialTheme.typography.bodyMedium
					.copy(
						fontFamily = defaultFont(round = 100f),
						shadow = Shadow(
							color = MaterialTheme.colorScheme.inverseOnSurface,
							offset = Offset(0f, 4f),
							blurRadius = 10f
						)
					)
			) {
				title()
			}
		}
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(4.dp)
		) {
			actions()
		}
	}
}
