package eu.depau.loak.ui.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.data.database.entities.DownloadCollectionEntity
import eu.depau.loak.domain.models.CronSchedule
import eu.depau.loak.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * Schedule picker for a downloaded collection (mirrors the AudioMuse cron sheet): day/week/month
 * + a time, or a custom cron, plus whether it's enabled. Also offers an immediate "download now".
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSheet(
	initialCron: String? = null,
	initialEnabled: Boolean = false,
	onDismissRequest: () -> Unit,
	onSave: (cron: String?, enabled: Boolean) -> Unit,
	onKick: () -> Unit = {}
) {
	var enabled by remember { mutableStateOf(initialEnabled) }
	val parsed = initialCron?.let { CronSchedule.parse(it) }
	var custom by remember { mutableStateOf(parsed == null) }
	var schedule by remember { mutableStateOf(parsed ?: CronSchedule(CronSchedule.Every.Day, 3, 0)) }
	var pickingTime by remember { mutableStateOf(false) }
	val time = rememberTimePickerState(schedule.hour, schedule.minute, is24Hour = true)
	val expr = rememberTextFieldState(initialCron ?: "")
	val dayNames: List<org.jetbrains.compose.resources.StringResource> = listOf(
		Res.string.day_0, Res.string.day_1, Res.string.day_2, Res.string.day_3,
		Res.string.day_4, Res.string.day_5, Res.string.day_6
	)

	if (pickingTime) AlertDialog(
		onDismissRequest = { pickingTime = false },
		confirmButton = { TextButton(onClick = { pickingTime = false }) { Text(stringResource(Res.string.action_ok)) } },
		text = { TimePicker(time) }
	)

	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Column(
			Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(16.dp)
		) {
			Text(stringResource(Res.string.title_downloads_settings), style = MaterialTheme.typography.titleLarge)
			if (custom) {
				TextField(
					state = expr,
					label = { Text(stringResource(Res.string.label_cron)) },
					modifier = Modifier.fillMaxWidth(),
					lineLimits = TextFieldLineLimits.SingleLine
				)
			} else {
				SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
					listOf(
						CronSchedule.Every.Day to Res.string.option_daily,
						CronSchedule.Every.Week to Res.string.option_weekly,
						CronSchedule.Every.Month to Res.string.option_monthly
					).forEachIndexed { i, (every, label) ->
						SegmentedButton(
							selected = schedule.every == every,
							onClick = { schedule = schedule.copy(every = every) },
							shape = SegmentedButtonDefaults.itemShape(i, 3)
						) { Text(stringResource(label)) }
					}
				}
				when (schedule.every) {
					CronSchedule.Every.Week -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
						(1..7).map { it % 7 }.forEach { day ->
							FilterChip(
								selected = day in schedule.days,
								onClick = {
									val days = if (day in schedule.days) schedule.days - day else schedule.days + day
									if (days.isNotEmpty()) schedule = schedule.copy(days = days)
								},
								label = { Text(stringResource(dayNames[day])) }
							)
						}
					}
					CronSchedule.Every.Month -> Column {
						Row {
							Text(stringResource(Res.string.label_day_of_month), Modifier.weight(1f))
							Text("${schedule.dayOfMonth}", color = MaterialTheme.colorScheme.primary)
						}
						// ponytail: up to 28, so every month has the day
						Slider(
							value = schedule.dayOfMonth.toFloat(),
							onValueChange = { schedule = schedule.copy(dayOfMonth = it.roundToInt()) },
							valueRange = 1f..28f
						)
					}
					CronSchedule.Every.Day -> {}
				}
				Row(verticalAlignment = Alignment.CenterVertically) {
					Text(stringResource(Res.string.label_time), Modifier.weight(1f))
					AssistChip(
						onClick = { pickingTime = true },
						label = { Text("${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}") }
					)
				}
			}
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(stringResource(Res.string.label_on), Modifier.weight(1f))
				Switch(checked = enabled, onCheckedChange = { enabled = it })
			}
			if (!custom) TextButton(onClick = { custom = true }) {
				Text(stringResource(Res.string.action_custom_schedule))
			}
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
				TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.action_cancel)) }
				TextButton(onClick = {
					onKick()
					onDismissRequest()
				}) { Text(stringResource(Res.string.action_download_now)) }
				Button(onClick = {
					val cron = if (custom) expr.text.toString().trim()
					else schedule.copy(hour = time.hour, minute = time.minute).toCron()
					// an unparseable/disabled schedule is stored off
					onSave(if (enabled) cron.ifEmpty { null } else null, enabled)
					onDismissRequest()
				}) { Text(stringResource(Res.string.action_save)) }
			}
		}
	}
}
