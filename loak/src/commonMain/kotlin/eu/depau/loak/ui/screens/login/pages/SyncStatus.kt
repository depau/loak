package eu.depau.loak.ui.screens.login.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_syncing
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.ui.core.LoginUiState

@Composable
fun LoginScreenSyncStatus(
	loginUiState: LoginUiState
) {
	AnimatedVisibility(
		modifier = Modifier.fillMaxWidth(),
		visible = loginUiState is LoginUiState.Syncing,
		enter = expandVertically() + fadeIn(),
		exit = shrinkVertically() + fadeOut()
	) {
		val syncState = loginUiState as? LoginUiState.Syncing
		Text(
			text = stringResource(syncState?.message ?: Res.string.info_syncing),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.primary,
			textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth(),
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	}
}
