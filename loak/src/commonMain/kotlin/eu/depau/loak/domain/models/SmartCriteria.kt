package eu.depau.loak.domain.models

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One node of a Navidrome smart playlist's rules: a single rule, or a group of them. */
sealed interface RuleNode

/** `{"<op>": {"<field>": <value>}}`. Values stay JSON, so anything the editor doesn't know survives. */
data class Rule(val op: String, val field: String, val value: JsonElement) : RuleNode

/** `{"all": [...]}` or `{"any": [...]}`; groups nest to any depth. */
data class RuleGroup(val any: Boolean, val children: List<RuleNode>) : RuleNode

/**
 * A Navidrome smart playlist's criteria (model/criteria in Navidrome's source). [extra] keeps the
 * keys the editor doesn't touch (limitPercent, offset, refreshDelay, …) as they came.
 */
data class SmartCriteria(
	val root: RuleGroup,
	val sort: String? = null,
	val order: String? = null,
	val limit: Int? = null,
	val extra: Map<String, JsonElement> = emptyMap()
) {
	fun toJson(): JsonObject = buildJsonObject {
		put(if (root.any) "any" else "all", JsonArray(root.children.map { it.toJson() }))
		sort?.let { put("sort", JsonPrimitive(it)) }
		order?.let { put("order", JsonPrimitive(it)) }
		limit?.let { put("limit", JsonPrimitive(it)) }
		extra.forEach { (k, v) -> put(k, v) }
	}

	companion object {
		private val OWN_KEYS = setOf("all", "any", "sort", "order", "limit")

		fun parse(json: JsonObject): SmartCriteria {
			val any = "any" in json
			val children = (json[if (any) "any" else "all"] as? JsonArray).orEmpty().mapNotNull(::parseNode)
			return SmartCriteria(
				root = RuleGroup(any, children),
				sort = (json["sort"] as? JsonPrimitive)?.content,
				order = (json["order"] as? JsonPrimitive)?.content,
				limit = (json["limit"] as? JsonPrimitive)?.intOrNull?.takeIf { it > 0 },
				extra = json.filterKeys { it !in OWN_KEYS }
			)
		}

		private fun parseNode(element: JsonElement): RuleNode? {
			val (key, body) = (element as? JsonObject)?.entries?.singleOrNull() ?: return null
			if (key.equals("all", true) || key.equals("any", true)) {
				return RuleGroup(key.equals("any", true), body.jsonArray.mapNotNull(::parseNode))
			}
			val (field, value) = body.jsonObject.entries.singleOrNull() ?: return null
			return Rule(key, field, value)
		}
	}
}

private fun RuleNode.toJson(): JsonObject = when (this) {
	is RuleGroup -> buildJsonObject { put(if (any) "any" else "all", JsonArray(children.map { it.toJson() })) }
	is Rule -> buildJsonObject { put(op, buildJsonObject { put(field, value) }) }
}

/** What a rule's value means, which picks its operators and its input. */
enum class RuleFieldType { Text, Number, Date, Bool, Playlist }

/** Operators Navidrome accepts for each kind of field (model/criteria/operators.go). */
val RULE_OPERATORS = mapOf(
	RuleFieldType.Text to listOf("contains", "notContains", "is", "isNot", "startsWith", "endsWith"),
	RuleFieldType.Number to listOf("gt", "lt", "is", "isNot", "inTheRange"),
	RuleFieldType.Date to listOf("inTheLast", "notInTheLast", "before", "after", "inTheRange"),
	RuleFieldType.Bool to listOf("is"),
	RuleFieldType.Playlist to listOf("inPlaylist", "notInPlaylist")
)

/**
 * The fields the editor offers, in picker order, grouped. Navidrome takes more (any tag in the
 * server's mappings, every artist role): "Other tag…" types one in as text.
 */
enum class RuleField(val key: String, val type: RuleFieldType, val group: Int) {
	Title("title", RuleFieldType.Text, 0),
	Artist("artist", RuleFieldType.Text, 0),
	AlbumArtist("albumartist", RuleFieldType.Text, 0),
	Album("album", RuleFieldType.Text, 0),
	Genre("genre", RuleFieldType.Text, 0),
	Year("year", RuleFieldType.Number, 0),
	Rating("rating", RuleFieldType.Number, 0),
	Loved("loved", RuleFieldType.Bool, 0),
	PlayCount("playcount", RuleFieldType.Number, 0),
	LastPlayed("lastplayed", RuleFieldType.Date, 0),
	DateAdded("dateadded", RuleFieldType.Date, 0),
	Duration("duration", RuleFieldType.Number, 1),
	Bpm("bpm", RuleFieldType.Number, 1),
	Bitrate("bitrate", RuleFieldType.Number, 1),
	FileType("filetype", RuleFieldType.Text, 1),
	Comment("comment", RuleFieldType.Text, 1),
	Mood("mood", RuleFieldType.Text, 1),
	Compilation("compilation", RuleFieldType.Bool, 1),
	// inPlaylist / notInPlaylist name the field "id": {"inPlaylist": {"id": "<playlist id>"}}
	Playlist("id", RuleFieldType.Playlist, 2);

	companion object {
		fun of(key: String) = entries.find { it.key.equals(key, ignoreCase = true) }
	}
}

/** A rule's field type: known fields by name, else guessed from the operator (other tags are text). */
fun Rule.fieldType(): RuleFieldType = when {
	op.equals("inPlaylist", true) || op.equals("notInPlaylist", true) -> RuleFieldType.Playlist
	else -> RuleField.of(field)?.takeIf { it != RuleField.Playlist }?.type ?: RuleFieldType.Text
}

/** A new rule's starting value for a field of [type] with [op]. */
fun defaultRuleValue(type: RuleFieldType, op: String): JsonElement = when {
	type == RuleFieldType.Bool -> JsonPrimitive(true)
	op == "inTheRange" -> JsonArray(listOf(JsonPrimitive(""), JsonPrimitive("")))
	else -> JsonPrimitive("")
}

/** The value as plain text, for summaries and text inputs. */
fun JsonElement.ruleText(): String = when (this) {
	is JsonPrimitive -> content
	is JsonArray -> joinToString("–") { it.ruleText() }
	is JsonObject -> toString()
}
