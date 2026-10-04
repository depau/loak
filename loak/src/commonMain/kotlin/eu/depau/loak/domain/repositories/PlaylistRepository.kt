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
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainPlaylistListType
import eu.depau.loak.ui.core.UiState

class PlaylistRepository(
	private val playlistDao: PlaylistDao,
	private val dbRepository: DbRepository,
	private val audioStore: AudioStore
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
}
