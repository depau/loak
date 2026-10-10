package eu.depau.loak.domain.manager

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LidarrQueueTest {
	@Test
	fun parsesPaginatedQueueRecords() {
		val queue = lidarrQueueItemsOf(Json.parseToJsonElement("""{
			"totalRecords": 4,
			"records": [{
				"id": 7,
				"albumId": 12,
				"artistId": 34,
				"size": 1000.0,
				"sizeleft": 250.0,
				"estimatedCompletionTime": "2026-10-10T12:00:00Z",
				"album": {
					"title": "Album",
					"artist": {"artistName": "Artist"}
				},
				"quality": {"quality": {"name": "FLAC"}}
			}]
		}"""))

		val item = assertNotNull(queue.singleOrNull())
		assertEquals(7L, item.id)
		assertEquals("Album", item.title)
		assertEquals("Artist", item.artistName)
		assertEquals("FLAC", item.quality)
		assertEquals(0.75f, item.progress)
	}
}
