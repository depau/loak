package eu.depau.loak.ui.screens.login.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun LoginScreenProgress(
	modifier: Modifier = Modifier,
	isBusy: Boolean
) {
	AnimatedVisibility(
		modifier = modifier.fillMaxWidth(),
		visible = isBusy,
		enter = expandVertically() + fadeIn(),
		exit = shrinkVertically() + fadeOut()
	) {
		LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
	}
}
