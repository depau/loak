package eu.depau.loak.domain.repositories

import eu.depau.loak.util.IoDispatcher

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.DownloadDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.data.database.entities.SyncActionType
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.data.database.mappers.toEntity
import eu.depau.loak.domain.manager.SessionManager
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongListType
import eu.depau.loak.ui.core.UiState
import eu.depau.loak.util.sortedByListType
import kotlin.time.Clock

class SongRepository(
	private val songDao: SongDao,
	private val albumDao: AlbumDao,
	private val downloadDao: DownloadDao,
	private val dbRepository: DbRepository,
	private val syncManager: SyncManager,
	private val sessionManager: SessionManager
) {
	suspend fun getAllSongs(): List<DomainSong> {
		return songDao.getAllSongs().map { it.toDomainModel() }
	}

	suspend fun getRandomSongs(count: Int): List<DomainSong> {
		return songDao.getRandomSongs(count).map { it.toDomainModel() }
	}

	/** The artist's most popular songs (Last.fm-based on Navidrome), in order. */
	suspend fun getTopSongs(artistName: String, count: Int = 50): List<DomainSong> {
		return sessionManager.api.getTopSongs(artistName, count).take(count)
			.map { it.toEntity().toDomainModel() }
	}

	/** Songs the server finds similar to a song, album or artist. */
	suspend fun getSimilarSongs(id: String, count: Int = 50): List<DomainSong> {
		// OpenSubsonic: getSimilarSongs takes all three id types, no need for getSimilarSongs2
		// take(): subsonic-client leaves count out of the request, so the server sends its default
		return sessionManager.api.getSimilarSongs(id, count).take(count)
			.map { it.toEntity().toDomainModel() }
	}

	/**
	 * Songs that ease from [fromId] into [toId] (the OpenSubsonic sonicSimilarity extension).
	 *
	 * ponytail: a raw call, because subsonic-client 1.0.0-SNAPSHOT sends stopSongId where the
	 * spec says endSongId. Use api.findSonicPath once it's fixed.
	 */
	suspend fun getSonicPath(fromId: String, toId: String, count: Int = 20): List<DomainSong> {
		val body = sessionManager.api.httpClient.get("findSonicPath.view") {
			parameter("startSongId", fromId)
			parameter("endSongId", toId)
			parameter("count", count)
		}.bodyAsText()
		val response = Json.parseToJsonElement(body).jsonObject["subsonic-response"]?.jsonObject
		val status = response?.get("status")?.jsonPrimitive?.content
		if (status != "ok") error("findSonicPath failed: ${response?.get("error") ?: body}")

		val ids = response["sonicMatch"]?.jsonArray
			?.mapNotNull { it.jsonObject["entry"]?.jsonObject?.get("id")?.jsonPrimitive?.content }
			.orEmpty()
		// ponytail: songs the library sync hasn't seen yet are dropped
		val known = songDao.getSongsByIds(ids).associateBy { it.songId }
		return ids.mapNotNull { known[it]?.toDomainModel() }
	}

	private suspend fun getLocalData(
		listType: DomainSongListType,
		reversed: Boolean,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainSong> {
		val songs = songDao
			.getAllSongs()
			.map { it.toDomainModel() }

		val downloadedIds = if (filters.contains(DomainFilter.Downloaded)) {
			downloadDao.getAllDownloadsList()
				.filter { it.status == DownloadStatus.DOWNLOADED }
				.map { it.songId }
				.toSet()
		} else emptySet()

		val filteredByFilters = songs.filter { song ->
			filters.all { filter ->
				when (filter) {
					DomainFilter.Starred -> song.starredAt != null
					DomainFilter.Downloaded -> downloadedIds.contains(song.id)
				}
			}
		}

		val sorted = filteredByFilters
			.toImmutableList()
			.sortedByListType(
				listType,
				albums = albumDao.getAllAlbumsList().map { it.toDomainModel() }
			)

		return if (reversed) {
			sorted.reversed().toImmutableList()
		} else {
			sorted
		}
	}

	private suspend fun refreshLocalData(
		listType: DomainSongListType,
		reversed: Boolean,
		filters: Set<DomainFilter>
	): ImmutableList<DomainSong> {
		dbRepository.syncLibrarySongs().getOrThrow()
		return getLocalData(listType, reversed, filters)
	}

	fun getSongsFlow(
		fullRefresh: Boolean,
		listType: DomainSongListType,
		reversed: Boolean,
		filters: Set<DomainFilter>
	): Flow<UiState<ImmutableList<DomainSong>>> = flow {
		val localData = getLocalData(listType, reversed, filters)
		if (fullRefresh) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData(listType, reversed, filters)))
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(IoDispatcher)

	suspend fun isSongStarred(song: DomainSong) = songDao.isSongStarred(song.id)
	suspend fun getSongRating(song: DomainSong) = songDao.getSongRating(song.id) ?: 0
	fun observeSongStarred(songId: String) = songDao.observeSongStarred(songId)
	suspend fun starSong(song: DomainSong) = setSongStarred(song.id, true)
	suspend fun unstarSong(song: DomainSong) = setSongStarred(song.id, false)

	suspend fun setSongStarred(songId: String, starred: Boolean) {
		if (starred) {
			songDao.updateSongStarredAt(songId, Clock.System.now())
			syncManager.enqueueAction(SyncActionType.STAR, songId)
		} else {
			songDao.updateSongStarredAt(songId, null)
			syncManager.enqueueAction(SyncActionType.UNSTAR, songId)
		}
	}

	suspend fun rateSong(song: DomainSong, rating: Int) {
		songDao.updateSongRating(song.id, rating)
		when (rating) {
			0 -> syncManager.enqueueAction(SyncActionType.STAR_0, song.id)
			1 -> syncManager.enqueueAction(SyncActionType.STAR_1, song.id)
			2 -> syncManager.enqueueAction(SyncActionType.STAR_2, song.id)
			3 -> syncManager.enqueueAction(SyncActionType.STAR_3, song.id)
			4 -> syncManager.enqueueAction(SyncActionType.STAR_4, song.id)
			5 -> syncManager.enqueueAction(SyncActionType.STAR_5, song.id)
		}
	}
}
