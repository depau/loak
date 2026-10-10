package eu.depau.loak.ui.screens.discover.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.discover.DeezerClient
import eu.depau.loak.domain.discover.DeezerCountryChart
import eu.depau.loak.domain.discover.WORLDWIDE_ISO
import eu.depau.loak.domain.discover.chartCountriesByLetter
import eu.depau.loak.domain.discover.isoCountryName
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.action_filter_countries
import eu.depau.loak.generated.resources.option_chart_country_automatic
import eu.depau.loak.generated.resources.option_chart_country_worldwide
import eu.depau.loak.generated.resources.subtitle_chart_country_automatic
import eu.depau.loak.generated.resources.subtitle_chart_country_detecting
import eu.depau.loak.generated.resources.title_chart_country
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.KeyboardArrowDown
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.components.sheets.ModalBottomSheet
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** The chart's country, as a chip that opens [ChartCountryPickerSheet]. */
@Composable
fun ChartCountryChip(countryName: String, onClick: () -> Unit) {
	AssistChip(
		onClick = onClick,
		label = { Text(countryName, maxLines = 1) },
		trailingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp)) },
		shape = CircleShape
	)
}

/**
 * Which country's Deezer chart the Top songs shelf and list show: automatic (the country
 * Deezer sees the caller from), Worldwide, or one of its chart countries A–Z. [selected] is
 * "" for automatic, else an ISO code, exactly as the preference holds it.
 */
@Composable
fun ChartCountryPickerSheet(
	charts: List<DeezerCountryChart>,
	selected: String,
	onSelect: (String) -> Unit,
	onDismissRequest: () -> Unit
) {
	val deezer = koinInject<DeezerClient>()
	val detected by produceState("") { value = deezer.getDetectedCountryIso() }
	var filter by remember { mutableStateOf("") }
	val grouped = remember(charts, filter) { chartCountriesByLetter(charts, filter) }
	val pick = { iso: String ->
		onSelect(iso)
		onDismissRequest()
	}

	ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		sheetTitle = stringResource(Res.string.title_chart_country)
	) {
		Text(
			stringResource(Res.string.title_chart_country),
			Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
			style = MaterialTheme.typography.headlineSmall
		)
		TextField(
			value = filter,
			onValueChange = { filter = it },
			modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
			placeholder = { Text(stringResource(Res.string.action_filter_countries)) },
			leadingIcon = { Icon(Icons.Outlined.Search, null) },
			singleLine = true,
			shape = CircleShape,
			colors = TextFieldDefaults.colors(
				focusedIndicatorColor = Color.Transparent,
				unfocusedIndicatorColor = Color.Transparent,
				disabledIndicatorColor = Color.Transparent
			)
		)
		LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
			item(key = "automatic") {
				CountryRow(
					name = stringResource(Res.string.option_chart_country_automatic),
					supporting = isoCountryName(detected)
						?.let { stringResource(Res.string.subtitle_chart_country_automatic, it) }
						?: stringResource(Res.string.subtitle_chart_country_detecting),
					selected = selected.isEmpty(),
					onClick = { pick("") }
				)
			}
			item(key = "worldwide") {
				CountryRow(
					name = stringResource(Res.string.option_chart_country_worldwide),
					selected = selected == WORLDWIDE_ISO,
					onClick = { pick(WORLDWIDE_ISO) }
				)
			}
			item(key = "divider") { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
			grouped.forEach { (letter, countries) ->
				stickyHeader(key = "letter-$letter") {
					Text(
						letter.toString(),
						Modifier
							.fillMaxWidth()
							.background(MaterialTheme.colorScheme.surfaceContainerLow)
							.padding(horizontal = 24.dp, vertical = 6.dp),
						style = MaterialTheme.typography.labelLarge,
						color = MaterialTheme.colorScheme.primary
					)
				}
				items(countries, key = { it.countryCode }) {
					CountryRow(
						name = it.countryName,
						selected = selected == it.countryCode,
						onClick = { pick(it.countryCode) }
					)
				}
			}
		}
	}
}

@Composable
private fun CountryRow(
	name: String,
	selected: Boolean,
	onClick: () -> Unit,
	supporting: String? = null
) {
	ListItem(
		modifier = Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick),
		colors = ListItemDefaults.colors(
			containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
		),
		// the whole row is the radio button: the control itself doesn't take a second click
		leadingContent = { RadioButton(selected = selected, onClick = null) },
		headlineContent = { Text(name, maxLines = 1) },
		supportingContent = supporting?.let { { Text(it, maxLines = 1) } }
	)
}
