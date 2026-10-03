package eu.depau.loak.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.di.LocalNavStack
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_done
import eu.depau.loak.generated.resources.action_start_listening
import eu.depau.loak.generated.resources.info_a_few_more_things
import eu.depau.loak.generated.resources.title_a_few_more_things
import eu.depau.loak.ui.navigation.Screen
import eu.depau.loak.ui.screens.settings.IntegrationsGroup
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * The setup wizard's last page, after the server: optional extras, like the end of a phone's
 * setup. Smart playlists are on when the server has them; AudioMuse-AI has a Set up button.
 */
@Composable
fun SetupIntegrationsScreen() {
	val backStack = LocalNavStack.current
	val sync by koinInject<SyncManager>().syncState.collectAsState()
	val audioMuse by koinInject<AudioMuseManager>().info.collectAsState()

	Scaffold { innerPadding ->
		Column(
			modifier = Modifier.fillMaxSize().padding(innerPadding),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			Column(
				modifier = Modifier
					.weight(1f)
					.widthIn(max = 600.dp)
					.verticalScroll(rememberScrollState())
					.padding(horizontal = 24.dp, vertical = 32.dp),
				verticalArrangement = Arrangement.spacedBy(16.dp)
			) {
				Text(stringResource(Res.string.title_a_few_more_things), style = MaterialTheme.typography.headlineMedium)
				Text(stringResource(Res.string.info_a_few_more_things), style = MaterialTheme.typography.bodyLarge)
				if (sync.isSyncing) Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) {
					Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(stringResource(sync.message), style = MaterialTheme.typography.bodyMedium)
						LinearProgressIndicator(progress = { sync.progress }, modifier = Modifier.fillMaxWidth())
					}
				}
				IntegrationsGroup()
			}
			Row(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
				Button(onClick = {
					backStack.clear()
					backStack.add(Screen.Home())
				}) {
					Text(stringResource(if (audioMuse != null) Res.string.action_start_listening else Res.string.action_done))
				}
			}
		}
	}
}
