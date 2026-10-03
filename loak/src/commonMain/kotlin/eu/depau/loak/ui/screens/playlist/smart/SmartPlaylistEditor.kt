package eu.depau.loak.ui.screens.playlist.smart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import eu.depau.loak.domain.manager.NavidromeManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SmartPlaylistDetails
import eu.depau.loak.domain.manager.SnackBarManager
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.RULE_OPERATORS
import eu.depau.loak.domain.models.Rule
import eu.depau.loak.domain.models.RuleField
import eu.depau.loak.domain.models.RuleFieldType
import eu.depau.loak.domain.models.RuleGroup
import eu.depau.loak.domain.models.RuleNode
import eu.depau.loak.domain.models.SmartCriteria
import eu.depau.loak.domain.models.defaultRuleValue
import eu.depau.loak.domain.models.fieldType
import eu.depau.loak.domain.models.ruleText
import eu.depau.loak.domain.repositories.DbRepository
import eu.depau.loak.generated.resources.*
import eu.depau.loak.icons.Icons
import eu.depau.loak.icons.outlined.Add
import eu.depau.loak.icons.outlined.Close
import eu.depau.loak.icons.outlined.KeyboardArrowDown
import eu.depau.loak.icons.outlined.Search
import eu.depau.loak.ui.components.common.displayName
import eu.depau.loak.util.Logger
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val NEW_RULE get() = Rule("contains", RuleField.Genre.key, JsonPrimitive(""))

/**
 * Creates ([playlist] null) or edits a Navidrome smart playlist: name, description, the rules
 * (groups nest to any depth), order, limit and visibility. [onSaved] gets the playlist's id.
 */
@Composable
fun SmartPlaylistEditor(
	playlist: DomainPlaylist?,
	details: SmartPlaylistDetails?,
	onDismissRequest: () -> Unit,
	onSaved: (String) -> Unit
) {
	val navidrome = koinInject<NavidromeManager>()
	val sessionManager = koinInject<SessionManager>()
	val dbRepository = koinInject<DbRepository>()
	val snackBarManager = koinInject<SnackBarManager>()
	val scope = rememberCoroutineScope()

	val originalName = playlist?.displayName()
	val name = rememberTextFieldState(originalName?.display.orEmpty())
	val comment = rememberTextFieldState(playlist?.comment.orEmpty())
	var public by remember { mutableStateOf(playlist?.public == true) }
	var criteria by remember {
		mutableStateOf(details?.criteria ?: SmartCriteria(RuleGroup(false, listOf(NEW_RULE))))
	}
	var saving by remember { mutableStateOf(false) }
	var error by remember { mutableStateOf<String?>(null) }
	// for "in playlist" rules: id to name
	val playlists by produceState(emptyList<Pair<String, String>>()) {
		value = runCatching {
			sessionManager.api.getPlaylists().filter { it.id != playlist?.id }.map { it.id to it.name }
		}.getOrDefault(emptyList())
	}

	Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
		Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
			Column(Modifier.fillMaxSize()) {
				Row(
					modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
					verticalAlignment = Alignment.CenterVertically,
					horizontalArrangement = Arrangement.spacedBy(8.dp)
				) {
					IconButton(onClick = onDismissRequest) {
						Icon(Icons.Outlined.Close, stringResource(Res.string.action_cancel))
					}
					Text(
						stringResource(
							if (playlist == null) Res.string.title_new_smart_playlist
							else Res.string.title_edit_smart_playlist
						),
						style = MaterialTheme.typography.titleLarge,
						modifier = Modifier.weight(1f)
					)
					Button(
						enabled = !saving && name.text.isNotBlank() && criteria.root.children.isNotEmpty(),
						onClick = {
							saving = true
							error = null
							scope.launch {
								try {
									val newName = originalName?.rename(name.text.toString().trim())
										?: name.text.toString().trim()
									val id = if (playlist == null) {
										navidrome.createSmartPlaylist(newName, comment.text.toString(), public, criteria)
									} else {
										navidrome.updateSmartPlaylist(playlist.id, newName, comment.text.toString(), public, criteria)
										playlist.id
									}
									dbRepository.syncPlaylists()
									snackBarManager.notify(Res.string.notice_smart_playlist_saved)
									onSaved(id)
								} catch (e: Exception) {
									Logger.e("SmartPlaylistEditor", "Failed to save smart playlist", e)
									error = e.message
								} finally {
									saving = false
								}
							}
						}
					) { Text(stringResource(Res.string.action_save)) }
				}

				Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
				Column(
					modifier = Modifier
						.widthIn(max = 720.dp)
						.fillMaxWidth()
						.padding(horizontal = 16.dp)
						.padding(bottom = 32.dp),
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					error?.let {
						Text(
							stringResource(Res.string.notice_smart_playlist_failed, it),
							color = MaterialTheme.colorScheme.error,
							style = MaterialTheme.typography.bodyMedium
						)
					}
					OutlinedTextField(
						state = name,
						modifier = Modifier.fillMaxWidth(),
						label = { Text(stringResource(Res.string.label_name)) },
						lineLimits = TextFieldLineLimits.SingleLine
					)
					OutlinedTextField(
						state = comment,
						modifier = Modifier.fillMaxWidth(),
						label = { Text(stringResource(Res.string.label_description)) },
						lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2)
					)

					SectionTitle(stringResource(Res.string.title_songs_that_match))
					SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
						listOf(false to Res.string.option_match_all, true to Res.string.option_match_any)
							.forEachIndexed { i, (any, label) ->
								SegmentedButton(
									selected = criteria.root.any == any,
									onClick = { criteria = criteria.copy(root = criteria.root.copy(any = any)) },
									shape = SegmentedButtonDefaults.itemShape(i, 2)
								) { Text(stringResource(label)) }
							}
					}
					RuleGroupBody(
						group = criteria.root,
						depth = 0,
						playlists = playlists,
						onChange = { criteria = criteria.copy(root = it) }
					)

					SectionTitle(stringResource(Res.string.title_order_limit))
					OrderAndLimit(criteria) { criteria = it }

					SectionTitle(stringResource(Res.string.title_sharing))
					Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
						ListItem(
							onClick = { public = !public },
							content = { Text(stringResource(Res.string.option_playlist_public)) },
							supportingContent = { Text(stringResource(Res.string.info_playlist_public)) },
							trailingContent = { Switch(checked = public, onCheckedChange = null) }
						)
					}
					Text(
						stringResource(Res.string.info_smart_editor_footer),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(horizontal = 12.dp)
					)
				}
				}
			}
		}
	}
}

@Composable
private fun SectionTitle(text: String) {
	Text(
		text,
		style = MaterialTheme.typography.titleSmall,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.padding(start = 12.dp, top = 12.dp)
	)
}

/** A group's rules and subgroups, recursively, then its add buttons. */
@Composable
private fun RuleGroupBody(
	group: RuleGroup,
	depth: Int,
	playlists: List<Pair<String, String>>,
	onChange: (RuleGroup) -> Unit
) {
	fun replace(i: Int, node: RuleNode) =
		onChange(group.copy(children = group.children.toMutableList().also { it[i] = node }))
	fun remove(i: Int) = onChange(group.copy(children = group.children.filterIndexed { j, _ -> j != i }))

	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		group.children.forEachIndexed { i, node ->
			key(i) {
				when (node) {
					is Rule -> RuleCard(node, playlists, { replace(i, it) }, { remove(i) })
					is RuleGroup -> GroupCard(node, depth + 1, playlists, { replace(i, it) }, { remove(i) })
				}
			}
		}
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			OutlinedButton(onClick = { onChange(group.copy(children = group.children + NEW_RULE)) }) {
				Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
				Spacer(Modifier.width(6.dp))
				Text(stringResource(Res.string.action_add_rule))
			}
			OutlinedButton(onClick = {
				onChange(group.copy(children = group.children + RuleGroup(!group.any, listOf(NEW_RULE))))
			}) {
				Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
				Spacer(Modifier.width(6.dp))
				Text(stringResource(Res.string.action_add_group))
			}
		}
	}
}

@Composable
private fun GroupCard(
	group: RuleGroup,
	depth: Int,
	playlists: List<Pair<String, String>>,
	onChange: (RuleGroup) -> Unit,
	onRemove: () -> Unit
) {
	val rail = MaterialTheme.colorScheme.outlineVariant
	Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
		Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Dropdown(
					label = stringResource(if (group.any) Res.string.option_group_any else Res.string.option_group_all),
					options = listOf(
						stringResource(Res.string.option_group_all) to false,
						stringResource(Res.string.option_group_any) to true
					),
					onPick = { onChange(group.copy(any = it)) }
				)
				Spacer(Modifier.weight(1f))
				IconButton(onClick = onRemove) {
					Icon(Icons.Outlined.Close, stringResource(Res.string.action_remove_group))
				}
			}
			// ponytail: the indent stops growing past two levels so deep trees keep their width;
			// the rail still shows where each group ends
			Box(
				Modifier
					.drawBehind { drawLine(rail, Offset.Zero, Offset(0f, size.height), 2.dp.toPx()) }
					.padding(start = if (depth <= 2) 12.dp else 6.dp, end = 8.dp)
			) {
				RuleGroupBody(group, depth, playlists, onChange)
			}
		}
	}
}

@Composable
private fun RuleCard(
	rule: Rule,
	playlists: List<Pair<String, String>>,
	onChange: (Rule) -> Unit,
	onRemove: () -> Unit
) {
	val type = rule.fieldType()
	var pickingField by remember { mutableStateOf(false) }

	Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
		Column(
			modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 12.dp),
			verticalArrangement = Arrangement.spacedBy(8.dp)
		) {
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				FilledTonalButton(onClick = { pickingField = true }) {
					Text(fieldLabel(if (type == RuleFieldType.Playlist) RuleField.Playlist.key else rule.field))
					Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp))
				}
				Dropdown(
					label = operatorText(rule.op),
					options = RULE_OPERATORS.getValue(type).map { operatorText(it) to it },
					onPick = { op -> onChange(rule.withOp(op, type)) }
				)
				Spacer(Modifier.weight(1f))
				if (type == RuleFieldType.Bool) Switch(
					checked = (rule.value as? JsonPrimitive)?.booleanOrNull != false,
					onCheckedChange = { onChange(rule.copy(value = JsonPrimitive(it))) }
				)
				IconButton(onClick = onRemove) {
					Icon(Icons.Outlined.Close, stringResource(Res.string.action_remove_rule))
				}
			}
			if (type != RuleFieldType.Bool) Box(Modifier.padding(end = 8.dp)) {
				RuleValueEditor(rule, type, playlists) { onChange(rule.copy(value = it)) }
			}
		}
	}

	if (pickingField) FieldPicker(
		onDismissRequest = { pickingField = false },
		onPick = { key ->
			pickingField = false
			onChange(rule.withField(key))
		}
	)
}

@Composable
private fun RuleValueEditor(
	rule: Rule,
	type: RuleFieldType,
	playlists: List<Pair<String, String>>,
	onChange: (JsonElement) -> Unit
) {
	val numeric = type == RuleFieldType.Number || rule.op.lowercase().endsWith("inthelast")
	when {
		type == RuleFieldType.Playlist -> Dropdown(
			label = playlists.find { it.first == rule.value.ruleText() }?.second
				?: stringResource(Res.string.label_pick_playlist),
			options = playlists.map { (id, name) -> name to id },
			onPick = { onChange(JsonPrimitive(it)) }
		)
		rule.op == "inTheRange" -> {
			val values = (rule.value as? JsonArray)?.map { it.ruleText() } ?: listOf("", "")
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				listOf(Res.string.label_from, Res.string.label_to).forEachIndexed { i, label ->
					ValueField(
						value = values.getOrElse(i) { "" },
						label = stringResource(label),
						numeric = type == RuleFieldType.Number,
						date = type == RuleFieldType.Date,
						modifier = Modifier.weight(1f)
					) { text ->
						val updated = values.toMutableList().also { while (it.size < 2) it += "" }
						updated[i] = text
						onChange(JsonArray(updated.map { valueOf(it, numeric = type == RuleFieldType.Number) }))
					}
				}
			}
		}
		else -> ValueField(
			value = rule.value.ruleText(),
			label = stringResource(Res.string.label_value),
			numeric = numeric,
			date = type == RuleFieldType.Date && !numeric,
			suffix = if (rule.op.lowercase().endsWith("inthelast")) stringResource(Res.string.label_rule_days, "") else null,
			modifier = Modifier.fillMaxWidth()
		) { onChange(valueOf(it, numeric)) }
	}
}

@Composable
private fun ValueField(
	value: String,
	label: String,
	numeric: Boolean,
	date: Boolean,
	modifier: Modifier = Modifier,
	suffix: String? = null,
	onChange: (String) -> Unit
) {
	TextField(
		value = value,
		onValueChange = onChange,
		modifier = modifier,
		label = { Text(label) },
		placeholder = if (date) ({ Text(stringResource(Res.string.hint_date)) }) else null,
		suffix = suffix?.let { { Text(it.trim()) } },
		singleLine = true,
		keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text)
	)
}

/** Numbers go to Navidrome as numbers; anything else, or a half-typed number, as text. */
private fun valueOf(text: String, numeric: Boolean): JsonPrimitive =
	if (numeric) text.toLongOrNull()?.let(::JsonPrimitive) ?: text.toDoubleOrNull()?.let(::JsonPrimitive) ?: JsonPrimitive(text)
	else JsonPrimitive(text)

private fun Rule.withField(key: String): Rule {
	val newType = RuleField.of(key)?.type ?: RuleFieldType.Text
	if (newType == fieldType()) return copy(field = key)
	val op = RULE_OPERATORS.getValue(newType).first()
	return Rule(op, key, defaultRuleValue(newType, op))
}

private fun Rule.withOp(newOp: String, type: RuleFieldType): Rule =
	if ((newOp == "inTheRange") == (op == "inTheRange")) copy(op = newOp)
	else copy(op = newOp, value = defaultRuleValue(type, newOp))

@Composable
private fun <T> Dropdown(label: String, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
	var expanded by remember { mutableStateOf(false) }
	Box {
		OutlinedButton(onClick = { expanded = true }) {
			Text(label)
			Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(18.dp))
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			options.forEach { (text, value) ->
				DropdownMenuItem(text = { Text(text) }, onClick = {
					expanded = false
					onPick(value)
				})
			}
		}
	}
}

@Composable
private fun OrderAndLimit(criteria: SmartCriteria, onChange: (SmartCriteria) -> Unit) {
	val sortKey = criteria.sort?.substringBefore(',')?.trim()?.trimStart('+', '-')
	val random = sortKey.equals("random", ignoreCase = true)
	val descending = criteria.order.equals("desc", true) || criteria.sort?.trim()?.startsWith('-') == true
	var limitText by remember { mutableStateOf(criteria.limit?.toString() ?: "100") }

	Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
		Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(stringResource(Res.string.option_sort_by), Modifier.weight(1f))
				Dropdown(
					label = when {
						sortKey == null -> stringResource(Res.string.label_none)
						random -> stringResource(Res.string.field_random)
						else -> fieldLabel(sortKey)
					},
					options = listOf(stringResource(Res.string.label_none) to null, stringResource(Res.string.field_random) to "random") +
						RuleField.entries.filter { it != RuleField.Playlist }.map { stringResource(it.label) to it.key },
					onPick = { onChange(criteria.copy(sort = it, order = if (it == null || it == "random") null else criteria.order)) }
				)
			}
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(stringResource(Res.string.option_order), Modifier.weight(1f))
				SingleChoiceSegmentedButtonRow {
					listOf(false to Res.string.option_ascending, true to Res.string.option_descending)
						.forEachIndexed { i, (desc, label) ->
							SegmentedButton(
								selected = sortKey != null && !random && descending == desc,
								enabled = sortKey != null && !random,
								onClick = { onChange(criteria.copy(sort = sortKey, order = if (desc) "desc" else "asc")) },
								shape = SegmentedButtonDefaults.itemShape(i, 2)
							) { Text(stringResource(label)) }
						}
				}
			}
			Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
				Column(Modifier.weight(1f)) {
					Text(stringResource(Res.string.option_limit))
					Text(
						stringResource(Res.string.info_limit),
						style = MaterialTheme.typography.bodySmall,
						color = MaterialTheme.colorScheme.onSurfaceVariant
					)
				}
				if (criteria.limit != null) TextField(
					value = limitText,
					onValueChange = { text ->
						limitText = text
						text.toIntOrNull()?.takeIf { it > 0 }?.let { onChange(criteria.copy(limit = it)) }
					},
					modifier = Modifier.width(96.dp),
					singleLine = true,
					keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
				)
				Switch(
					checked = criteria.limit != null,
					onCheckedChange = { on ->
						onChange(criteria.copy(limit = if (on) limitText.toIntOrNull()?.takeIf { it > 0 } ?: 100 else null))
					}
				)
			}
		}
	}
}

/** Picks a rule's field: common ones first, then song details, lists, or any tag by name. */
@Composable
private fun FieldPicker(onDismissRequest: () -> Unit, onPick: (String) -> Unit) {
	val filter = rememberTextFieldState()
	var otherTag by remember { mutableStateOf(false) }
	val groups = listOf(Res.string.title_fields_common, Res.string.title_fields_details, Res.string.title_fields_lists)

	ModalBottomSheet(
		onDismissRequest = onDismissRequest,
		sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	) {
		Text(
			stringResource(Res.string.title_rule_field),
			style = MaterialTheme.typography.titleLarge,
			modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
		)
		TextField(
			state = filter,
			modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
			placeholder = { Text(stringResource(Res.string.hint_filter_fields)) },
			leadingIcon = { Icon(Icons.Outlined.Search, null) },
			lineLimits = TextFieldLineLimits.SingleLine
		)
		Column(Modifier.verticalScroll(rememberScrollState())) {
			groups.forEachIndexed { group, title ->
				val fields = RuleField.entries.filter { it.group == group }
					.map { it to stringResource(it.label) }
					.filter { (_, label) -> label.contains(filter.text, ignoreCase = true) }
				if (fields.isEmpty()) return@forEachIndexed
				Text(
					stringResource(title),
					style = MaterialTheme.typography.labelLarge,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
				)
				fields.forEach { (field, label) ->
					ListItem(onClick = { onPick(field.key) }, content = { Text(label) })
				}
			}
			ListItem(
				onClick = { otherTag = true },
				leadingContent = { Icon(Icons.Outlined.Add, null) },
				content = { Text(stringResource(Res.string.action_other_tag)) }
			)
		}
	}

	if (otherTag) {
		val tag = rememberTextFieldState()
		AlertDialog(
			onDismissRequest = { otherTag = false },
			title = { Text(stringResource(Res.string.action_other_tag)) },
			text = {
				TextField(
					state = tag,
					label = { Text(stringResource(Res.string.label_tag_name)) },
					lineLimits = TextFieldLineLimits.SingleLine
				)
			},
			confirmButton = {
				TextButton(
					enabled = tag.text.isNotBlank(),
					onClick = { onPick(tag.text.toString().trim().lowercase()) }
				) { Text(stringResource(Res.string.action_ok)) }
			},
			dismissButton = {
				TextButton(onClick = { otherTag = false }) { Text(stringResource(Res.string.action_cancel)) }
			}
		)
	}
}
