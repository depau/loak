package eu.depau.loak.domain.manager

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class LidarrInfoTest {
	private val json = Json { ignoreUnknownKeys = true }

	@Test
	fun parsesStatusRootFoldersAndQueue() {
		val status = json.parseToJsonElement("""{"appName":"Lidarr","version":"3.1.0.4875"}""")
		val rootFolders = json.parseToJsonElement(
			"""[{"id":1,"path":"/music"},{"id":2,"path":"/more"}]"""
		)
		val queue = json.parseToJsonElement("""{"totalRecords":7,"records":[]}""")
		val info = lidarrInfoOf(status, rootFolders, queue)
		assertEquals("3.1.0.4875", info.version)
		assertEquals(2, info.rootFolderCount)
		assertEquals(7, info.queueCount)
	}

	@Test
	fun missingCountsFallBackToZeroAndUnknown() {
		val info = lidarrInfoOf(null, null, null)
		assertEquals("unknown", info.version)
		assertEquals(0, info.rootFolderCount)
		assertEquals(0, info.queueCount)
	}

	@Test
	fun abortedCountCallsDoNotFailTheWholeRead() {
		val status = json.parseToJsonElement("""{"version":"3.1.0.4875"}""")
		// rootfolder answered garbage, queue never answered: only their counts drop
		val info = lidarrInfoOf(status, json.parseToJsonElement("""{"not":"an array"}"""), null)
		assertEquals("3.1.0.4875", info.version)
		assertEquals(0, info.rootFolderCount)
		assertEquals(0, info.queueCount)
	}
}
