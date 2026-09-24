package eu.depau.loak.ui.screens.radio.components

import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Modifier
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_no_radios
import org.jetbrains.compose.resources.stringResource
import eu.depau.loak.domain.models.DomainRadio
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Radio
import eu.depau.loak.ui.components.common.ContentUnavailable
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.ui.util.loakAnimateItem

fun LazyGridScope.radioListScreenContent(
	state: UiState<List<DomainRadio>>,
	onRadioClick: (DomainRadio) -> Unit
) {
	val data = state.data.orEmpty()

	if (data.isNotEmpty()) {
		items(data, key = { it.id }) { radio ->
			RadioListScreenCard(
				modifier = loakAnimateItem(),
				radio = radio,
				onPlayClick = { onRadioClick(radio) }
			)
		}
	} else {
		when (state) {
			is UiState.Loading -> {
				items(10) {
					RadioListScreenCardPlaceholder()
				}
			}

			else -> {
				item(span = { GridItemSpan(maxLineSpan) }) {
					ContentUnavailable(
						icon = Icons.Outlined.Radio,
						label = stringResource(Res.string.info_no_radios)
					)
				}
			}
		}
	}
}
