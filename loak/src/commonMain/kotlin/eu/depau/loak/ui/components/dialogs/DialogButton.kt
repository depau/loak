package eu.depau.loak.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** A Material 3 dialog action: a text button, tinted with the error colour when [destructive]. */
@Composable
fun DialogButton(
	onClick: () -> Unit,
	enabled: Boolean = true,
	destructive: Boolean = false,
	interactionSource: MutableInteractionSource? = null,
	content: @Composable RowScope.() -> Unit
) {
	val contentColor = when {
		!enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
		destructive -> MaterialTheme.colorScheme.error
		else -> MaterialTheme.colorScheme.primary
	}

	CompositionLocalProvider(
		LocalContentColor provides contentColor,
		LocalTextStyle provides MaterialTheme.typography.labelLarge
	) {
		Box(
			modifier = Modifier
				.clip(CircleShape)
				.heightIn(min = 40.dp)
				.clickable(
					onClick = onClick,
					enabled = enabled,
					role = Role.Button,
					interactionSource = interactionSource
				)
				.padding(horizontal = 12.dp)
		) {
			Row(
				modifier = Modifier.align(Alignment.Center),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically,
				content = content
			)
		}
	}
}
