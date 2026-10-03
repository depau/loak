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
import eu.depau.loak.domain.manager.PlayLogManager
import eu.depau.loak.domain.manager.AudioMuseManager
import eu.depau.loak.domain.manager.QueueSyncManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.pow
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
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
		val key = genre ?: ""
		val now = Clock.System.now()
		snapshots[key]?.takeIf { !rebuild && now.epochSeconds - it.builtAt < PICKS_TTL.inWholeSeconds }
			?.ids?.mapNotNull { library.songsById[it] }
			?.takeIf { it.size >= PICKS_SIZE / 2 }
			?.let { return@withContext it }

		val picks = buildQuickPicks(library, genre)
		snapshots[key] = Snapshot(now.epochSeconds, picks.map { it.id })
		settings[PICKS_KEY] = json.encodeToString(snapshots.toMap())
		picks
	}

	private suspend fun buildQuickPicks(library: HomeLibrary, genre: String?): List<DomainSong> {
		val now = Clock.System.now()
		val recentPlays = playLog.songPlays(PlayLogManager.KEEP)
		fun eligible(song: DomainSong) = !song.isDisliked() &&
			(recentPlays[song.id] ?: 0) <= HEAVY_ROTATION &&
			song.inGenre(genre)

		val songs = library.songs.filter(::eligible)
		val recentAlbums = library.albums
			.filter { (it.lastPlayedAt ?: return@filter false) >= now - 30.days }
			.mapTo(HashSet()) { it.id }
		val recent = songs.filter { (it.albumId in recentAlbums && it.playCount > 0) || it.id in recentPlays }
		val recentIds = recent.mapTo(HashSet()) { it.id }
		// "a lot": the top fifth of what has been played, at least 3 plays
		val played = songs.map { it.playCount }.filter { it > 0 }.sorted()
		val highPlays = maxOf(3, played.getOrElse(played.size * 4 / 5) { 3 })
		val sure = songs.filter {
			it.id !in recentIds &&
				(it.starredAt != null || (it.userRating ?: 0) >= 4 || it.playCount >= highPlays)
		}

		val picks = LinkedHashMap<String, DomainSong>()
		fun add(list: List<DomainSong>, count: Int) {
			list.filter { it.id !in picks }.weightedSample(count) { 1.0 + it.playCount + 2 * (recentPlays[it.id] ?: 0) }
				.forEach { picks[it.id] = it }
		}
		add(recent, 10)
		add(sure, 6)

		// discoveries: songs like two of the picks, only by artists already played
		val playedArtists = songs.filter { it.playCount > 0 }.mapTo(HashSet()) { it.artistId }
		val discoveries = picks.values.take(2).flatMap { seed ->
			try {
				songRepository.getSimilarSongs(seed.id, count = 20)
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Logger.w(TAG, "could not fetch similar songs for quick picks", e)
				emptyList()
			}
		}.mapNotNull { library.songsById[it.id] }
			.filter { eligible(it) && it.artistId in playedArtists }
			.distinctBy { it.id }
		add(discoveries, 4)

		// short on history: anything played, then anything at all
		add(recent + sure, PICKS_SIZE - picks.size)
		add(songs.filter { it.playCount > 0 }, PICKS_SIZE - picks.size)
		add(songs, PICKS_SIZE - picks.size)

		return picks.values.shuffled().spreadAlbums()
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

	private fun DomainSong.isDisliked() = (userRating ?: 0) in 1..2

	private fun DomainSong.genreNames() = (genres + listOfNotNull(genre)).distinct()

	private fun DomainSong.inGenre(genre: String?) =
		genre == null || genreNames().any { it.equals(genre, ignoreCase = true) }

	private fun DomainAlbum.inGenre(genre: String?) = genre == null ||
		(genres + listOfNotNull(this.genre)).any { it.equals(genre, ignoreCase = true) }

	/** [count] items picked at random, likelier the heavier (Efraimidis–Spirakis). */
	private fun <T> List<T>.weightedSample(count: Int, weight: (T) -> Double): List<T> {
		if (count <= 0) return emptyList()
		return sortedByDescending { Random.nextDouble().pow(1.0 / weight(it)) }.take(count)
	}

	/** Moves songs so that no two from the same album play back to back, where it can. */
	private fun List<DomainSong>.spreadAlbums(): List<DomainSong> {
		val songs = toMutableList()
		for (i in 1 until songs.size) {
			if (songs[i].albumId != songs[i - 1].albumId) continue
			val j = (i + 1 until songs.size).firstOrNull { songs[it].albumId != songs[i - 1].albumId }
				?: continue
			songs.add(i, songs.removeAt(j))
		}
		return songs
	}

	companion object {
		private const val TAG = "HomeRepository"
		private const val PINS_KEY = "speedDialPins"
		private const val PICKS_KEY = "quickPicks"
		const val SPEED_DIAL_SIZE = 26 // 3 pages of 9, less the dice
		const val PICKS_SIZE = 20
		private const val SHELF_SIZE = 20
		/** Songs played more than this in the last 5 days are left out of Quick picks. */
		private const val HEAVY_ROTATION = 20
		private val PICKS_TTL = 30.minutes

		fun keyOf(collection: DomainSongCollection) = when (collection) {
			is DomainPlaylist -> "playlist:${collection.id}"
			is DomainAlbum -> "album:${collection.id}"
			else -> null
		}

		fun keyOf(song: DomainSong) = "song:${song.id}"
	}
}
