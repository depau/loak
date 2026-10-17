package eu.depau.loak.domain.history

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import eu.depau.loak.domain.manager.PreferenceManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * An in-memory [Settings]: the no-arg one is a persistent store shared with every other test
 * (and with the machine's real one on the JVM).
 */
private class InMemorySettings : Settings {
	private val values = mutableMapOf<String, Any>()

	override val keys: Set<String> get() = values.keys
	override val size: Int get() = values.size
	override fun clear() = values.clear()
	override fun remove(key: String) {
		values.remove(key)
	}

	override fun hasKey(key: String) = key in values
	override fun putString(key: String, value: String) {
		values[key] = value
	}

	override fun getStringOrNull(key: String) = values[key] as? String
	override fun getString(key: String, defaultValue: String) = getStringOrNull(key) ?: defaultValue
	override fun putInt(key: String, value: Int) {
		values[key] = value
	}

	override fun getIntOrNull(key: String) = values[key] as? Int
	override fun getInt(key: String, defaultValue: Int) = getIntOrNull(key) ?: defaultValue
	override fun putLong(key: String, value: Long) {
		values[key] = value
	}

	override fun getLongOrNull(key: String) = values[key] as? Long
	override fun getLong(key: String, defaultValue: Long) = getLongOrNull(key) ?: defaultValue
	override fun putFloat(key: String, value: Float) {
		values[key] = value
	}

	override fun getFloatOrNull(key: String) = values[key] as? Float
	override fun getFloat(key: String, defaultValue: Float) = getFloatOrNull(key) ?: defaultValue
	override fun putDouble(key: String, value: Double) {
		values[key] = value
	}

	override fun getDoubleOrNull(key: String) = values[key] as? Double
	override fun getDouble(key: String, defaultValue: Double) = getDoubleOrNull(key) ?: defaultValue
	override fun putBoolean(key: String, value: Boolean) {
		values[key] = value
	}

	override fun getBooleanOrNull(key: String) = values[key] as? Boolean
	override fun getBoolean(key: String, defaultValue: Boolean) = getBooleanOrNull(key) ?: defaultValue
}

/**
 * A source that cannot be read at all is not a source that had nothing to say.
 *
 * The Recap names the sources it is missing, so a Koito answering 404 to everything — behind a
 * reverse proxy that does not know the host, say — must not come out looking like a quiet month.
 * Both are empty [HistoryRead.plays]; only the first is [HistoryRead.unreadable].
 */
class HistoryReadTest {
	private fun koitoSource(engine: MockEngine): KoitoHistorySource {
		val prefs = PreferenceManager(
			InMemorySettings().apply {
				this["koitoUrl"] = "https://koito.example.org"
				this["koitoApiKey"] = "token"
			}
		)
		return KoitoHistorySource(prefs, engine)
	}

	private fun refused() = MockEngine { respondError(HttpStatusCode.NotFound) }

	private fun page(body: String) = MockEngine {
		respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
	}

	@Test
	fun `a source that refuses every request reads as unreadable not as empty`() = runTest {
		assertNull(koitoSource(refused()).recentListens(0, 10, 100))
	}

	@Test
	fun `a page with no listens is a real answer`() = runTest {
		val source = koitoSource(page("""{"items":[],"has_next_page":false}"""))
		assertEquals(emptyList(), source.recentListens(0, 10, 100))
	}

	@Test
	fun `a page of listens is read`() = runTest {
		val source = koitoSource(
			page(
				"""
				{"items":[
					{"time":"2024-03-05T10:00:00Z","track":{"title":"Dracula","artists":[{"name":"Tame Impala"}]}}
				],"has_next_page":false}
				"""
			)
		)
		val listens = source.recentListens(0, 10, 100)
		assertEquals(1, listens?.size)
		assertEquals("Dracula", listens?.first()?.title)
		assertEquals("Tame Impala", listens?.first()?.artist)
	}

	@Test
	fun `a combined history keeps the plays of the readable sources and names the rest`() = runTest {
		val local = object : ListeningHistory {
			override val id = "local"
			override suspend fun reads(from: Long, to: Long) = HistoryRead(mapOf("s1" to listOf(5L)))
		}
		val refused = object : ListeningHistory {
			override val id = "koito"
			override suspend fun reads(from: Long, to: Long) = HistoryRead(unreadable = listOf("koito"))
		}
		val combined = CombinedHistory { listOf(local, refused) }

		val read = combined.reads(0, 10)
		assertEquals(mapOf("s1" to listOf(5L)), read.plays)
		assertEquals(listOf("koito"), read.unreadable)
		// the eyebrow and the share text still describe what the user turned on
		assertEquals("local+koito", combined.id)
	}
}
