package eu.depau.loak.domain.repositories

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import eu.depau.loak.TestFakes
import eu.depau.loak.data.database.dao.LyricDao
import eu.depau.loak.data.database.entities.LyricEntity
import eu.depau.loak.domain.manager.ConnectivityManager
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainExplicitStatus
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.lyrics.LyricsConfig
import eu.depau.loak.domain.models.lyrics.LyricsProvider
import eu.depau.loak.domain.models.settings.OfflineMode
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * CF-LYR (lyrics) at the logic layer: the real [LyricsRepository] against a fake [LyricDao]
 * and a fake LRC server (Ktor MockEngine — also pinned to the test scheduler — injected
 * through the repository's engine seam). No network, no Room. Locks the project invariants:
 * saved (LOCAL_PROVIDER) lyrics are pinned first, offline returns only the last-known copy
 * with zero network calls, results come in config priority order as each provider answers,
 * a failing provider is skipped, and only the highest-priority non-saved result is cached.
 */
class LyricsFlowTest {

	private val json = Json { ignoreUnknownKeys = true }

	/** A fake LRC server pinned to the test scheduler (same precedent as the scrobble gate). */
	private fun fakeServer(
		scheduler: TestCoroutineScheduler,
		requests: MutableList<Pair<String, String?>>,
		slow: (host: String) -> Boolean = { false },
		failing: (host: String) -> Boolean = { false },
	) = MockEngine(MockEngineConfig().apply {
		dispatcher = StandardTestDispatcher(scheduler)
		addHandler { request ->
			val host = request.url.host
			requests += host to request.url.parameters["q"]
			if (slow(host)) delay(10.seconds)
			if (failing(host)) {
				respond(
					content = "{}",
					status = HttpStatusCode.InternalServerError,
					headers = headersOf(HttpHeaders.ContentType, "application/json"),
				)
			} else {
				respond(bodyFor(request), HttpStatusCode.OK, jsonHeaders())
			}
		}
	})

	private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

	/**
	 * Serves the exact wire shapes each provider parses: lrclib answers a JSON array of
	 * objects with `syncedLyrics`, musixmatch needs its `token.get` and the
	 * `macro.subtitles.get` envelope. Distinct LRC per host so tests can tell who won.
	 */
	private fun bodyFor(request: HttpRequestData): String = when {
		request.url.encodedPath.endsWith("token.get") ->
			"""{"message":{"body":{"user_token":"testtoken"}}}"""

		request.url.encodedPath.endsWith("macro.subtitles.get") ->
			"""{"message":{"body":{"macro_calls":{"track.subtitles.get":{"message":{"body":""" +
				"""{"subtitle_list":[{"subtitle":{"subtitle_body":"[00:01.00]lyrics from musixmatch"}}]}""" +
				"""}}}}}}"""

		else -> """[{"syncedLyrics":"[00:01.00]lyrics from ${request.url.host}"}]"""
	}

	/**
	 * An in-memory Settings backend for the prefs under test. NOTE: the no-arg `Settings()`
	 * on JVM/desktop resolves to `java.util.Preferences.userRoot()` — a shared persistent
	 * store that leaks values across tests and builds. It is cleared per call and the
	 * LyricsConfig key is written explicitly.
	 */
	private fun settings(): Settings = Settings().also { it.clear() }

	/** A fake [LyricDao] holding one saved/cached [LyricEntity] per song. */
	private fun fakeLyricDao(vararg entities: LyricEntity) = object : LyricDao {
		val stored = entities.associateBy { it.songId }.toMutableMap()
		val inserts = mutableListOf<LyricEntity>()

		override suspend fun getLyrics(songId: String): LyricEntity? = stored[songId]
		override suspend fun insertLyrics(lyrics: LyricEntity) {
			inserts += lyrics
			stored[lyrics.songId] = lyrics
		}

		override suspend fun clearAllLyrics() {
			stored.clear()
		}
	}

	/** A repository on the fake server with the given provider config and offline state. */
	private fun repository(
		scheduler: TestCoroutineScheduler,
		engine: MockEngine,
		dao: LyricDao,
		config: LyricsConfig = LyricsConfig.Default,
		settings: Settings = settings(),
		offline: Boolean = false,
	): LyricsRepository {
		settings[LyricsConfig.KEY] = json.encodeToString(config)
		val prefs = PreferenceManager(settings)
		if (offline) prefs.offlineMode = OfflineMode.Forced
		val conn = ConnectivityManager(prefs)
		val session = SessionManager(
			settings, prefs, NetworkStatsManager(TestFakes.fakeNetworkStatsDao(), conn), engine
		)
		return LyricsRepository(dao, settings, session, conn, engine)
	}

	private fun lrclibOnly() = LyricsConfig(
		providers = listOf(LyricsProvider(LyricsProvider.Id.LRCLIB, true)),
		lyricsPlusMirrors = emptyList(),
		lrcLibBaseUrl = "https://lrclib.example/api/search",
		version = LyricsConfig.VERSION,
	)

	private fun lrclibThenMusixmatch() = LyricsConfig(
		providers = listOf(
			LyricsProvider(LyricsProvider.Id.LRCLIB, true),
			LyricsProvider(LyricsProvider.Id.MUSIXMATCH, true),
		),
		lyricsPlusMirrors = emptyList(),
		lrcLibBaseUrl = "https://lrclib.example/api/search",
		version = LyricsConfig.VERSION,
	)

	@Test
	fun offlineReturnsSavedFirstOrCacheOrNothingAndMakesNoRequests() = runTest {
		val requests = mutableListOf<Pair<String, String?>>()
		// every host answers 500: any accidental network call is recorded and skipped
		val engine = fakeServer(testScheduler, requests) { true }

		// empty cache: nothing to return, still no network
		val empty = repository(testScheduler, engine, fakeLyricDao(), offline = true)
		assertEquals(emptyList(), empty.fetchAllLyrics(song()))

		// saved lyrics win as the only result
		val savedDao = fakeLyricDao(
			LyricEntity(songId = SONG_ID, rawContent = "[00:01.00]saved line", providerName = LOCAL_PROVIDER)
		)
		val savedRepo = repository(testScheduler, engine, savedDao, offline = true)
		val savedResult = savedRepo.fetchAllLyrics(song())
		assertEquals(listOf("saved line"), savedResult.map { it.lines.first().text })
		assertEquals(LOCAL_PROVIDER, savedResult.single().providerName)

		// a cached provider copy is shown when there is no saved copy
		val cachedDao = fakeLyricDao(
			LyricEntity(songId = SONG_ID, rawContent = "[00:01.00]cached line", providerName = "LRCLIB")
		)
		val cachedRepo = repository(testScheduler, engine, cachedDao, offline = true)
		val cachedResult = cachedRepo.fetchAllLyrics(song())
		assertEquals(listOf("cached line"), cachedResult.map { it.lines.first().text })
		assertEquals("LRCLIB", cachedResult.single().providerName)

		assertEquals(0, requests.size, "offline makes zero network requests")
	}

	@Test
	fun providersRunInConfigOrder() = runTest {
		val requests = mutableListOf<Pair<String, String?>>()
		val engine = fakeServer(testScheduler, requests)
		val repo = repository(testScheduler, engine, fakeLyricDao(), lrclibThenMusixmatch())

		val results = repo.fetchAllLyrics(song())

		assertEquals(
			listOf("LRCLIB", "MUSIXMATCH"), results.map { it.providerName },
			"results come back in config order",
		)
		assertEquals(
			listOf("lyrics from lrclib.example", "lyrics from musixmatch"),
			results.map { it.lines.first().text },
		)
	}

	@Test
	fun lyricsShowAsEachProviderAnswersStillInPriorityOrder() = runTest {
		val requests = mutableListOf<Pair<String, String?>>()
		val engine = fakeServer(testScheduler, requests, slow = { it == "lrclib.example" })
		val dao = fakeLyricDao()
		val repo = repository(testScheduler, engine, dao, lrclibThenMusixmatch())

		val emissions = repo.lyricsFlow(song()).toList()

		assertEquals(
			listOf(listOf("MUSIXMATCH"), listOf("LRCLIB", "MUSIXMATCH")),
			emissions.map { results -> results.map { it.providerName } },
			"the faster provider shows first; the slower, higher-priority one joins ahead of it",
		)
		assertEquals(
			"LRCLIB", dao.inserts.single().providerName,
			"the cache waits for the higher-priority provider rather than taking the first answer",
		)
	}

	@Test
	fun savedLyricsComeFirstEvenWhenAProviderWouldMatch() = runTest {
		val requests = mutableListOf<Pair<String, String?>>()
		val engine = fakeServer(testScheduler, requests)
		val dao = fakeLyricDao(
			LyricEntity(songId = SONG_ID, rawContent = "[00:01.00]saved line", providerName = LOCAL_PROVIDER)
		)
		val repo = repository(testScheduler, engine, dao, lrclibThenMusixmatch())

		val results = repo.fetchAllLyrics(song())

		assertEquals(LOCAL_PROVIDER, results[0].providerName, "saved lyrics are pinned first")
		assertEquals(3, results.size, "saved + the two configured providers")
		assertTrue(requests.any { it.first == "lrclib.example" }, "providers still run for a saved song")
	}

	@Test
	fun failingProviderIsSkippedAndTheRestStillYieldsLyrics() = runTest {
		val requests = mutableListOf<Pair<String, String?>>()
		val engine = fakeServer(testScheduler, requests) { it == "lrclib.example" }
		val repo = repository(testScheduler, engine, fakeLyricDao(), lrclibThenMusixmatch())

		val results = repo.fetchAllLyrics(song())

		assertEquals(1, results.size, "the failing provider is skipped, the other still yields")
		assertEquals("MUSIXMATCH", results.single().providerName)
	}

	@Test
	fun firstNonSavedResultIsCachedOnceAndOnlyWithoutSavedCopy() = runTest {
		val requests = mutableListOf<Pair<String, String?>>()
		val engine = fakeServer(testScheduler, requests)
		val dao = fakeLyricDao()
		val repo = repository(testScheduler, engine, dao, lrclibThenMusixmatch())

		val results = repo.fetchAllLyrics(song())

		assertEquals(2, results.size)
		val cached = dao.inserts.single()
		assertEquals(SONG_ID, cached.songId)
		assertEquals("LRCLIB", cached.providerName, "only the FIRST provider's content is cached")
		assertEquals(
			"""{"syncedLyrics":"[00:01.00]lyrics from lrclib.example"}""",
			cached.rawContent,
			"cached raw is the first provider's body (lrclib matches the whole found object)",
		)

		// saved lyrics present: nothing is cached even when providers match
		val savedDao = fakeLyricDao(
			LyricEntity(songId = SONG_ID, rawContent = "[00:01.00]saved line", providerName = LOCAL_PROVIDER)
		)
		val savedRepo = repository(testScheduler, engine, savedDao, lrclibThenMusixmatch())
		savedRepo.fetchAllLyrics(song())
		assertEquals(0, savedDao.inserts.size, "no caching when a saved copy exists")
	}

	@Test
	fun fetchLyricsReturnsTheFirstResult() = runTest {
		val requests = mutableListOf<Pair<String, String?>>()
		val engine = fakeServer(testScheduler, requests)
		val config = lrclibOnly()

		// no saved copy: the first (and only) provider result
		val plain = repository(testScheduler, engine, fakeLyricDao(), config)
		assertEquals("LRCLIB", plain.fetchLyrics(song())?.providerName)

		// saved copy: fetchLyrics returns the saved one even though a provider matches
		val dao = fakeLyricDao(
			LyricEntity(songId = SONG_ID, rawContent = "[00:01.00]saved line", providerName = LOCAL_PROVIDER)
		)
		val saved = repository(testScheduler, engine, dao, config)
		assertEquals(LOCAL_PROVIDER, saved.fetchLyrics(song())?.providerName)
	}

	@Test
	fun lrcLibTakesTitleWithoutParentheticalsPlusArtist() = runTest {
		val requests = mutableListOf<Pair<String, String?>>()
		val engine = fakeServer(testScheduler, requests)
		val repo = repository(testScheduler, engine, fakeLyricDao(), lrclibOnly())

		repo.fetchLyrics(song("song (prod. x)"))

		val (host, query) = requests.single()
		assertEquals("lrclib.example", host)
		assertEquals("song Artist", query, "query is the parenthetical-stripped title + artist")
	}

	private companion object {
		const val SONG_ID = "song-1"
	}

	private fun song(title: String = "song") = DomainSong(
		id = SONG_ID,
		title = title,
		artistName = "Artist",
		artistId = "artist-1",
		albumTitle = "Album",
		albumId = "album-1",
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
		filePath = "/song.mp3",
		starredAt = null,
		coverArtId = null,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown,
		artists = emptyList(),
		albumArtists = emptyList(),
		isExternal = false
	)
}
