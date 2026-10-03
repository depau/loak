package eu.depau.loak.ui.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_dont_show_again
import eu.depau.loak.generated.resources.action_update_app
import eu.depau.loak.generated.resources.action_update_install_restart
import eu.depau.loak.generated.resources.action_update_later
import eu.depau.loak.generated.resources.info_update
import eu.depau.loak.generated.resources.info_update_downloading_percent
import eu.depau.loak.generated.resources.info_update_checking
import eu.depau.loak.generated.resources.info_update_installing
import eu.depau.loak.generated.resources.info_update_ready
import eu.depau.loak.generated.resources.title_update
import eu.depau.loak.ui.theme.ContinuousCapsule
import eu.depau.loak.ui.theme.defaultFont
import eu.depau.loak.ui.util.LocalUpdateController
import eu.depau.loak.ui.util.UpdatePhase
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/**
 * Modal bottom sheet driving the desktop auto-updater. Reads [LocalUpdateController],
 * shows [UpdatePhase] progress, and offers download / install & restart actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopUpdateSheet() {
	val updateController = LocalUpdateController.current
	if (updateController == null || !updateController.supported) return

	val preferenceManager = koinInject<PreferenceManager>()
	val state by updateController.state.collectAsStateWithLifecycle()

	// Fire the update check exactly once per launch, before drawing anything.
	LaunchedEffect(updateController) {
		updateController.check()
	}

	// The user can defer the offer for this session ("later"); that's separate from the
	// master "check for updates" preference, which hides the sheet on future launches.
	var deferred by remember { mutableStateOf(false) }
	if (deferred) return

	val visible = when (state.phase) {
		UpdatePhase.Checking, UpdatePhase.Available, UpdatePhase.Downloading,
		UpdatePhase.Downloaded, UpdatePhase.Installing -> true
		UpdatePhase.Idle, UpdatePhase.UpToDate, UpdatePhase.Failed -> false
	}
	if (!visible) return

	ModalBottomSheet(
		onDismissRequest = {
			// Dismissing mid-flow just closes it for now; the download continues in background.
			deferred = true
		},
		sheetState = rememberBottomSheetState(
			initialValue = SheetValue.Expanded,
		)
	) {
		Column(
			modifier = Modifier
				.fillMaxWidth()
				.padding(horizontal = 24.dp, vertical = 16.dp),
			horizontalAlignment = Alignment.Start,
			verticalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Text(
				text = stringResource(Res.string.title_update),
				style = MaterialTheme.typography.titleLarge,
				fontFamily = defaultFont(round = 100f)
			)

			when (state.phase) {
				UpdatePhase.Checking ->
					Text(
						stringResource(Res.string.info_update_checking),
						style = MaterialTheme.typography.bodyMedium
					)

				UpdatePhase.Available -> {
					Text(
						stringResource(Res.string.info_update, state.newVersion ?: ""),
						style = MaterialTheme.typography.bodyMedium
					)
					Button(
						onClick = { updateController.download() },
						modifier = Modifier.fillMaxWidth(),
						shape = ContinuousCapsule
					) {
						Text(
							stringResource(Res.string.action_update_app),
							fontFamily = defaultFont(100)
						)
					}
				}

				UpdatePhase.Downloading -> {
					val progress = state.progress ?: 0f
					Text(
						text = stringResource(
							Res.string.info_update_downloading_percent,
							state.newVersion ?: "",
							(progress * 100).roundToInt()
						),
						style = MaterialTheme.typography.bodyMedium
					)
					LinearProgressIndicator(
						progress = { progress },
						modifier = Modifier.fillMaxWidth()
					)
				}

				UpdatePhase.Downloaded -> {
					val scope = rememberCoroutineScope()
					Text(
						stringResource(Res.string.info_update_ready),
						style = MaterialTheme.typography.bodyMedium
					)
					Button(
						onClick = { scope.launch { updateController.installAndRestart() } },
						modifier = Modifier.fillMaxWidth(),
						shape = ContinuousCapsule
					) {
						Text(
							stringResource(Res.string.action_update_install_restart),
							fontFamily = defaultFont(100)
						)
					}
				}

				UpdatePhase.Installing -> {
					Text(
						stringResource(Res.string.info_update_installing),
						style = MaterialTheme.typography.bodyMedium
					)
					LinearProgressIndicator(
						modifier = Modifier.fillMaxWidth()
					)
				}

				else -> {}
			}

			when (state.phase) {
				UpdatePhase.Downloaded, UpdatePhase.Installing -> {}
				else -> OutlinedButton(
					onClick = { deferred = true },
					modifier = Modifier.fillMaxWidth(),
					shape = ContinuousCapsule
				) {
					Text(
						text = stringResource(Res.string.action_update_later),
						fontFamily = defaultFont(100)
					)
				}
			}
		}
	}
}
