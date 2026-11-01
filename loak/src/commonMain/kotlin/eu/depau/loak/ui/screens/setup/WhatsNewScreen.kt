package eu.depau.loak.ui.screens.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.Feature
import eu.depau.loak.domain.manager.FeatureCatalog
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_done
import eu.depau.loak.generated.resources.info_already_on
import eu.depau.loak.generated.resources.info_new_in_loak
import eu.depau.loak.generated.resources.title_more_to_turn_on
import eu.depau.loak.generated.resources.title_new_in_loak
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.CheckCircle
import eu.depau.loak.icons.outlined.NewReleases
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Shown once over the app when it gained a feature this user has never been shown and hasn't
 * set up: the new ones first, then the rest still to turn on, then what is on already.
 */
@Composable
fun WhatsNewScreen(ids: List<String>) {
	val catalog = koinInject<FeatureCatalog>()
	val backStack = LocalNavStack.current
	val new = remember(ids) { Feature.entries.filter { it.id in ids } }
	val available by produceState(new) { value = catalog.available() }
	val rest = available - new.toSet()
	val (on, off) = rest.partition(catalog::isSetUp)

	WizardScaffold(
		title = stringResource(Res.string.title_new_in_loak),
		icon = Icons.Outlined.NewReleases,
		iconColor = MaterialTheme.colorScheme.tertiaryContainer,
		description = stringResource(Res.string.info_new_in_loak),
		actions = {
			WizardButton(stringResource(Res.string.action_done), onClick = {
				catalog.markSeen(new)
				backStack.removeLastOrNull()
			})
		}
	) {
		FeatureCards(features = new, new = new.toSet())
		if (off.isNotEmpty()) {
			Text(
				stringResource(Res.string.title_more_to_turn_on),
				modifier = Modifier.padding(start = 4.dp, top = 12.dp),
				style = MaterialTheme.typography.titleSmall,
				color = MaterialTheme.colorScheme.primary
			)
			FeatureCards(features = off)
		}
		if (on.isNotEmpty()) {
			Row(
				Modifier.padding(start = 4.dp, top = 12.dp),
				horizontalArrangement = Arrangement.spacedBy(8.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
				Text(
					stringResource(Res.string.info_already_on, on.map { stringResource(featureTitle(it)) }.joinToString(" · ")),
					style = MaterialTheme.typography.bodyMedium,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}
		}
	}
}
