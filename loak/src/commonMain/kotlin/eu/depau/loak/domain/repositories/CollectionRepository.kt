package eu.depau.loak.domain.repositories

import eu.depau.loak.util.IoDispatcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.data.database.mappers.toEntity
import eu.depau.loak.domain.manager.SessionManager
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainPlaylist
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.ui.core.UiState
import dev.zt64.subsonic.api.model.AlbumInfo as ApiAlbumInfo

class CollectionRepository(
	private val albumDao: AlbumDao,
	private val playlistDao: PlaylistDao,
	private val songDao: SongDao,
	private val dbRepository: DbRepository,
	private val sessionManager: SessionManager
) {
	suspend fun getLocalData(collectionId: String): DomainSongCollection {
		return albumDao.getAlbumById(collectionId)?.toDomainModel()
			?: playlistDao.getPlaylistById(collectionId)?.toDomainModel()
			?: throw Error("Collection ID $collectionId is neither a known album or playlist")
	}

	private suspend fun refreshLocalData(collectionId: String): DomainSongCollection {
		when (val collection = getLocalData(collectionId)) {
			is DomainAlbum -> {
				val album = sessionManager.api.getAlbum(collection.id)
				songDao.updateSongsByAlbumId(album.id, album.songs.map { it.toEntity() })
				albumDao.insertAlbum(album.toEntity())
				albumDao.getAlbumById(album.id)!!.toDomainModel()
			}

			is DomainPlaylist -> {
				dbRepository.syncPlaylistSongs(collection.id).getOrThrow()
				playlistDao.getPlaylistById(collection.id)!!.toDomainModel()
			}
		}
		return getLocalData(collectionId)
	}

	fun getCollectionFlow(
		fullRefresh: Boolean,
		collectionId: String
	): Flow<UiState<DomainSongCollection>> = flow {
		val localData = runCatching { getLocalData(collectionId) }.getOrNull()
		if (localData != null) {
			if (fullRefresh) {
				emit(UiState.Loading(data = localData))
				try {
					emit(UiState.Success(data = refreshLocalData(collectionId)))
				} catch (error: Exception) {
					emit(UiState.Error(error = error, data = localData))
				}
			} else {
				emit(UiState.Success(data = localData))
			}
		} else {
			emit(UiState.Loading())
			try {
				emit(UiState.Success(data = refreshRemoteCollection(collectionId)))
			} catch (error: Exception) {
				emit(UiState.Error(error = error))
			}
		}
	}.flowOn(IoDispatcher)

	private suspend fun refreshRemoteCollection(collectionId: String): DomainSongCollection {
		return try {
			val album = sessionManager.api.getAlbum(collectionId)
			songDao.updateSongsByAlbumId(album.id, album.songs.map { it.toEntity() })
			albumDao.insertAlbum(album.toEntity())
			albumDao.getAlbumById(album.id)!!.toDomainModel()
		} catch (_: Exception) {
			dbRepository.syncPlaylistSongs(collectionId).getOrThrow()
			playlistDao.getPlaylistById(collectionId)!!.toDomainModel()
		}
	}

	fun getOtherAlbums(artistId: String, albumId: String) = albumDao
		.getAlbumsByArtistExcluding(artistId, albumId)
		.map { it.map { album -> album.toDomainModel() } }

	suspend fun getSongById(songId: String) = songDao
		.getSongById(songId)
		?.toDomainModel()

	suspend fun getAlbumInfo(albumId: String): ApiAlbumInfo {
		return sessionManager.api.getAlbumInfo(albumId)
	}
}
