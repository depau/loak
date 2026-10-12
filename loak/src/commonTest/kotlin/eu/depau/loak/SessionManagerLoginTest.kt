package eu.depau.loak

import com.russhwolf.settings.Settings
import dev.zt64.subsonic.api.model.Role
import eu.depau.loak.data.database.dao.NetworkStatsDao
import eu.depau.loak.data.database.entities.NetworkStatsEntity
import eu.depau.loak.data.database.entities.SyncRunEntity
import eu.depau.loak.data.database.entities.TransferCategory
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CF-01 (login) at the logic layer: the real [SessionManager] talks to a fake Subsonic
 * server (Ktor MockEngine) through the injectable engine seam, with in-memory settings.
 * No network, no UI, no user data — the hermetic gate for the login flow.
 */
class SessionManagerLoginTest {

	/** In-memory settings backend (multiplatform-settings no-arg). */
	private fun settings(): Settings = Settings()

	/** Fake [NetworkStatsDao]: login records transfer stats, but no DB is needed here. */
	private fun fakeNetworkStatsDao() = object : NetworkStatsDao {
		override suspend fun add(hour: Long, metered: Boolean, category: TransferCategory, bytes: Long, requests: Long) {}
		override suspend fun getSince(since: Long) = emptyList<NetworkStatsEntity>()
		override suspend fun pruneTransfers(before: Long) {}
		override suspend fun insertSyncRun(run: SyncRunEntity) {}
		override suspend fun getSyncRuns(limit: Int) = emptyList<SyncRunEntity>()
		override suspend fun pruneSyncRuns(before: Instant) {}
	}

	/** The exact `getUser` response shape the subsonic client parses (verified empirically). */
	private val getUserJson = """
		{"subsonic-response":{"status":"ok","version":"1.16.1","user":{
			"username":"demo","email":"demo@demo.navidrome.org","scrobblingEnabled":true,
			"folder":[],"roles":["STREAM","PLAYLIST","SHARE"]
		}}}
	""".trimIndent()

	/** A fake Subsonic server that answers `ping` and `getUser` with parseable JSON. */
	private fun fakeServer(onRequest: (io.ktor.client.request.HttpRequestData) -> String = { _ ->
		getUserJson
	}) = MockEngine { request ->
		val body = when {
			request.url.encodedPath.endsWith("/getUser.view") -> onRequest(request)
			else -> """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
		}
		respond(
			content = body,
			status = HttpStatusCode.OK,
			headers = headersOf(HttpHeaders.ContentType, "application/json")
		)
	}

	private fun sessionManager(
		settings: Settings,
		engine: io.ktor.client.engine.mock.MockEngine,
	): SessionManager {
		val prefs = PreferenceManager(settings)
		val conn = ConnectivityManager(prefs)
		val stats = NetworkStatsManager(fakeNetworkStatsDao(), conn)
		return SessionManager(settings, prefs, stats, engine)
	}

	@Test
	fun loginSucceedsAgainstFakeServer() = runTest {
		val settings = settings()
		val engine = fakeServer()
		val session = sessionManager(settings, engine)

		session.login("http://fake.navidrome.org", "demo", "demo")

		assertTrue(session.isLoggedIn.value, "login flips isLoggedIn")
		assertEquals("demo", session.username)
		assertEquals("http://fake.navidrome.org", session.instanceUrl)
		assertEquals(
			"demo@http://fake.navidrome.org".hashCode().toUInt().toString(16),
			session.serverKey.value,
			"serverKey derives from user@host"
		)
	}

	@Test
	fun loginSetsPersistedCredentials() = runTest {
		val settings = settings()
		val session = sessionManager(settings, fakeServer())

		session.login("https://server.example", "alice", "secret")

		assertEquals("https://server.example", settings.getString("instanceUrl", ""))
		assertEquals("alice", settings.getString("username", ""))
		assertEquals("secret", settings.getString("password", ""))
	}
}
