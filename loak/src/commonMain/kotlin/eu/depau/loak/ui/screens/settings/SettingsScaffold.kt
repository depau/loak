package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_back
import eu.depau.loak.ui.components.layouts.NestedTopBar
import eu.depau.loak.ui.components.layouts.NestedTopBarDefaults
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.components.SettingsGroupDefaults
import eu.depau.loak.ui.screens.setup.WizardScaffold
import org.jetbrains.compose.resources.stringResource

/** Whether the settings page around it wears the setup wizard's look, its title and description up top. */
val LocalInWizard = staticCompositionLocalOf { false }

/**
 * A settings detail page: back arrow only where needed, scrolling grouped content.
 *
 * Opened from the setup wizard's last page or "New in Lo'ak", it keeps the wizard's look
 * instead, as [wizardTitle] (or [title]) with [icon] and [description], and Back at the bottom.
 */
@Composable
internal fun SettingsScaffold(
	title: String,
	wizardTitle: String? = null,
	icon: ImageVector? = null,
	description: String? = null,
	content: @Composable ColumnScope.() -> Unit
) {
	val backStack = LocalNavStack.current
	if (backStack.any { it is Screen.SetupFeatures || it is Screen.WhatsNew }) {
		WizardScaffold(
			title = wizardTitle ?: title,
			icon = icon,
			description = description,
			actions = {
				TextButton(onClick = { backStack.removeLastOrNull() }) { Text(stringResource(Res.string.action_back)) }
				Spacer(Modifier.weight(1f))
			}
		) {
			Column(
				Modifier.padding(top = 12.dp),
				verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
			) {
				CompositionLocalProvider(LocalInWizard provides true) { content() }
			}
		}
		return
	}
	Scaffold(
		topBar = {
			NestedTopBar(
				title = { Text(title) },
				navigationAction = NestedTopBarDefaults::NavigationAction
			)
		}
	) { innerPadding ->
		Column(
			modifier = Modifier
				.padding(innerPadding)
				.verticalScroll(rememberScrollState())
				.padding(horizontal = 16.dp)
				.padding(bottom = 24.dp),
			verticalArrangement = Arrangement.spacedBy(SettingsGroupDefaults.GapBetweenGroups)
		) { content() }
	}
}
