package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.manager.Feature
import eu.depau.loak.domain.manager.FeatureCatalog
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_integrations
import eu.depau.loak.generated.resources.title_integrations
import eu.depau.loak.ui.screens.setup.FeatureCards
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The setup wizard's feature cards, for later: the ones never shown before wear a badge, once. */
@Composable
fun SettingsIntegrationsScreen() {
	val catalog = koinInject<FeatureCatalog>()
	val new by produceState(emptySet<Feature>()) {
		value = catalog.unannounced().toSet().also(catalog::markSeen)
	}
	SettingsScaffold(stringResource(Res.string.title_integrations)) {
		Text(
			stringResource(Res.string.info_integrations),
			style = MaterialTheme.typography.bodyMedium,
			modifier = Modifier.padding(horizontal = 12.dp)
		)
		FeatureCards(new = new)
	}
}
