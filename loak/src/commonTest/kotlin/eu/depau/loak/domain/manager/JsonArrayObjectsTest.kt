package eu.depau.loak.domain.manager

import kotlin.test.Test
import kotlin.test.assertEquals

/** The streamed Lidarr catalog must split into the same elements however the bytes arrive. */
class JsonArrayObjectsTest {
	private val elements = listOf(
		"""{"id":1,"title":"a } \" { [","artist":{"x":[1,{"y":2}]}}""",
		"""{"id":2,"title":"Björk – Homogenic 🎵"}""",
		"""{"id":3,"tags":[],"path":"C:\\music\\"}"""
	)
	private val body = (" [ " + elements.joinToString(" ,\n") + " ] ").encodeToByteArray()

	private fun split(chunk: Int): List<String> {
		val out = mutableListOf<String>()
		val objects = JsonArrayObjects { out += it }
		body.toList().chunked(chunk).forEach { objects.feed(it.toByteArray(), it.size) }
		return out
	}

	@Test
	fun splitsElementsAtEveryChunkSize() {
		for (chunk in listOf(1, 2, 3, 7, body.size)) assertEquals(elements, split(chunk), "chunk $chunk")
	}

	@Test
	fun emptyArrayHasNoElements() {
		val out = mutableListOf<String>()
		JsonArrayObjects { out += it }.feed("[]".encodeToByteArray(), 2)
		assertEquals(emptyList(), out)
	}
}
