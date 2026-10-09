package eu.depau.loak.ui.components.sheets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.depau.loak.domain.repositories.HomeRepository
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_pin_speed_dial
import eu.depau.loak.generated.resources.action_unpin_speed_dial
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.filled.Keep
import eu.depau.loak.icons.outlined.Keep
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Pins or unpins a song, album or playlist ([key], see [HomeRepository.keyOf]) on Speed dial. */
@Composable
internal fun speedDialPinRow(key: String, onDismissRequest: () -> Unit): SheetRow {
	val homeRepository = koinInject<HomeRepository>()
	val pins by homeRepository.pins.collectAsStateWithLifecycle()
	val pinned = key in pins
	return SheetRow(
		stringResource(if (pinned) Res.string.action_unpin_speed_dial else Res.string.action_pin_speed_dial),
		if (pinned) Icons.Filled.Keep else Icons.Outlined.Keep,
		{
			homeRepository.setPinned(key, !pinned)
			onDismissRequest()
		}
	)
}
