package eu.depau.loak.domain.repositories

import eu.depau.loak.domain.manager.AudioStore
import eu.depau.loak.util.IoDispatcher

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.dao.SyncActionDao
import eu.depau.loak.data.database.entities.LOCAL_PLAYLIST_PREFIX
import eu.depau.loak.data.database.entities.PlaylistEdit
import eu.depau.loak.data.database.entities.PlaylistEntity
import eu.depau.loak.data.database.entities.PlaylistSongCrossRef
import eu.depau.loak.data.database.entities.SyncActionEntity
import eu.depau.loak.data.database.entities.SyncActionType
import eu.depau.loak.data.database.mappers.toEntity
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainPlaylistListType
import eu.depau.loak.ui.core.UiState
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class PlaylistRepository(
	private val playlistDao: PlaylistDao,
	private val dbRepository: DbRepository,
	private val audioStore: AudioStore,
	private val songDao: SongDao,
	private val syncDao: SyncActionDao,
	private val syncManager: SyncManager,
	private val sessionManager: SessionManager
) {
	private suspend fun getLocalData(
		listType: DomainPlaylistListType,
		reversed: Boolean,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainPlaylist> {
		val playlists = when (listType) {
			DomainPlaylistListType.Name -> playlistDao.getAllPlaylistsByName()
			DomainPlaylistListType.DateAdded -> playlistDao.getAllPlaylistsByDateAdded()
			DomainPlaylistListType.Duration -> playlistDao.getAllPlaylistsByDuration()
			DomainPlaylistListType.Random -> playlistDao.getAllPlaylistsRandom()
		}

		val downloadedIds = if (filters.contains(DomainFilter.Downloaded)) {
			audioStore.downloadedIds()
		} else null

		val filtered = playlists.filter { (_, songs) ->
			filters.all { filter ->
				when (filter) {
					DomainFilter.Starred -> false // not applicable
					DomainFilter.Downloaded -> downloadedIds != null && downloadedIds.containsAll(songs.map { it.song.songId })
				}
			}
		}.map { it.toDomainModel() }

		val sorted = if (reversed) {
			filtered.reversed().toImmutableList()
		} else {
			filtered.toImmutableList()
		}
		return sorted
	}

	private suspend fun refreshLocalData(
		listType: DomainPlaylistListType,
		reversed: Boolean,
		filters: Set<DomainFilter> = emptySet(),
		withSongs: Boolean = true
	): ImmutableList<DomainPlaylist> {
		val playlists = dbRepository.syncPlaylists().getOrThrow()
		// one request per playlist: only on explicit refreshes; a playlist's own page updates its songs
		if (withSongs) playlists.forEach { playlist ->
			dbRepository.syncPlaylistSongs(playlist.playlistId).getOrThrow()
		}
		return getLocalData(listType, reversed, filters)
	}

	/** Emits whenever the cached playlists change (renames, deletes, syncs). */
	fun playlistChanges() = playlistDao.getAllPlaylistsFlow().drop(1)

	fun getPlaylistsFlow(
		fullRefresh: Boolean,
		listType: DomainPlaylistListType,
		reversed: Boolean,
		filters: Set<DomainFilter> = emptySet(),
		withSongs: Boolean = true
	): Flow<UiState<ImmutableList<DomainPlaylist>>> = flow {
		val localData = getLocalData(listType, reversed, filters)
		if (fullRefresh) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData(listType, reversed, filters, withSongs)))
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(IoDispatcher)

	/*
	 * Playlist edits: applied to the local cache right away, then queued and replayed on the
	 * server by SyncManager (at once when online). One path, online or not.
	 */

	/** Creates the playlist under a temporary id until the server gives it one; returns the id. */
	@OptIn(ExperimentalUuidApi::class)
	suspend fun create(name: String, songs: List<DomainSong>): String {
		val id = LOCAL_PLAYLIST_PREFIX + Uuid.random()
		val now = Clock.System.now()
		playlistDao.insertPlaylist(
			PlaylistEntity(
				playlistId = id, name = name, comment = null, owner = sessionManager.username,
				coverArtId = null, songCount = 0, duration = Duration.ZERO, public = false,
				readOnly = false, createdAt = now, modifiedAt = now, validUntil = null,
				allowedUsers = emptyList()
			)
		)
		setSongs(id, songs.map { it.id }, songs)
		enqueue(SyncActionType.CREATE_PLAYLIST, id, PlaylistEdit(songs.map { it.id }, name = name))
		return id
	}

	/** Appends the songs not in the playlist yet; false if there were none. */
	suspend fun addSongs(playlistId: String, songs: List<DomainSong>): Boolean {
		val id = syncManager.playlistId(playlistId)
		val current = playlistDao.getPlaylistSongIds(id)
		val toAdd = songs.filterNot { it.id in current }.distinctBy { it.id }
		if (toAdd.isEmpty()) return false
		setSongs(id, current + toAdd.map { it.id }, toAdd)
		enqueue(SyncActionType.ADD_TO_PLAYLIST, id, PlaylistEdit(toAdd.map { it.id }))
		return true
	}

	/**
	 * Removes the song at [index]. The removal is held for an Undo: returns the action id for
	 * [undoRemove] or SyncManager.release.
	 */
	suspend fun removeSong(playlistId: String, index: Int): Int? {
		val id = syncManager.playlistId(playlistId)
		val current = playlistDao.getPlaylistSongIds(id)
		val songId = current.getOrNull(index) ?: return null
		setSongs(id, current.filterIndexed { i, _ -> i != index })
		val occurrence = current.take(index).count { it == songId }
		return syncManager.enqueueHeld(
			SyncActionType.REMOVE_FROM_PLAYLIST, id,
			payload = Json.encodeToString(PlaylistEdit(listOf(songId), occurrence))
		)
	}

	suspend fun undoRemove(actionId: Int, playlistId: String, index: Int, songId: String) {
		syncManager.cancel(actionId)
		val id = syncManager.playlistId(playlistId)
		val current = playlistDao.getPlaylistSongIds(id).toMutableList()
		current.add(index.coerceAtMost(current.size), songId)
		setSongs(id, current)
	}

	/** Name, description and visibility; a newer edit replaces a queued one. */
	suspend fun update(playlistId: String, name: String, comment: String, public: Boolean) {
		val id = syncManager.playlistId(playlistId)
		val playlist = playlistDao.getPlaylistEntity(id) ?: return
		playlistDao.insertPlaylist(playlist.copy(name = name, comment = comment, public = public))
		syncDao.removePlaylistUpdates(id)
		val edit = PlaylistEdit(name = name, comment = comment, public = public)
		enqueue(SyncActionType.UPDATE_PLAYLIST, id, edit)
	}

	private suspend fun enqueue(type: SyncActionType, playlistId: String, edit: PlaylistEdit) =
		syncManager.enqueue(
			SyncActionEntity(
				actionType = type, itemId = playlistId, payload = Json.encodeToString(edit)
			)
		)

	/** Replaces the playlist's songs in the cache; [newSongs] are stored first, links need them. */
	private suspend fun setSongs(
		playlistId: String,
		songIds: List<String>,
		newSongs: List<DomainSong> = emptyList()
	) {
		songDao.insertSongsIgnoringConflicts(newSongs.map { it.toEntity() })
		playlistDao.replacePlaylistSongs(
			playlistId,
			songIds.mapIndexed { i, songId -> PlaylistSongCrossRef(playlistId, songId, i) }
		)
		val playlist = playlistDao.getPlaylistById(playlistId) ?: return
		playlistDao.insertPlaylist(
			playlist.playlist.copy(
				songCount = songIds.size,
				duration = playlist.songs.fold(Duration.ZERO) { sum, it -> sum + it.song.duration }
			)
		)
	}
}
