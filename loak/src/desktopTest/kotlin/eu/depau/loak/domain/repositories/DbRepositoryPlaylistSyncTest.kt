package eu.depau.loak.domain.repositories

import androidx.room3.Room
import com.russhwolf.settings.PropertiesSettings
import eu.depau.loak.data.database.CacheDatabase
import eu.depau.loak.data.database.entities.PlaylistEntity
import eu.depau.loak.di.JdbcSQLiteDriver
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DbRepositoryPlaylistSyncTest {
	@Test
	fun deletesPlaylistRemovedBetweenListAndSongFetch() = runBlocking {
		val db = Room.inMemoryDatabaseBuilder<CacheDatabase>()
			.setDriver(JdbcSQLiteDriver())
			.build()
		try {
			db.playlistDao().insertPlaylist(
				PlaylistEntity(
					playlistId = "gone",
					name = "Gone",
					comment = null,
					owner = null,
					coverArtId = null,
					songCount = 1,
					duration = 1.seconds,
					public = null,
					readOnly = null,
					createdAt = Instant.fromEpochSeconds(0),
					modifiedAt = Instant.fromEpochSeconds(0),
					validUntil = null,
					allowedUsers = emptyList()
				)
			)
			val settings = PropertiesSettings(Properties())
			val preferences = PreferenceManager(settings)
			val stats = NetworkStatsManager(db.networkStatsDao(), ConnectivityManager(preferences))
			val session = SessionManager(
				settings,
				preferences,
				stats,
				MockEngine {
					respond(
						content = """{"subsonic-response":{"status":"failed","version":"1.16.1","error":{"code":70,"message":"playlist not found"}}}""",
						status = HttpStatusCode.OK,
						headers = headersOf(HttpHeaders.ContentType, "application/json")
					)
				}
			)
			val repository = DbRepository(
				db.albumDao(),
				db.playlistDao(),
				db.songDao(),
				db.genreDao(),
				db.artistDao(),
				db.radioDao(),
				db.lyricDao(),
				db.syncActionDao(),
				session,
				stats
			)

			assertTrue(repository.syncPlaylistSongs("gone").getOrThrow().isEmpty())
			assertNull(db.playlistDao().getPlaylistById("gone"))
		} finally {
			db.close()
		}
	}
}
