package eu.depau.loak.domain.repositories

import eu.depau.loak.util.IoDispatcher

import androidx.room3.concurrent.AtomicInt
import dev.zt64.subsonic.api.model.SubsonicException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_syncing
import eu.depau.loak.generated.resources.info_syncing_albums
import eu.depau.loak.generated.resources.info_syncing_artists
import eu.depau.loak.generated.resources.info_syncing_finished
import eu.depau.loak.generated.resources.info_syncing_genres
import eu.depau.loak.generated.resources.info_syncing_playlists
import eu.depau.loak.generated.resources.info_syncing_radios
import eu.depau.loak.generated.resources.info_syncing_saved
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.GenreDao
import eu.depau.loak.data.database.dao.LyricDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.RadioDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.dao.SyncActionDao
import eu.depau.loak.data.database.entities.AlbumEntity
import eu.depau.loak.data.database.entities.ArtistEntity
import eu.depau.loak.data.database.entities.PlaylistEntity
import eu.depau.loak.data.database.entities.PlaylistSongCrossRef
import eu.depau.loak.data.database.entities.SongEntity
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.data.database.mappers.toEntity
import eu.depau.loak.di.traced
import eu.depau.loak.domain.manager.NetworkStatsManager
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.util.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import dev.zt64.subsonic.api.model.Album as ApiAlbum
import dev.zt64.subsonic.api.model.AlbumListType as ApiAlbumListType
import dev.zt64.subsonic.api.model.Song as ApiSong

class DbRepository(
	private val albumDao: AlbumDao,
	private val playlistDao: PlaylistDao,
	private val songDao: SongDao,
	private val genreDao: GenreDao,
	private val artistDao: ArtistDao,
	private val radioDao: RadioDao,
	private val lyricDao: LyricDao,
	private val syncDao: SyncActionDao,
	private val sessionManager: SessionManager,
	private val networkStatsManager: NetworkStatsManager
) {
	private val concurrentRequestLimit = Semaphore(20)

	private val dbChunkSize = 500 // should be enough

	private val pageSize = 500 // the getAlbumList2 maximum

	private suspend fun <T> runDbOp(block: suspend () -> T): Result<T> =
		withContext(IoDispatcher) {
			try {
				Result.success(block())
			} catch (e: Exception) {
				if (e is CancellationException) throw e
				Result.failure(e)
			}
		}

	suspend fun removeEverything(): Result<Unit> = runDbOp {
		albumDao.clearAllAlbums()
		playlistDao.clearAllPlaylists()
		songDao.clearAllSongs()
		genreDao.clearAllGenres()
		artistDao.clearAllArtists()
		radioDao.clearAllRadios()
		lyricDao.clearAllLyrics()
		syncDao.clearAllActions()
		Logger.i("DbRepository", "Database wiped completely.")
	}

	suspend fun syncEverything(
		onProgress: (Float, StringResource) -> Unit = { _, _ -> }
	): Result<Unit> = traced("sync.library", "Full library pull") { data ->
		val start = Clock.System.now()
		pullLibrary(onProgress, data).also {
			networkStatsManager.recordSyncRun(start, it.isSuccess, data["songs"] as? Int ?: 0)
		}
	}

	private suspend fun pullLibrary(
		onProgress: (Float, StringResource) -> Unit,
		data: MutableMap<String, Any>
	): Result<Unit> = runDbOp {
		val progressCallback = suspend { progress: Float, message: StringResource ->
			Logger.i("DbRepository", "$progress ${getString(message)}")
			onProgress(progress, message)
		}

		progressCallback(0.0f, Res.string.info_syncing)

		progressCallback(0.01f, Res.string.info_syncing_genres)
		syncGenres().getOrThrow()

		progressCallback(0.02f, Res.string.info_syncing_radios)
		try {
			syncRadios().getOrThrow()
		} catch (ex: SubsonicException) {
			Logger.e(
				tag = "DbRepository",
				msg = "could not sync radio stations, maybe this server doesn't support it",
				tr = ex
			)
		}

		progressCallback(0.04f, Res.string.info_syncing_artists)
		syncArtists().getOrThrow()

		progressCallback(0.07f, Res.string.info_syncing_playlists)
		val known = playlistDao.getAllPlaylistEntities().associateBy { it.playlistId }
		// Only playlists that changed since we last fetched their songs. The song-link
		// count also catches rows that other callers of syncPlaylists() stored without songs.
		val playlists = syncPlaylists().getOrThrow().filter {
			known[it.playlistId]?.modifiedAt != it.modifiedAt
				|| playlistDao.getPlaylistSongCount(it.playlistId) != it.songCount
		}

		val validAlbumIds = mutableSetOf<String>()
		val validSongIds = mutableSetOf<String>()

		val libraryResult = syncLibrarySongs { localProgress, message ->
			val globalProgress = 0.10f + (localProgress * 0.65f)
			progressCallback(globalProgress, message)
		}.getOrThrow()

		validAlbumIds.addAll(libraryResult.first)
		validSongIds.addAll(libraryResult.second)
		data["albums"] = validAlbumIds.size
		data["songs"] = validSongIds.size
		data["playlists"] = playlists.size

		val totalPlaylists = playlists.size
		if (totalPlaylists > 0) {
			val completedPlaylists = AtomicInt(0)

			val playlistSongIdSets = coroutineScope {
				playlists.map { playlist ->
					async {
						concurrentRequestLimit.withPermit {
							val playlistSongIds =
								syncPlaylistSongs(playlist.playlistId).getOrThrow()

							val done = completedPlaylists.incrementAndGet()
							val globalProgress = 0.75f + (0.25f * (done.toFloat() / totalPlaylists))
							progressCallback(globalProgress, Res.string.info_syncing_playlists)
							playlistSongIds
						}
					}
				}.awaitAll()
			}
			for (set in playlistSongIdSets) {
				validSongIds.addAll(set)
			}
		}

		// Keep songs that are referenced in any playlist in the local database
		validSongIds.addAll(playlistDao.getAllPlaylistSongIds())
		albumDao.deleteObsoleteAlbums(validAlbumIds)
		songDao.deleteObsoleteSongs(validSongIds)

		progressCallback(1.0f, Res.string.info_syncing_finished)
	}

	/**
	 * Albums via getAlbumList2, songs in bulk via search3 with the `""` (match all) query.
	 * Servers that don't support that query fall back to one getAlbum per album.
	 */
	suspend fun syncLibrarySongs(
		onProgress: suspend (Float, StringResource) -> Unit = { _, _ -> }
	): Result<Pair<Set<String>, Set<String>>> = runDbOp {
		onProgress(0.0f, Res.string.info_syncing_albums)
		val summaries = mutableListOf<ApiAlbum>()
		while (true) {
			val batch = sessionManager.api.getAlbumsID3(
				ApiAlbumListType.AlphabeticalByName, pageSize, summaries.size
			)
			summaries.addAll(batch)
			if (batch.size < pageSize) break
		}
		if (summaries.isEmpty()) return@runDbOp emptySet<String>() to emptySet()

		val albums = summaries.map { it.toEntity() }
		albums.chunked(dbChunkSize).forEach { albumDao.insertAlbums(it) }
		val albumsById = albums.associateBy { it.albumId }
		val expectedSongs = summaries.sumOf { it.songCount }

		onProgress(0.1f, Res.string.info_syncing_albums)
		val songIds = mutableSetOf<String>()
		val seen = mutableSetOf<String>()
		try {
			while (true) {
				val page = sessionManager.api.searchID3(
					"\"\"", artistCount = 0, albumCount = 0,
					songCount = pageSize, songOffset = seen.size
				).songs
				// an empty page ends it; one with nothing new = a server ignoring the offset
				if (page.count { seen.add(it.id) } == 0) break
				val songs = page.toLibrarySongs(albumsById)
				// See the per-album path for why conflicts are ignored.
				songDao.insertSongsIgnoringConflicts(songs)
				songs.mapTo(songIds) { it.songId }
				val fetched = seen.size.toFloat() / expectedSongs.coerceAtLeast(1)
				onProgress(0.1f + 0.8f * fetched.coerceAtMost(1f), Res.string.info_syncing_albums)
			}
		} catch (e: SubsonicException) {
			Logger.w("DbRepository", "search3 failed; fetching albums one by one", e)
		}

		// ponytail: 90% heuristic for "this server doesn't support the match-all query"
		// (it then returns nothing or a capped page); album song counts can lag a bit.
		if (songIds.size * 10 < expectedSongs * 9) {
			Logger.w(
				"DbRepository",
				"search3 returned ${songIds.size}/$expectedSongs songs; fetching albums one by one"
			)
			return@runDbOp syncAlbumsOneByOne(summaries, onProgress)
		}

		Logger.i("DbRepository", "- Songs Synced: ${albums.size} albums, ${songIds.size} songs")
		onProgress(1.0f, Res.string.info_syncing_saved)
		albumsById.keys to songIds
	}

	private suspend fun syncAlbumsOneByOne(
		allAlbumSummaries: List<ApiAlbum>,
		onProgress: suspend (Float, StringResource) -> Unit
	): Pair<Set<String>, Set<String>> {
		val totalAlbums = allAlbumSummaries.size
		val completedAlbums = AtomicInt(0)
		var finalSongsSynced = 0

		val allValidAlbumIds = mutableSetOf<String>()
		val allValidSongIds = mutableSetOf<String>()

		onProgress(0.1f, Res.string.info_syncing_albums)

		val albumChannel = Channel<ApiAlbum>(capacity = 100)

		coroutineScope {
			launch(IoDispatcher) {
				allAlbumSummaries.map { summary ->
					launch {
						concurrentRequestLimit.withPermit {
							try {
								val album = sessionManager.api.getAlbum(summary.id)

								val done = completedAlbums.incrementAndGet()
								val fetchProgress = 0.1f + (0.8f * (done.toFloat() / totalAlbums))
								onProgress(fetchProgress, Res.string.info_syncing_albums)

								albumChannel.send(album)
							} catch (e: Exception) {
								if (e is SerializationException) {
									Logger.e(
										"DbRepository",
										"could not deserialize album ${summary.id} (${summary.name}); skipping it",
										e
									)
								} else if (e.message != null && e.message!!.contains("DATA_NOT_FOUND")) {
									Logger.e(
										"DbRepository",
										"album with id ${summary.id} (${summary.name}) not found; skipping it",
										e
									)
								} else {
									Logger.e(
										"DbRepository",
										"could not fetch album ${summary.id} (${summary.name}); skipping it",
										e
									)
								}
							}
						}
					}
				}.joinAll()
				albumChannel.close()
			}

			launch(IoDispatcher) {
				val albumBatch = mutableListOf<AlbumEntity>()
				val songBatch = mutableListOf<SongEntity>()
				val summariesMap = allAlbumSummaries.associateBy { it.id }

				for (album in albumChannel) {
					val summary = summariesMap[album.id]
					val albumEntity = album.toEntity(
						artistIdOverride = summary?.artistId,
						artistNameOverride = summary?.artistName
					)
					albumBatch.add(albumEntity)
					allValidAlbumIds.add(albumEntity.albumId)

					album.songs.forEach { song ->
						val songEntity = song.toEntity(
							artistIdOverride = albumEntity.artistId.takeIf { song.artistId.isNullOrBlank() },
							artistNameOverride = albumEntity.artistName.takeIf { song.artistName.isNullOrBlank() }
						)
						songBatch.add(songEntity)
						allValidSongIds.add(songEntity.songId)
					}

					if (albumBatch.size >= dbChunkSize || songBatch.size >= 1500) {
						albumDao.insertAlbums(albumBatch)
						// Full-library rebuild: songs are fetched for every album,
						// so on a repeat sync the same rows already exist (they can
						// even appear under several albums). The web Room driver turns
						// @Upsert into a bare INSERT, which then trips the PK on
						// every existing row and floods the worker; IGNORE keeps the
						// insert idempotent and rows that vanish are pruned by
						// deleteObsoleteSongs below.
						songDao.insertSongsIgnoringConflicts(songBatch)

						finalSongsSynced += songBatch.size
						albumBatch.clear()
						songBatch.clear()
					}
				}

				if (albumBatch.isNotEmpty() || songBatch.isNotEmpty()) {
					if (albumBatch.isNotEmpty()) albumDao.insertAlbums(albumBatch)
					if (songBatch.isNotEmpty()) songDao.insertSongsIgnoringConflicts(songBatch)
					finalSongsSynced += songBatch.size
				}
			}
		}

		Logger.i(
			"DbRepository",
			"- Songs Synced: $totalAlbums albums, $finalSongsSynced songs"
		)

		onProgress(1.0f, Res.string.info_syncing_saved)
		return allValidAlbumIds to allValidSongIds
	}

	suspend fun syncPlaylists(): Result<List<PlaylistEntity>> = runDbOp {
		val remotePlaylists = sessionManager.api.getPlaylists()
		val playlistEntities = remotePlaylists.map { it.toEntity() }
		val validPlaylistIds = playlistEntities.map { it.playlistId }.toSet()

		playlistEntities.chunked(dbChunkSize).forEach { chunk ->
			playlistDao.insertPlaylists(chunk)
		}

		playlistDao.deleteObsoletePlaylists(validPlaylistIds)

		Logger.i("DbRepository", "- Playlists Synced: ${playlistEntities.size} playlists found")

		playlistEntities
	}

	suspend fun syncPlaylistSongs(playlistId: String): Result<Set<String>> = runDbOp {
		val playlist = try {
			sessionManager.api.getPlaylist(playlistId)
		} catch (e: Exception) {
			if (e is SerializationException) {
				Logger.e(
					"DbRepository",
					"could not deserialize playlist $playlistId; skipping it",
					e
				)
				return@runDbOp emptySet<String>()
			} else {
				throw e
			}
		}
		playlistDao.insertPlaylist(playlist.toEntity())
		val songEntities = playlist.songs.map { it.toEntity() }
		val songIds = songEntities.map { it.songId }.toSet()
		if (songEntities.isNotEmpty()) {
			// Songs already exist from the album sync phase (most playlist
			// songs belong to synced albums). A plain insert re-fires the PK;
			// on the web driver @Upsert degrades to a bare INSERT (no
			// ON CONFLICT clause) and floods the worker with constraint
			// errors — IGNORE matches upsert semantics here since song rows
			// are refreshed by the album phase and only the cross-refs below
			// carry the playlist membership.
			songEntities.chunked(dbChunkSize).forEach { chunk ->
				songDao.insertSongsIgnoringConflicts(chunk)
			}

			val crossRefs = songEntities.mapIndexed { index, it ->
				PlaylistSongCrossRef(playlistId = playlistId, songId = it.songId, position = index)
			}

			playlistDao.replacePlaylistSongs(playlistId, crossRefs)
		} else {
			playlistDao.deletePlaylistSongCrossRefs(playlistId)
		}

		Logger.i("DbRepository", "- Playlist [$playlistId] synced: ${songEntities.size} songs")
		songIds
	}

	suspend fun syncGenres(): Result<Unit> = runDbOp {
		val remoteGenres = sessionManager.api.getGenres()
		val entities = remoteGenres.map { it.toEntity() }

		entities.chunked(dbChunkSize).forEach { chunk ->
			genreDao.insertGenres(chunk)
		}
		genreDao.deleteObsoleteGenres(entities.map { it.genreName }.toSet())

		Logger.i("DbRepository", "- Genres Synced: ${entities.size} genres found")
	}

	suspend fun syncArtists(): Result<Unit> = runDbOp {
		//val pageSize = 500
		//var offset = 0
		val artists =	 mutableListOf<ArtistEntity>()

		// reverting this for now
		// see the original upstream issue about grouping arbitrary artists

		//try {
		//	while (true) {
		//		val batch = sessionManager.api.searchID3(
		//			query = "", artistCount = pageSize, artistOffset = offset
		//		).artists.map { it.toEntity() }
		//		if (batch.isEmpty()) break
		//		artists.addAll(batch)
		//		if (batch.size < pageSize) break
		//		offset += pageSize
		//	}
		//	// because some servers like to not give an error...?
		//	require(artists.isNotEmpty())
		//} catch (ex: Exception) {
		//	Logger.w(
		//		"DbRepository",
		//		"could not sync artists from search3 endpoint, trying getArtists",
		//		ex
		//	)
		//	artists.clear()

		val batch = sessionManager.api.getArtists().index
			.flatMap { index -> index.artists.map { artist -> artist.toEntity() } }
		artists.addAll(batch)
		//}

		artists.chunked(dbChunkSize).forEach { chunk ->
			artistDao.insertArtists(chunk)
		}
		artistDao.deleteObsoleteArtists(artists.map { it.artistId }.toSet())

		Logger.i("DbRepository", "- Artists Synced: ${artists.size} artists found")
	}

	suspend fun syncRadios(): Result<Unit> = runDbOp {
		val remoteRadios = sessionManager.api.getInternetRadioStations()
		val entities = remoteRadios.map { it.toEntity() }

		entities.chunked(dbChunkSize).forEach { chunk ->
			radioDao.insertRadios(chunk)
		}
		radioDao.deleteObsoleteRadios(entities.map { it.radioId }.toSet())

		Logger.i("DbRepository", "- Radios Synced: ${entities.size} stations found")
	}

	suspend fun fetchArtistMetadata(artistId: String): Result<DomainArtist> = runDbOp {
		val artistInfo = sessionManager.api.getArtistInfo(artistId)
		val simIds = artistInfo.similarArtists.map { it.id }

		val currentEntity = artistDao.getArtistById(artistId)
			?: throw Exception("Artist not found in local DB")

		val updatedEntity = currentEntity.copy(
			biography = artistInfo.biography,
			similarArtistIds = simIds,
			lastFmUrl = artistInfo.lastFmUrl
		)

		artistDao.insertArtist(updatedEntity)

		updatedEntity.toDomainModel()
	}
}

/**
 * Library songs of [albumsById]'s albums (songs without a known album are dropped: nothing
 * would list them); a song without an artist takes its album's, like the per-album path.
 */
internal fun List<ApiSong>.toLibrarySongs(albumsById: Map<String, AlbumEntity>): List<SongEntity> =
	mapNotNull { song ->
		val album = albumsById[song.albumId] ?: return@mapNotNull null
		song.toEntity(
			artistIdOverride = album.artistId.takeIf { song.artistId.isNullOrBlank() },
			artistNameOverride = album.artistName.takeIf { song.artistName.isNullOrBlank() }
		)
	}
