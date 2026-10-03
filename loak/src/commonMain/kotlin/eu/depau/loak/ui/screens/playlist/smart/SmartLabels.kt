package eu.depau.loak.ui.screens.playlist.smart

import androidx.compose.runtime.Composable
import eu.depau.loak.domain.models.Rule
import eu.depau.loak.domain.models.RuleField
import eu.depau.loak.domain.models.RuleFieldType
import eu.depau.loak.domain.models.RuleGroup
import eu.depau.loak.domain.models.RuleNode
import eu.depau.loak.domain.models.SmartCriteria
import eu.depau.loak.domain.models.fieldType
import eu.depau.loak.domain.models.ruleText
import eu.depau.loak.generated.resources.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

val RuleField.label: StringResource
	get() = when (this) {
		RuleField.Title -> Res.string.field_title
		RuleField.Artist -> Res.string.field_artist
		RuleField.AlbumArtist -> Res.string.field_album_artist
		RuleField.Album -> Res.string.field_album
		RuleField.Genre -> Res.string.field_genre
		RuleField.Year -> Res.string.field_year
		RuleField.Rating -> Res.string.field_rating
		RuleField.Loved -> Res.string.field_loved
		RuleField.PlayCount -> Res.string.field_play_count
		RuleField.LastPlayed -> Res.string.field_last_played
		RuleField.DateAdded -> Res.string.field_date_added
		RuleField.Duration -> Res.string.field_duration
		RuleField.Bpm -> Res.string.field_bpm
		RuleField.Bitrate -> Res.string.field_bitrate
		RuleField.FileType -> Res.string.field_file_type
		RuleField.Comment -> Res.string.field_comment
		RuleField.Mood -> Res.string.field_mood
		RuleField.Compilation -> Res.string.field_compilation
		RuleField.Playlist -> Res.string.field_playlist
	}

fun operatorLabel(op: String): StringResource? = when (op.lowercase()) {
	"is", "eq" -> Res.string.op_is
	"isnot" -> Res.string.op_is_not
	"contains" -> Res.string.op_contains
	"notcontains" -> Res.string.op_not_contains
	"startswith" -> Res.string.op_starts_with
	"endswith" -> Res.string.op_ends_with
	"gt" -> Res.string.op_gt
	"lt" -> Res.string.op_lt
	"intherange" -> Res.string.op_in_range
	"before" -> Res.string.op_before
	"after" -> Res.string.op_after
	"inthelast" -> Res.string.op_in_the_last
	"notinthelast" -> Res.string.op_not_in_the_last
	"inplaylist" -> Res.string.op_in_playlist
	"notinplaylist" -> Res.string.op_not_in_playlist
	else -> null
}

/** A field's name for people: known fields translated, other tags as Navidrome names them. */
@Composable
fun fieldLabel(key: String): String = RuleField.of(key)?.let { stringResource(it.label) } ?: key

@Composable
fun operatorText(op: String): String = operatorLabel(op)?.let { stringResource(it) } ?: op

/** "Genre contains Jazz • Rating over 3 • (Loved is yes or Play count over 10)" */
@Composable
fun ruleSummary(node: RuleNode, playlistNames: Map<String, String>, nested: Boolean = false): String = when (node) {
	is Rule -> {
		val type = node.fieldType()
		val value = when {
			type == RuleFieldType.Playlist -> playlistNames[node.value.ruleText()] ?: node.value.ruleText()
			type == RuleFieldType.Bool -> stringResource(
				if ((node.value as? JsonPrimitive)?.booleanOrNull != false) Res.string.label_yes else Res.string.label_no
			)
			node.op.lowercase().endsWith("inthelast") -> stringResource(Res.string.label_rule_days, node.value.ruleText())
			else -> node.value.ruleText()
		}
		if (type == RuleFieldType.Playlist) "${operatorText(node.op)} $value"
		else "${fieldLabel(node.field)} ${operatorText(node.op)} $value"
	}
	is RuleGroup -> {
		val separator = if (node.any) " ${stringResource(Res.string.label_rule_or)} " else " • "
		val parts = node.children.map { ruleSummary(it, playlistNames, nested = true) }
		parts.joinToString(separator).let { if (nested && parts.size > 1) "($it)" else it }
	}
}

/** "Random order, up to 100 songs" / "Sorted by Last played" */
@Composable
fun orderSummary(criteria: SmartCriteria): String? {
	val sort = criteria.sort?.substringBefore(',')?.trim()?.trimStart('+', '-')
	val order = when {
		sort == null -> null
		sort.equals("random", true) -> stringResource(Res.string.label_rule_order_random)
		else -> stringResource(Res.string.label_rule_sorted_by, fieldLabel(sort))
	}
	val limit = criteria.limit?.let { stringResource(Res.string.label_rule_limit, it) }
	return listOfNotNull(order, limit).joinToString(", ").ifEmpty { null }
}
