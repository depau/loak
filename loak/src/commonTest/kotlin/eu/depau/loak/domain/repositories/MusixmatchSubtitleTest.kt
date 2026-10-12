package eu.depau.loak.domain.repositories

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** LOAK-1H/1K: Musixmatch sends `[]` for empty objects; that must read as "no lyrics", not throw. */
class MusixmatchSubtitleTest {
	private fun message(raw: String) = Json.parseToJsonElement(raw)

	@Test
	fun readsSubtitleBody() {
		val m = message(
			"""{"body":{"macro_calls":{"track.subtitles.get":{"message":{"body":
			{"subtitle_list":[{"subtitle":{"subtitle_body":"[00:01.00] hi"}}]}}}}}}"""
		)
		assertEquals("[00:01.00] hi", musixmatchSubtitle(m))
	}

	@Test
	fun emptyArrayBodiesAreNoLyrics() {
		assertNull(musixmatchSubtitle(message("""{"body":[]}""")))
		assertNull(
			musixmatchSubtitle(
				message("""{"body":{"macro_calls":{"track.subtitles.get":{"message":{"body":[]}}}}}""")
			)
		)
	}
}
