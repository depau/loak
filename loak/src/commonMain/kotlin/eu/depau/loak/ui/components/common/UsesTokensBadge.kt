package eu.depau.loak.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.badge_uses_tokens
import eu.depau.loak.generated.resources.info_uses_tokens
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Coin
import eu.depau.loak.ui.theme.warning
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Amber "Uses tokens", only on actions that themselves send something to the AI service set
 * up in AudioMuse-AI. Tapping it says what that means.
 */
@Composable
fun UsesTokensBadge() {
	val info by koinInject<AudioMuseManager>().info.collectAsState()
	val state = rememberTooltipState(isPersistent = true)
	val scope = rememberCoroutineScope()
	val amber = MaterialTheme.colorScheme.warning
	TooltipBox(
		positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Below),
		tooltip = { PlainTooltip { Text(stringResource(Res.string.info_uses_tokens, info?.aiProviderName ?: "")) } },
		state = state
	) {
		Row(
			modifier = Modifier
				.height(20.dp)
				.clip(RoundedCornerShape(6.dp))
				.background(amber.copy(alpha = 0.16f))
				.clickable { scope.launch { state.show() } }
				.padding(horizontal = 6.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(3.dp)
		) {
			Icon(Icons.Outlined.Coin, null, Modifier.size(12.dp), tint = amber)
			Text(
				stringResource(Res.string.badge_uses_tokens),
				style = MaterialTheme.typography.labelSmall,
				fontWeight = FontWeight.SemiBold,
				color = amber
			)
		}
	}
}
