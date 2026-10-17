package eu.depau.loak.domain.repositories

import eu.depau.loak.domain.models.PlaylistKind
import eu.depau.loak.domain.models.parsePlaylistName
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.history.ListeningHistory
import eu.depau.loak.domain.history.QUICK_PICKS_SIZE
import eu.depau.loak.domain.history.RecapData
import eu.depau.loak.domain.history.RecapPeriod
import eu.depau.loak.domain.history.buildRecap
import eu.depau.loak.domain.history.fetch
import eu.depau.loak.domain.history.recapSpans
import eu.depau.loak.domain.history.pickQuickPicks
import eu.depau.loak.domain.manager.PreferenceManager
import eu.depau.loak.domain.manager.PlayLogManager
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.genreNames
import eu.depau.loak.domain.models.inGenre
import eu.depau.loak.domain.models.isDisliked
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.minutes

/** A Speed dial tile: a playlist or album opens, a song plays its radio. */
sealed interface SpeedDialItem {
	val key: String
	val pinned: Boolean

	data class Playlist(val playlist: DomainPlaylist, override val pinned: Boolean) : SpeedDialItem {
		override val key get() = "playlist:${playlist.id}"
	}

	data class Album(val album: DomainAlbum, override val pinned: Boolean) : SpeedDialItem {
		override val key get() = "album:${album.id}"
	}

	data class Song(val song: DomainSong, override val pinned: Boolean) : SpeedDialItem {
		override val key get() = "song:${song.id}"
	}
}

/** Someone else playing a song on the server right now. */
data class ServerListener(val song: DomainSong, val username: String, val minutesAgo: Int)

/** The library as Home reads it, loaded once per refresh. */
class HomeLibrary(val songs: List<DomainSong>, val albums: List<DomainAlbum>) {
	val songsById = songs.associateBy { it.id }
}

/**
 * Builds Home's shelves from the cached library, this device's play log and a few server
 * calls. Every shelf takes an optional genre: Home filtered by a genre chip, or a genre's page.
 */
class HomeRepository(
	private val songDao: SongDao,
	private val albumDao: AlbumDao,
	private val artistDao: ArtistDao,
	private val playlistDao: PlaylistDao,
	private val dbRepository: DbRepository,
	private val songRepository: SongRepository,
	private val sessionManager: SessionManager,
	private val queueSyncManager: QueueSyncManager,
	private val playLog: PlayLogManager,
	private val listeningHistory: ListeningHistory,
	private val preferenceManager: PreferenceManager,
	private val settings: Settings,
	private val audioMuse: AudioMuseManager
) {
	private val json = Json { ignoreUnknownKeys = true }

	// ponytail: the whole library in memory per refresh, like the library's sorting does
	suspend fun library(): HomeLibrary = withContext(IoDispatcher) {
		HomeLibrary(
			songs = songDao.getAllSongs().map { it.toDomainModel() },
			albums = albumDao.getAllAlbumsList().map { it.toDomainModel() }
		)
	}

	/** The genres listened to most: song play counts summed per genre. */
	fun topGenres(library: HomeLibrary, count: Int = 10): List<String> = library.songs
		.flatMap { song -> song.genreNames().map { it to song.playCount } }
		.groupBy({ it.first }, { it.second })
		.mapValues { it.value.sum() }
		.filterValues { it > 0 }
		.entries.sortedByDescending { it.value }
		.take(count).map { it.key }

	// region recap

	/**
	 * What was listened to over [period]: plays, hours, artists, the activity grid and the top
	 * three lists, plus the same figures for the period before it to compare against.
	 *
	 * Every source is asked once, for one span covering both halves. Only the songs that were
	 * actually played are read back out of the library, so a Recap of a quiet day does not pull
	 * the whole catalogue for it.
	 */
	suspend fun recap(period: RecapPeriod): RecapData = withContext(IoDispatcher) {
		val zone = TimeZone.currentSystemDefault()
		val now = Clock.System.now()
		val oldest = playLog.oldestPlay() ?: now
		val spans = recapSpans(period, now, zone, oldest)
		val fetch = spans.fetch
		val read = listeningHistory.reads(fetch.start, fetch.end)
		val songs = if (read.plays.isEmpty()) emptyMap() else {
			songDao.getSongsByIds(read.plays.keys.toList()).associate { it.songId to it.toDomainModel() }
		}
		val recap = buildRecap(read.plays, songs, spans, zone, listeningHistory.id, read.unreadable)
		// a played song carries its album's art, not its artist's: the Artist cards read it off
		// the artist, like every other artist card in the app
		val art = if (recap.topArtists.isEmpty()) emptyMap() else {
			artistDao.getArtistsByIds(recap.topArtists.map { it.id })
				.associate { it.artistId to it.coverArtId }
		}
		recap.copy(
			topArtists = recap.topArtists.map { artist ->
				// the artist's own image when the server has one, else what was played
				artist.copy(coverArtId = art[artist.id]?.takeIf(String::isNotBlank) ?: artist.coverArtId)
			}
		)
	}

	// endregion

	// region speed dial

	/** Ids of the pinned tiles, as [SpeedDialItem.key]s, in pin order. */
	val pins: StateFlow<List<String>>
		field = MutableStateFlow(
			settings.getStringOrNull(PINS_KEY)
				?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }
				.orEmpty()
		)

	fun isPinned(key: String) = key in pins.value

	fun setPinned(key: String, pinned: Boolean) {
		val new = if (pinned) (pins.value - key) + key else pins.value - key
		pins.value = new
		settings[PINS_KEY] = json.encodeToString(new)
	}

	/**
	 * Up to [SPEED_DIAL_SIZE] tiles: pinned first, then the most started playlists, the most
	 * played albums and songs, taking turns.
	 */
	suspend fun speedDial(library: HomeLibrary): List<SpeedDialItem> = withContext(IoDispatcher) {
		val albumsById = library.albums.associateBy { it.id }
		val pinned = pins.value.mapNotNull { key -> resolve(key, library, albumsById) }

		val playlistStarts = playLog.playlistStarts()
		val playlists = playlistStarts.entries.sortedByDescending { it.value }
			.mapNotNull { playlistDao.getPlaylistById(it.key)?.toDomainModel() }
			.map { SpeedDialItem.Playlist(it, false) }
		val albumStarts = playLog.albumStarts()
		val albums = library.albums
			.map { it to it.playCount + 3 * (albumStarts[it.id] ?: 0) }
			.filter { it.second > 0 }
			.sortedByDescending { it.second }
			.map { SpeedDialItem.Album(it.first, false) }
		val songs = library.songs
			.filter { it.playCount > 0 && !it.isDisliked() }
			.sortedByDescending { it.playCount }
			.take(SPEED_DIAL_SIZE)
			.map { SpeedDialItem.Song(it, false) }

		val taken = pinned.mapTo(HashSet()) { it.key }
		val items = pinned.toMutableList()
		val sources = listOf(playlists.iterator(), albums.iterator(), songs.iterator())
		while (items.size < SPEED_DIAL_SIZE && sources.any { it.hasNext() }) {
			for (source in sources) {
				while (source.hasNext()) {
					val item = source.next()
					if (taken.add(item.key)) {
						items += item
						break
					}
				}
				if (items.size == SPEED_DIAL_SIZE) break
			}
		}
		// a new account has played nothing yet
		if (items.isEmpty()) library.albums.sortedByDescending { it.createdAt }
			.take(SPEED_DIAL_SIZE).mapTo(items) { SpeedDialItem.Album(it, false) }
		items
	}

	private suspend fun resolve(
		key: String,
		library: HomeLibrary,
		albumsById: Map<String, DomainAlbum>
	): SpeedDialItem? {
		val (type, id) = key.split(":", limit = 2).takeIf { it.size == 2 } ?: return null
		return when (type) {
			"playlist" -> playlistDao.getPlaylistById(id)?.toDomainModel()
				?.let { SpeedDialItem.Playlist(it, true) }
			"album" -> albumsById[id]?.let { SpeedDialItem.Album(it, true) }
			"song" -> library.songsById[id]?.let { SpeedDialItem.Song(it, true) }
			else -> null
		}
	}

	// endregion

	// region quick picks

	@Serializable
	private data class Snapshot(val builtAt: Long, val ids: List<String>)

	private val snapshots: MutableMap<String, Snapshot> = settings.getStringOrNull(PICKS_KEY)
		?.let { runCatching { json.decodeFromString<Map<String, Snapshot>>(it) }.getOrNull() }
		.orEmpty().toMutableMap()

	/**
	 * 20 songs you'll like: recent listening, sure likes and a few discoveries. The same list
	 * comes back for [PICKS_TTL], so what's on screen is what Play all plays; [rebuild] or an
	 * older list makes a new one.
	 */
	suspend fun quickPicks(
		library: HomeLibrary,
		genre: String?,
		rebuild: Boolean
	): List<DomainSong> = withContext(IoDispatcher) {
		// the knobs are part of the key: switching source or decay rebuilds instead of
		// showing half an hour of picks ranked by the way it was
		val key = "${preferenceManager.listeningHistorySource}|${preferenceManager.quickPicksDecay}" +
			"|${genre ?: ""}"
		val now = Clock.System.now()
		snapshots[key]?.takeIf { !rebuild && now.epochSeconds - it.builtAt < PICKS_TTL.inWholeSeconds }
			?.ids?.mapNotNull { library.songsById[it] }
			?.takeIf { it.size >= QUICK_PICKS_SIZE / 2 }
			?.let { return@withContext it }

		val picks = buildQuickPicks(library, genre)
		// drop what has expired rather than keeping one stale list per genre forever
		snapshots.entries.removeAll { now.epochSeconds - it.value.builtAt >= PICKS_TTL.inWholeSeconds }
		snapshots[key] = Snapshot(now.epochSeconds, picks.map { it.id })
		settings[PICKS_KEY] = json.encodeToString(snapshots.toMap())
		picks
	}

	private suspend fun buildQuickPicks(library: HomeLibrary, genre: String?): List<DomainSong> {
		val heat = listeningHistory.heat(HEAT_WINDOW, preferenceManager.quickPicksDecay.halfLife)
		val recentAlbumIds = library.albums
			.filter { (it.lastPlayedAt ?: return@filter false) >= Clock.System.now() - 30.days }
			.mapTo(HashSet()) { it.id }

		return pickQuickPicks(library.songs, heat, recentAlbumIds, genre) { seeds ->
			similarSongsFor(library, seeds)
		}
	}

	/**
	 * Songs like the first picks, off the server, as the "curve balls" in Quick picks. The
	 * seeds are asked for at once — this is the one part of Home that waits on the server.
	 * A failure is not worth an empty list's worth of noise: it just means no curve balls.
	 */
	private suspend fun similarSongsFor(library: HomeLibrary, seeds: List<DomainSong>): List<DomainSong> =
		coroutineScope {
			seeds.map { seed ->
				async {
					try {
						songRepository.getSimilarSongs(seed.id, count = 20)
					} catch (e: Exception) {
						if (e is CancellationException) throw e
						Logger.w(TAG, "could not fetch similar songs for quick picks", e)
						emptyList()
					}
				}
			}.awaitAll()
				.flatten()
				.mapNotNull { library.songsById[it.id] }
				.distinctBy { it.id }
		}

	// endregion

	/** The artists played most, for one Instant mix each. */
	suspend fun topArtists(library: HomeLibrary, genre: String?, count: Int = 10): List<DomainArtist> =
		withContext(IoDispatcher) {
			val ids = library.songs.filter { it.inGenre(genre) && it.playCount > 0 }
				.groupBy { it.artistId }
				.mapValues { (_, songs) -> songs.sumOf { it.playCount } }
				.entries.sortedByDescending { it.value }
				.take(count).map { it.key }
			val artists = artistDao.getArtistsByIds(ids).associateBy { it.artistId }
			ids.mapNotNull { artists[it]?.toDomainModel() }
		}

	/** AudioMuse-AI's playlists: the scheduled ones (Sonic Fingerprint, …) first, then the clusters. */
	suspend fun madeForYou(): List<DomainPlaylist> = withContext(IoDispatcher) {
		playlistDao.getAllPlaylistsByName().map { it.toDomainModel() }
			.map { it to parsePlaylistName(it.name, it.validUntil != null, audioMuse = true) }
			.filter { (_, name) -> name.kind.isRebuilt }
			.sortedBy { (_, name) -> name.kind != PlaylistKind.AudioMuseScheduled }
			.map { it.first }
	}

	/** AudioMuse-AI's radios, as their playlists on the server. Empty without a connection. */
	suspend fun radios(): List<DomainPlaylist> = withContext(IoDispatcher) {
		val names = audioMuse.refreshRadios().filter { it.enabled }.mapTo(HashSet()) { it.name }
		if (names.isEmpty()) return@withContext emptyList()
		playlistDao.getAllPlaylistsByName().map { it.toDomainModel() }.filter { it.name in names }
	}

	/** One of the top artists, changing daily, with the similar artists in the library. */
	suspend fun similarTo(topArtists: List<DomainArtist>): Pair<DomainArtist, List<DomainArtist>>? =
		withContext(IoDispatcher) {
			if (topArtists.isEmpty()) return@withContext null
			val day = (Clock.System.now().epochSeconds / 86_400).toInt()
			val seed = topArtists[day % minOf(5, topArtists.size)]
			val similarIds = seed.similarArtistIds.ifEmpty {
				dbRepository.fetchArtistMetadata(seed.id).getOrNull()?.similarArtistIds.orEmpty()
			}
			val similar = artistDao.getArtistsByIds(similarIds).map { it.toDomainModel() }
				.sortedBy { similarIds.indexOf(it.id) }
			if (similar.isEmpty()) null else seed to similar
		}

	fun recentlyAdded(library: HomeLibrary, genre: String?): List<DomainAlbum> = library.albums
		.filter { it.inGenre(genre) }
		.sortedByDescending { it.createdAt }
		.take(SHELF_SIZE)

	/** Albums played a lot, or starred, that haven't been played in months. */
	fun forgottenFavourites(library: HomeLibrary, genre: String?): List<DomainAlbum> {
		val cutoff = Clock.System.now() - 90.days
		return library.albums
			.filter { album ->
				val last = album.lastPlayedAt ?: return@filter false
				last < cutoff && album.inGenre(genre) && (album.starredAt != null || album.playCount >= 3)
			}
			.sortedByDescending { it.playCount }
			.take(SHELF_SIZE)
	}

	/** Albums nobody has played yet, a different handful each day. */
	fun neverPlayed(library: HomeLibrary): List<DomainAlbum> {
		val day = (Clock.System.now().epochSeconds / 86_400).toInt()
		return library.albums
			.filter { it.playCount == 0 && it.lastPlayedAt == null }
			.shuffled(kotlin.random.Random(day))
			.take(SHELF_SIZE)
	}

	/** Songs barely played, by the artists played most. */
	fun deepCuts(library: HomeLibrary, count: Int = 20): List<DomainSong> {
		val top = library.songs.groupBy { it.artistId }
			.mapValues { (_, songs) -> songs.sumOf { it.playCount } }
			.filterValues { it > 0 }
			.entries.sortedByDescending { it.value }.take(10).map { it.key }.toSet()
		val day = (Clock.System.now().epochSeconds / 86_400).toInt()
		return library.songs
			.filter { it.artistId in top && it.playCount <= 3 && !it.isDisliked() }
			.shuffled(kotlin.random.Random(day))
			.take(count)
	}

	/**
	 * What other users are playing.
	 *
	 * ponytail: a raw call, because subsonic-client 1.0.0-SNAPSHOT drops the songs from
	 * getNowPlaying's entries. Use api.getNowPlaying once it keeps them.
	 */
	suspend fun nowPlaying(library: HomeLibrary): List<ServerListener> = withContext(IoDispatcher) {
		val body = sessionManager.api.httpClient.get("getNowPlaying.view").bodyAsText()
		val entries = json.parseToJsonElement(body).jsonObject["subsonic-response"]
			?.jsonObject?.get("nowPlaying")?.jsonObject?.get("entry")?.jsonArray.orEmpty()
		entries.mapNotNull { element ->
			val entry = element.jsonObject
			val username = entry["username"]?.jsonPrimitive?.content ?: return@mapNotNull null
			if (username.equals(sessionManager.username, ignoreCase = true)) return@mapNotNull null
			val song = library.songsById[entry["id"]?.jsonPrimitive?.content] ?: return@mapNotNull null
			ServerListener(song, username, entry["minutesAgo"]?.jsonPrimitive?.int ?: 0)
		}
	}

	/**
	 * A path from the song played last to a starred one, changing daily. Only on servers that
	 * can find sonic paths.
	 */
	suspend fun sonicJourney(library: HomeLibrary, current: DomainSong?): Pair<DomainSong, DomainSong>? {
		if ("sonicSimilarity" !in queueSyncManager.extensions()) return null
		val from = current ?: playLog.lastSong()?.first?.let { library.songsById[it] } ?: return null
		val starred = library.songs.filter { it.starredAt != null && it.id != from.id }
		if (starred.isEmpty()) return null
		val day = (Clock.System.now().epochSeconds / 86_400).toInt()
		return from to starred[Random(day).nextInt(starred.size)]
	}

	companion object {
		private const val TAG = "HomeRepository"
		private const val PINS_KEY = "speedDialPins"
		private const val PICKS_KEY = "quickPicks"
		const val SPEED_DIAL_SIZE = 26 // 3 pages of 9, less the dice
		private const val SHELF_SIZE = 20
		/**
		 * Heat is summed over this window; each play is worth half as much every
		 * `quickPicksDecay` half-life, so recent heavy rotation leads Quick picks and then
		 * fades. The window is what a remote source can be asked about in one page, so it
		 * stays fixed while the decay is the knob.
		 */
		private val HEAT_WINDOW = 30.days
		private val PICKS_TTL = 30.minutes

		fun keyOf(collection: DomainSongCollection) = when (collection) {
			is DomainPlaylist -> "playlist:${collection.id}"
			is DomainAlbum -> "album:${collection.id}"
			else -> null
		}

		fun keyOf(song: DomainSong) = "song:${song.id}"
	}
}
