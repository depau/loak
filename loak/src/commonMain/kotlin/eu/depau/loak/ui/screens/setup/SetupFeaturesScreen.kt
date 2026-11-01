package eu.depau.loak.ui.screens.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.Feature
import eu.depau.loak.domain.manager.FeatureCatalog
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

/**
 * The setup wizard's last page, after the server: every optional feature as a card, like the
 * end of a phone's setup, while the library syncs.
 */
@Composable
fun SetupFeaturesScreen() {
	val backStack = LocalNavStack.current
	val sync by koinInject<SyncManager>().syncState.collectAsState()
	val catalog = koinInject<FeatureCatalog>()
	// all of them are on this page: none is news later, even if the app is closed from here
	LaunchedEffect(Unit) { catalog.markSeen(Feature.entries.filter(catalog::onPlatform)) }

	WizardScaffold(
		title = stringResource(Res.string.title_make_loak_yours),
		icon = Icons.Filled.Sparkle,
		description = stringResource(Res.string.info_make_loak_yours),
		step = WizardStep(2, 2),
		actions = {
			WizardButton(stringResource(Res.string.action_start_listening), onClick = {
				backStack.clear()
				backStack.add(Screen.Home())
			})
		}
	) {
		if (sync.isSyncing) SyncingCard(sync)
		FeatureCards()
	}
}
