package eu.depau.loak.domain.models

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class SmartCriteriaTest {
	private val sample = """
		{"all":[
			{"contains":{"genre":"Jazz"}},
			{"gt":{"rating":3}},
			{"any":[{"is":{"loved":true}},{"all":[{"gt":{"playcount":10}},{"inPlaylist":{"id":"abc"}}]}]}
		],"sort":"random","limit":100,"refreshDelay":"1h","limitPercent":0}
	""".trimIndent()

	@Test
	fun roundTripKeepsEverything() {
		val json = Json.parseToJsonElement(sample).jsonObject
		val criteria = SmartCriteria.parse(json)
		assertEquals(false, criteria.root.any)
		assertEquals(3, criteria.root.children.size)
		val nested = criteria.root.children[2] as RuleGroup
		assertEquals(true, nested.any)
		assertEquals(RuleGroup(false, listOf(Rule("gt", "playcount", JsonPrimitive(10)), Rule("inPlaylist", "id", JsonPrimitive("abc")))), nested.children[1])
		assertEquals("random", criteria.sort)
		assertEquals(100, criteria.limit)
		assertEquals(setOf("refreshDelay", "limitPercent"), criteria.extra.keys)
		assertEquals(json, criteria.toJson())
	}

	@Test
	fun fieldTypes() {
		assertEquals(RuleFieldType.Playlist, Rule("notInPlaylist", "id", JsonPrimitive("")).fieldType())
		assertEquals(RuleFieldType.Date, Rule("inTheLast", "lastplayed", JsonPrimitive(30)).fieldType())
		assertEquals(RuleFieldType.Text, Rule("contains", "grouping", JsonPrimitive("x")).fieldType())
	}
}
