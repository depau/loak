package eu.depau.loak

import com.russhwolf.settings.Settings
import eu.depau.loak.data.database.dao.NetworkStatsDao
import eu.depau.loak.data.database.dao.PlayLogDao
import eu.depau.loak.data.database.entities.NetworkStatsEntity
import eu.depau.loak.data.database.entities.PlayLogEntity
import eu.depau.loak.data.database.entities.SongPlayCount
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
import kotlin.time.Instant

/**
 * Shared test helpers for CF flow gates. Reused by every
 * commonTest unit test; the desktop UI tests keep their own copy
 * (different source set — no shared test classpath).
 *
 * The wire shapes are empirically verified against the real subsonic
 * client in `SessionManagerLoginTest` / the CF-01 spread — do not
 * "fix" them or response parsing breaks.
 */
object TestFakes {

	/** The exact `getUser` response body the client parses. */
	val getUserJson: String = """
		{"subsonic-response":{"status":"ok","version":"1.16.1","user":{
			"username":"demo","email":"demo@demo.navidrome.org","scrobblingEnabled":true,
			"folder":[],"roles":["STREAM","PLAYLIST","SHARE"]
		}}}
	""".trimIndent()

	fun pingJson(): String =
		"""{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""

	/** A fake Subsonic server: `ping` and `getUser` succeed. */
	fun fakeServer(): MockEngine = MockEngine { request ->
		val body = when {
			request.url.encodedPath.endsWith("/getUser.view") -> getUserJson
			else -> pingJson()
		}
		respond(
			content = body,
			status = HttpStatusCode.OK,
			headers = headersOf(HttpHeaders.ContentType, "application/json")
		)
	}

	/** No-op [NetworkStatsDao] so managers needing it don't require a Room DB. */
	fun fakeNetworkStatsDao(): NetworkStatsDao = object : NetworkStatsDao {
		override suspend fun add(hour: Long, metered: Boolean, category: TransferCategory, bytes: Long, requests: Long) {}
		override suspend fun getSince(since: Long) = emptyList<NetworkStatsEntity>()
		override suspend fun pruneTransfers(before: Long) {}
		override suspend fun insertSyncRun(run: SyncRunEntity) {}
		override suspend fun getSyncRuns(limit: Int) = emptyList<SyncRunEntity>()
		override suspend fun pruneSyncRuns(before: Instant) {}
	}

	/** An in-memory [PlayLogDao], so tests of what reads the play log need no Room DB. */
	fun fakePlayLogDao(): PlayLogDao = object : PlayLogDao {
		private val plays = mutableListOf<PlayLogEntity>()

		override suspend fun insert(play: PlayLogEntity) {
			plays += play.copy(id = plays.size + 1L)
		}

		override suspend fun playsSince(since: Long) =
			plays.filter { it.playedAt >= since }.sortedBy { it.playedAt }

		override suspend fun playCountsSince(since: Long) = plays
			.filter { it.playedAt >= since }
			.groupingBy { it.songId }.eachCount()
			.map { SongPlayCount(it.key, it.value) }

		override suspend fun lastPlay() = plays.maxByOrNull { it.playedAt }

		override suspend fun prune(before: Long) {
			plays.removeAll { it.playedAt < before }
		}
	}

	/**
	 * A SessionManager on the fake server.
	 *
	 * NOTE: the no-arg `Settings()` resolves to a persistent store on the JVM
	 * (`java.util.Preferences.userRoot()`) — shared across tests and even builds.
	 * Tests that read prefs the way production does (e.g. queueSyncEnabled,
	 * saved credentials) MUST pass an isolated store, e.g.
	 * `PropertiesSettings(Properties())` on desktop (see AudioStoreTest).
	 */
	fun sessionManager(
		settings: Settings = Settings(),
		engine: MockEngine = fakeServer(),
	): SessionManager {
		val prefs = PreferenceManager(settings)
		val conn = ConnectivityManager(prefs)
		val stats = NetworkStatsManager(fakeNetworkStatsDao(), conn)
		return SessionManager(settings, prefs, stats, engine)
	}
}
