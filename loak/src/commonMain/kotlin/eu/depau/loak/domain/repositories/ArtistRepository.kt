package eu.depau.loak.domain.repositories

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.DownloadDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.data.database.entities.SyncActionType
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.data.database.mappers.toEntity
import eu.depau.loak.domain.manager.SyncManager
import eu.depau.loak.domain.models.DomainArtist
import eu.depau.loak.domain.models.DomainArtistListType
import eu.depau.loak.domain.models.DomainFilter
import eu.depau.loak.ui.core.UiState
import kotlin.time.Clock

class ArtistRepository(
	private val artistDao: ArtistDao,
	private val songDao: SongDao,
	private val downloadDao: DownloadDao,
	private val syncManager: SyncManager,
	private val dbRepository: DbRepository
) {
	private suspend fun getLocalData(
		listType: DomainArtistListType,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainArtist> {
		val artists = when (listType) {
			DomainArtistListType.AlphabeticalByName -> artistDao.getArtistsAlphabeticalByName()
			DomainArtistListType.Random -> artistDao.getArtistsRandom()
		}.map { it.toDomainModel() }

		return artists.filter { artist ->
			filters.all { filter ->
				when (filter) {
					DomainFilter.Starred -> artist.starredAt != null
					DomainFilter.Downloaded -> songDao
						.getSongsByArtistId(artist.id)
						.takeIf { it.isNotEmpty() }
						?.all {
							val download = downloadDao.getDownloadById(it.songId)
							return@all download?.status == DownloadStatus.DOWNLOADED
						} ?: false
				}
			}
		}.toImmutableList()
	}

	private suspend fun refreshLocalData(
		listType: DomainArtistListType,
		filters: Set<DomainFilter> = emptySet()
	): ImmutableList<DomainArtist> {
		dbRepository.syncArtists().getOrThrow()
		return getLocalData(listType, filters)
	}

	fun getArtistsFlow(
		fullRefresh: Boolean,
		listType: DomainArtistListType,
		filters: Set<DomainFilter> = emptySet()
	): Flow<UiState<ImmutableList<DomainArtist>>> = flow {
		val localData = getLocalData(listType, filters)
		if (fullRefresh) {
			emit(UiState.Loading(data = localData))
			try {
				emit(UiState.Success(data = refreshLocalData(listType, filters)))
			} catch (error: Exception) {
				emit(UiState.Error(error = error, data = localData))
			}
		} else {
			emit(UiState.Success(data = localData))
		}
	}.flowOn(Dispatchers.IO)

	suspend fun isArtistStarred(artist: DomainArtist) = artistDao.isArtistStarred(artist.id)

	suspend fun starArtist(artist: DomainArtist) {
		val starredEntity = artist.toEntity().copy(
			starredAt = Clock.System.now()
		)
		artistDao.insertArtist(starredEntity)
		syncManager.enqueueAction(SyncActionType.STAR, artist.id)
	}

	suspend fun unstarArtist(artist: DomainArtist) {
		val unstarredEntity = artist.toEntity().copy(
			starredAt = null
		)
		artistDao.insertArtist(unstarredEntity)
		syncManager.enqueueAction(SyncActionType.UNSTAR, artist.id)
	}
}
