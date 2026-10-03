package eu.depau.loak.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import eu.depau.loak.ui.components.common.SegmentedListItem
import eu.depau.loak.ui.components.common.SegmentedListItemDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.CronSchedule
import eu.depau.loak.domain.models.CronSchedule.Every
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Radio
import eu.depau.loak.icons.outlined.Refresh
import eu.depau.loak.icons.outlined.Schedule
import eu.depau.loak.ui.components.common.UsesTokensBadge
import eu.depau.loak.ui.screens.settings.components.SettingsGroup
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** A row of AudioMuse-AI's `cron` table, kept whole so saving sends its options back. */
private class CronEntry(val raw: JsonObject) {
	val taskType = raw["task_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
	val name = raw["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
	val expr = raw["cron_expr"]?.jsonPrimitive?.contentOrNull.orEmpty()
	val enabled = raw["enabled"]?.jsonPrimitive?.booleanOrNull == true

	fun with(expr: String = this.expr, enabled: Boolean = this.enabled) = JsonObject(
		raw + mapOf("cron_expr" to JsonPrimitive(expr), "enabled" to JsonPrimitive(enabled))
	)
}

private val taskNames = mapOf(
	"analysis" to Res.string.task_analysis,
	"clustering" to Res.string.task_clustering,
	"sonic_fingerprint" to Res.string.task_sonic_fingerprint,
	"album_of_the_week" to Res.string.task_album_of_the_week,
	"alchemy_radio" to Res.string.task_alchemy_radio
)

private val dayNames = listOf(
	Res.string.day_0, Res.string.day_1, Res.string.day_2, Res.string.day_3,
	Res.string.day_4, Res.string.day_5, Res.string.day_6
)

@Composable
private fun CronEntry.label() = taskNames[taskType]?.let { stringResource(it) } ?: name

@Composable
private fun describe(expr: String): String {
	val s = CronSchedule.parse(expr) ?: return expr
	val time = "${s.hour.toString().padStart(2, '0')}:${s.minute.toString().padStart(2, '0')}"
	return when (s.every) {
		Every.Day -> stringResource(Res.string.schedule_daily, time)
		// Monday first
		Every.Week -> stringResource(
			Res.string.schedule_weekly,
			s.days.sortedBy { (it + 6) % 7 }.map { stringResource(dayNames[it]) }.joinToString(", "),
			time
		)
		Every.Month -> stringResource(Res.string.schedule_monthly, s.dayOfMonth, time)
	}
}

/**
 * Schedules (admins only: AudioMuse-AI doesn't show them to anyone else), Rebuild AI
 * playlists and Refresh radios now.
 */
@Composable
fun AudioMuseScheduleGroup(manager: AudioMuseManager) {
	val info by manager.info.collectAsState()
	val radios by manager.radios.collectAsState()
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()
	val admin = info?.isAdmin == true
	var reload by remember { mutableStateOf(0) }
	val entries by produceState(emptyList<CronEntry>(), admin, reload) {
		if (admin) value = runCatching {
			manager.getJson("api/cron").jsonArray.map { CronEntry(it.jsonObject) }
		}.getOrDefault(value)
	}
	var editing by remember { mutableStateOf<CronEntry?>(null) }
	var rebuilding by remember { mutableStateOf(false) }

	fun save(entry: JsonObject) = scope.launch {
		runCatching { manager.postJson("api/cron", entry) }
			.onFailure { snackBarManager.notify(Res.string.info_audiomuse_unreachable, it.message ?: "") }
		reload++
	}

	if (admin && entries.isNotEmpty()) SettingsGroup(title = { Text(stringResource(Res.string.title_on_a_schedule)) }) {
		entries.forEachIndexed { i, entry ->
			SegmentedListItem(
				onClick = { editing = entry },
				shapes = SegmentedListItemDefaults.segmentedShapes(index = i, count = entries.size),
				content = {
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(entry.label())
						// scheduled rebuilds are named by the AI service when the server has one
						if (entry.taskType == "clustering" && info?.canAsk == true) UsesTokensBadge()
					}
				},
				supportingContent = {
					Text(if (entry.enabled) describe(entry.expr) else stringResource(Res.string.schedule_off))
				},
				trailingContent = {
					Switch(checked = entry.enabled, onCheckedChange = { save(entry.with(enabled = it)) })
				}
			)
		}
	}
	if (admin || radios.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
		if (admin) FilledTonalButton(onClick = { rebuilding = true }) {
			Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
			Spacer(Modifier.size(8.dp))
			Text(stringResource(Res.string.action_rebuild_ai_playlists))
		}
		if (radios.isNotEmpty()) FilledTonalButton(onClick = {
			scope.launch {
				runCatching { manager.request("api/radios/run", post = true) {} }
					.onSuccess { snackBarManager.notify(Res.string.notice_refreshing_radios) }
					.onFailure { snackBarManager.notify(Res.string.info_audiomuse_unreachable, it.message ?: "") }
			}
		}) {
			Icon(Icons.Outlined.Radio, null, Modifier.size(18.dp))
			Spacer(Modifier.size(8.dp))
			Text(stringResource(Res.string.action_refresh_radios))
		}
	}
	if (admin) Text(
		stringResource(Res.string.info_schedules),
		style = MaterialTheme.typography.bodySmall,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
		modifier = Modifier.padding(horizontal = 4.dp)
	)

	editing?.let { entry ->
		ScheduleSheet(entry, onDismissRequest = { editing = null }) {
			save(it)
			editing = null
		}
	}
	if (rebuilding) RebuildSheet(manager) { rebuilding = false }
}

@Composable
private fun ScheduleSheet(entry: CronEntry, onDismissRequest: () -> Unit, onSave: (JsonObject) -> Unit) {
	val parsed = CronSchedule.parse(entry.expr)
	var custom by remember { mutableStateOf(parsed == null) }
	var schedule by remember { mutableStateOf(parsed ?: CronSchedule(Every.Day, 3, 0)) }
	var enabled by remember { mutableStateOf(entry.enabled) }
	val time = rememberTimePickerState(schedule.hour, schedule.minute, is24Hour = true)
	val expr = rememberTextFieldState(entry.expr)
	var pickingTime by remember { mutableStateOf(false) }

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
			Text(entry.label(), style = MaterialTheme.typography.titleLarge)
			if (custom) {
				TextField(
					state = expr,
					modifier = Modifier.fillMaxWidth(),
					label = { Text(stringResource(Res.string.label_cron)) },
					lineLimits = TextFieldLineLimits.SingleLine
				)
			} else {
				SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
					listOf(
						Every.Day to Res.string.option_daily,
						Every.Week to Res.string.option_weekly,
						Every.Month to Res.string.option_monthly
					).forEachIndexed { i, (every, label) ->
						SegmentedButton(
							selected = schedule.every == every,
							onClick = { schedule = schedule.copy(every = every) },
							shape = SegmentedButtonDefaults.itemShape(i, 3)
						) { Text(stringResource(label)) }
					}
				}
				when (schedule.every) {
					Every.Week -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
					Every.Month -> Column {
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
					Every.Day -> {}
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
			if (!custom) TextButton(onClick = {
				expr.edit { replace(0, length, schedule.copy(hour = time.hour, minute = time.minute).toCron()) }
				custom = true
			}) { Text(stringResource(Res.string.action_custom_schedule)) }
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
				TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.action_cancel)) }
				Button(onClick = {
					val cron = if (custom) expr.text.toString().trim()
					else schedule.copy(hour = time.hour, minute = time.minute).toCron()
					onSave(entry.with(expr = cron, enabled = enabled))
				}) { Text(stringResource(Res.string.action_save)) }
			}
		}
	}
}

@Composable
private fun RebuildSheet(manager: AudioMuseManager, onDismissRequest: () -> Unit) {
	val info by manager.info.collectAsState()
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()
	var playlists by remember { mutableStateOf(10) }
	var perPlaylist by remember { mutableStateOf(0) }
	var aiNames by remember { mutableStateOf(info?.canAsk == true) }
	var busy by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }
	LaunchedEffect(Unit) {
		runCatching { manager.getJson("api/config").jsonObject }.onSuccess { config ->
			config["top_n_clustering_playlist"]?.jsonPrimitive?.intOrNull?.let { playlists = it }
			config["max_songs_per_cluster"]?.jsonPrimitive?.intOrNull?.let { perPlaylist = it }
		}
	}

	ModalBottomSheet(onDismissRequest = onDismissRequest) {
		Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
			Text(stringResource(Res.string.action_rebuild_ai_playlists), style = MaterialTheme.typography.titleLarge)
			Text(stringResource(Res.string.info_rebuild), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
			SliderRow(Res.string.label_playlists_count, "$playlists") {
				Slider(value = playlists.toFloat(), onValueChange = { playlists = it.roundToInt() }, valueRange = 1f..50f)
			}
			SliderRow(Res.string.label_songs_per_playlist, if (perPlaylist == 0) stringResource(Res.string.label_no_limit) else "$perPlaylist") {
				Slider(value = perPlaylist.toFloat(), onValueChange = { perPlaylist = it.roundToInt() }, valueRange = 0f..200f)
			}
			if (info?.canAsk == true) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
				Column(Modifier.weight(1f)) {
					Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
						Text(stringResource(Res.string.option_name_with_ai))
						UsesTokensBadge()
					}
					Text(stringResource(Res.string.subtitle_name_with_ai), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
				}
				Switch(checked = aiNames, onCheckedChange = { aiNames = it })
			}
			Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
				Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
					Icon(Icons.Outlined.Schedule, null, Modifier.size(20.dp))
					Text(stringResource(Res.string.info_rebuild_time), style = MaterialTheme.typography.bodySmall)
				}
			}
			error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
			Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
				TextButton(onClick = onDismissRequest) { Text(stringResource(Res.string.action_cancel)) }
				Button(enabled = !busy, onClick = {
					busy = true
					scope.launch {
						runCatching {
							manager.postJson("api/clustering/start", JsonObject(buildMap {
								put("top_n_clustering_playlist", JsonPrimitive(playlists))
								put("max_songs_per_cluster", JsonPrimitive(perPlaylist))
								// otherwise the server's own AI service names them
								if (!aiNames) put("ai_model_provider", JsonPrimitive("NONE"))
							}))
						}.onSuccess {
							snackBarManager.notify(Res.string.notice_rebuilding)
							onDismissRequest()
						}.onFailure { error = it.message }
						busy = false
					}
				}) {
					if (busy) CircularProgressIndicator(Modifier.size(18.dp)) else Text(stringResource(Res.string.action_rebuild))
				}
			}
		}
	}
}

@Composable
private fun SliderRow(label: StringResource, value: String, slider: @Composable () -> Unit) {
	Column {
		Row {
			Text(stringResource(label), Modifier.weight(1f))
			Text(value, color = MaterialTheme.colorScheme.primary)
		}
		slider()
	}
}
