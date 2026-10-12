package eu.depau.loak.domain.manager

import com.russhwolf.settings.Settings
import eu.depau.loak.TestFakes
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.entities.SongEntity
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.settings.StartupQueue
import eu.depau.loak.ui.core.PlayerUiState
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * CF-QS (queue sync) at the logic layer: the real [QueueSyncManager] talks to a fake Subsonic
 * server (Ktor MockEngine) through the SessionManager engine seam, with a fake [SongDao] that
 * knows the server's songs. No network, no Room, no UI — covers server-queue push/pull
 * (`fetch`/`save`) and the startup-state reconciliation.
 */
class QueueSyncManagerTest {

	/**
	 * Settings backend for the preferences under test. NOTE: `Settings()` without arguments on
	 * JVM/desktop is `PreferencesSettings(Preferences.userRoot())` — a shared, persistent store
	 * that leaks values across tests in the same JVM. Tests that depend on a preference must set
	 * it explicitly instead of assuming a fresh default.
	 */
	private fun settings(): Settings = Settings()

	/**
	 * An in-memory [SongDao]: only [SongDao.getSongsByIds] is reached by queue sync, but the
	 * interface is Room-wide so the rest are no-ops.
	 */
	private fun fakeSongDao(vararg songs: SongEntity) = object : SongDao {
		private val byId = songs.associateBy { it.songId }
		override suspend fun getSongById(songId: String): SongEntity? = byId[songId]
		override suspend fun updateSongStarredAt(songId: String, starredAt: Instant?) {}
		override suspend fun updateSongRating(songId: String, userRating: Int) {}
		override suspend fun insertSongsIgnoringConflicts(songs: List<SongEntity>) {}
		override suspend fun updateSongs(songs: List<SongEntity>) {}
		override suspend fun getAllSongs(): List<SongEntity> = byId.values.toList()
		override suspend fun getSongsByAlbumId(albumId: String): List<SongEntity> = emptyList()
		override suspend fun deleteSong(songId: String) {}
		override suspend fun isSongStarred(songId: String): Boolean = false
		override fun observeSongStarred(songId: String): Flow<Boolean> = flowOf(false)
		override suspend fun getSongRating(songId: String): Int? = null
		override suspend fun clearAllSongs() {}
		override suspend fun getAllSongIds(): List<String> = byId.keys.toList()
		override suspend fun getSongsByIds(ids: List<String>): List<SongEntity> =
			ids.mapNotNull { byId[it] }
		override suspend fun getRandomSongs(count: Int): List<SongEntity> =
			byId.values.take(count)
		override suspend fun searchSongsList(query: String): List<SongEntity> = emptyList()
		override suspend fun getSongsByArtistId(artistId: String): List<SongEntity> = emptyList()
	}

	/**
	 * A fake Subsonic server: `ping`/`getUser` succeed by default, every `.view` endpoint not
	 * given a body answers an empty ok response, and given bodies are served under their exact
	 * path suffix. `/getOpenSubsonicExtensions.view` advertises `formPost` only, so queue save
	 * takes the form-POST path and fetch the classic `getPlayQueue`. GET/POST requests to
	 * `.view` endpoints are captured in [getLog]/[postLog].
	 */
	private fun fakeServer(vararg bodies: Pair<String, String>): FakeServer {
		val getLog = mutableListOf<String>()
		val postLog = mutableListOf<Map<String, List<String>>>()
		val handlers = bodies.toMap()
		val engine = MockEngine { request ->
			val path = request.url.encodedPath
			when {
				path.endsWith(".view") && request.method == HttpMethod.Post -> {
					val form = (request.body as? FormDataContent)?.formData?.entries()
						?.associate { it.key to it.value }
						.orEmpty()
					postLog.add(form)
					respond(okJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
				}

				path.endsWith("getOpenSubsonicExtensions.view") -> respond(
					extensionsJson,
					HttpStatusCode.OK,
					headersOf(HttpHeaders.ContentType, "application/json"),
				)

				path.endsWith(".view") -> {
					getLog.add(path.substringAfterLast('/'))
					val body = handlers.entries.firstOrNull { path.endsWith(it.key) }?.value ?: okJson
					respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
				}

				else -> respond(TestFakes.getUserJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
			}
		}
		return FakeServer(engine, getLog, postLog)
	}

	private class FakeServer(
		val engine: MockEngine,
		val getLog: MutableList<String>,
		val postLog: MutableList<Map<String, List<String>>>,
	)

	private fun sessionManager(engine: MockEngine, prefs: PreferenceManager): SessionManager {
		val settings = Settings()
		prefs.deviceName = "Test Box"
		val conn = ConnectivityManager(prefs)
		val stats = NetworkStatsManager(TestFakes.fakeNetworkStatsDao(), conn)
		return SessionManager(settings, prefs, stats, engine)
	}

	private fun manager(
		fake: FakeServer,
		prefs: PreferenceManager,
		vararg localSongs: SongEntity,
	) = QueueSyncManager(sessionManager(fake.engine, prefs), prefs, fakeSongDao(*localSongs))

	@Test
	fun fetchReturnsTheServerQueue() = runBlocking {
		val fake = fakeServer("/getPlayQueue.view" to playQueueJson)
		val prefs = PreferenceManager(settings())
		val manager = manager(fake, prefs, song("s1"), song("s2"), song("s3"))

		val queue = manager.fetch()

		assertEquals(listOf("s1", "s2", "s3"), queue?.songs?.map { it.id })
		assertEquals(1, queue?.currentIndex, "current index points at the current song")
		assertEquals(10_000, queue?.positionMs)
		assertEquals("Lo'ak (Other Phone)", queue?.changedBy)
		assertEquals("2026-10-10T12:00:00Z", queue?.changed?.toString(), "changed parses as Instant")
		assertTrue("getPlayQueue.view" in fake.getLog, "fetch asks the classic endpoint")
	}

	@Test
	fun fetchDropsSongsTheLibraryHasNotSeen() = runBlocking {
		// the server queue references s1, s2 and s3, but the library only knows s1
		val fake = fakeServer("/getPlayQueue.view" to playQueueJson)
		val prefs = PreferenceManager(settings())
		val manager = manager(fake, prefs, song("s1"))

		val queue = manager.fetch()

		assertEquals(listOf("s1"), queue?.songs?.map { it.id }, "unknown songs are dropped")
		assertEquals(0, queue?.currentIndex, "the first known song becomes the current one")
	}

	@Test
	fun savePushesTheQueueAndClearsPickedUp() = runBlocking {
		val fake = fakeServer()
		val prefs = PreferenceManager(settings())
		val manager = manager(fake, prefs, song("s1"), song("s2"))
		manager.markPickedUp(remote("Lo'ak (Other Phone)"))
		val state = PlayerUiState(
			queue = listOf(song("s1").toDomainModel(), song("s2").toDomainModel()),
			currentIndex = 1,
			progress = 0.5f
		)

		manager.save(state)

		val post = fake.postLog.single()
		assertEquals(listOf("s1", "s2"), post["id"], "both songs are pushed")
		assertEquals(listOf("s2"), post["current"], "the current song is sent by id")
		assertEquals(listOf("60000"), post["position"], "position is duration * progress")
		assertNull(manager.pickedUpFrom.value, "saving drops the picked-up marker")
	}

	@Test
	fun markPickedUpOnlySetForOtherClients() = runBlocking {
		val fake = fakeServer()
		val prefs = PreferenceManager(settings())
		val manager = manager(fake, prefs, song("s1"))

		// this device saved it: no pick-up shown
		manager.markPickedUp(remote("Lo'ak (Test Box)"))
		assertNull(manager.pickedUpFrom.value, "this device's own save is not a pick-up")

		manager.markPickedUp(remote("Other App"))
		assertEquals("Other App", manager.pickedUpFrom.value?.changedBy)
	}

	@Test
	fun startupStatePicksUpTheServerQueue() = runBlocking {
		val fake = fakeServer("/getPlayQueue.view" to playQueueJson)
		val prefs = PreferenceManager(settings())
		prefs.queueSyncEnabled = true
		prefs.startupQueue = StartupQueue.Server
		val manager = manager(fake, prefs, song("s1"), song("s2"), song("s3"))
		val local = PlayerUiState(queue = listOf(song("s9").toDomainModel()), currentIndex = 0)

		val state = manager.startupState(local)

		assertEquals(listOf("s1", "s2", "s3"), state?.queue?.map { it.id }, "remote queue wins")
		assertEquals(1, state?.currentIndex, "current song preserved from the server")
		assertEquals("s2", state?.currentSong?.id)
	}

	@Test
	fun startupStateFallsBackToLocalWhenServerHasNoQueue() = runBlocking {
		// no /getPlayQueue body: the server responds ok with no playQueue field
		val fake = fakeServer()
		val prefs = PreferenceManager(settings())
		prefs.queueSyncEnabled = true
		prefs.startupQueue = StartupQueue.Server
		val manager = manager(fake, prefs, song("s1"))
		val local = PlayerUiState(queue = listOf(song("s9").toDomainModel()), currentIndex = 0)

		val state = manager.startupState(local)

		assertEquals(listOf("s9"), state?.queue?.map { it.id }, "falls back to the local queue")
	}

	@Test
	fun startupStateFallsBackToLocalWhenServerUnreachable() = runBlocking {
		// every .view call throws; fetch propagates, serverState catches and falls back
		val prefs = PreferenceManager(settings())
		prefs.queueSyncEnabled = true
		prefs.startupQueue = StartupQueue.Server
		val session = sessionManager(
			MockEngine { error("server down") },
			prefs,
		)
		val manager = QueueSyncManager(session, prefs, fakeSongDao(song("s1")))
		val local = PlayerUiState(queue = listOf(song("s9").toDomainModel()), currentIndex = 0)

		val state = manager.startupState(local)

		assertEquals(listOf("s9"), state?.queue?.map { it.id }, "unreachable server keeps the local queue")
	}

	@Test
	fun startupStateWithServerSyncDisabledKeepsLocal() = runBlocking {
		val fake = fakeServer("/getPlayQueue.view" to playQueueJson)
		val prefs = PreferenceManager(settings())
		prefs.queueSyncEnabled = false
		val manager = manager(fake, prefs, song("s1"))
		val local = PlayerUiState(queue = listOf(song("s9").toDomainModel()), currentIndex = 0)

		val state = manager.startupState(local)

		assertEquals(listOf("s9"), state?.queue?.map { it.id }, "sync disabled ignores the server")
		assertTrue(fake.getLog.none { it == "getPlayQueue.view" }, "no fetch when sync is disabled")
	}

	@Test
	fun startupStateWithNothingReturnsNull() = runBlocking {
		val fake = fakeServer()
		val prefs = PreferenceManager(settings())
		prefs.startupQueue = StartupQueue.Nothing
		val manager = manager(fake, prefs)

		assertNull(manager.startupState(PlayerUiState()), "Nothing means no startup queue")
	}

	// --- wire shapes & fixtures ---

	private val okJson = """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""

	/** Advertises `formPost`: queue save uses form POST, fetch uses `getPlayQueue` (no index). */
	private val extensionsJson = """
		{"subsonic-response":{"status":"ok","version":"1.16.1","openSubsonic":true,"data":[
			{"name":"formPost","versions":[1]}
		]}}
	""".trimIndent()

	private val playQueueJson = """
		{"subsonic-response":{"status":"ok","version":"1.16.1","playQueue":{
			"entry":[
				{"id":"s1","title":"One"},
				{"id":"s2","title":"Two"},
				{"id":"s3","title":"Three"}
			],
			"current":"s2",
			"position":10000,
			"changed":"2026-10-10T12:00:00Z",
			"changedBy":"Lo'ak (Other Phone)"
		}}}
	""".trimIndent()

	private fun song(id: String) = SongEntity(
		songId = id,
		title = id,
		artistName = "Artist",
		artistId = "a1",
		albumTitle = null,
		belongsToAlbumId = null,
		parentId = null,
		comment = null,
		trackNumber = 1,
		discNumber = 1,
		isrc = emptyList(),
		year = 2024,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = 2.minutes,
		bpm = null,
		contributors = emptyList(),
		playCount = 0,
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 1000,
		fileExtension = "mp3",
		mimeType = "audio/mpeg",
		filePath = "/$id.mp3",
		starredAt = null,
		coverArtId = null,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown,
		artists = emptyList(),
		albumArtists = emptyList(),
		isExternal = false
	)

	private fun remote(changedBy: String) = ServerQueue(
		songs = listOf(song("s1").toDomainModel()),
		currentIndex = 0,
		positionMs = 0,
		changed = null,
		changedBy = changedBy
	)
}
