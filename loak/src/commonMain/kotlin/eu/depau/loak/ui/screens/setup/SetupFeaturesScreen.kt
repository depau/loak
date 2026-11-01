package eu.depau.loak.ui.screens.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_start_listening
import eu.depau.loak.generated.resources.info_make_loak_yours
import eu.depau.loak.generated.resources.title_make_loak_yours
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Sparkle
import eu.depau.loak.ui.components.common.SyncingCard
import eu.depau.loak.ui.navigation.Screen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Bump when a feature is added: users who saw an older list get the page again, once. */
const val INTEGRATIONS_VERSION = 3

/**
 * The setup wizard's last page, after the server: every optional feature as a card, like the
 * end of a phone's setup, while the library syncs.
 */
@Composable
fun SetupFeaturesScreen() {
	val backStack = LocalNavStack.current
	val sync by koinInject<SyncManager>().syncState.collectAsState()
	val preferenceManager = koinInject<PreferenceManager>()

	WizardScaffold(
		title = stringResource(Res.string.title_make_loak_yours),
		icon = Icons.Filled.Sparkle,
		description = stringResource(Res.string.info_make_loak_yours),
		step = WizardStep(2, 2),
		actions = {
			WizardButton(stringResource(Res.string.action_start_listening), onClick = {
				preferenceManager.integrationsSeen = INTEGRATIONS_VERSION
				// shown over the app after an update: back to where it was
				if (backStack.size > 1) backStack.removeLastOrNull()
				else {
					backStack.clear()
					backStack.add(Screen.Home())
				}
			})
		}
	) {
		if (sync.isSyncing) SyncingCard(sync)
		FeatureCards()
	}
}
