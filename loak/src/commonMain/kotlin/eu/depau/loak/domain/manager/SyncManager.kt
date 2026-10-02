package eu.depau.loak.domain.manager

import eu.depau.loak.util.IoDispatcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.info_status_idle
import org.jetbrains.compose.resources.StringResource
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.SyncActionDao
import eu.depau.loak.data.database.entities.SyncActionEntity
import eu.depau.loak.data.database.entities.SyncActionType
import eu.depau.loak.domain.repositories.DbRepository
import eu.depau.loak.util.Logger
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

data class SyncState(
	val isSyncing: Boolean = false,
	val progress: Float = 0f,
	val message: StringResource = Res.string.info_status_idle
)

class SyncManager(
	private val repository: DbRepository,
	private val syncDao: SyncActionDao,
	private val albumDao: AlbumDao,
	private val connectivityManager: ConnectivityManager,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager
) {
	private val scope = CoroutineScope(SupervisorJob() + IoDispatcher)
	private var syncJob: Job? = null
	private val syncMutex = Mutex()

	private val fullSyncThreshold = 1.hours

	val syncState: StateFlow<SyncState>
		field = MutableStateFlow(SyncState())

	init {
		scope.launch {
			connectivityManager.isOnline.collect { isOnline ->
				if (!syncMutex.isLocked && isOnline) {
					syncMutex.withLock { processQueue() }
				}
			}
		}
	}

	fun startPeriodicSync() {
		Logger.i("SyncManager", "Starting periodic sync cycle.")
		if (syncJob?.isActive == true) return

		scope.launch {
			// A session must exist before we can sync — without one the API
			// client has an empty baseUrl and every request 404s against the
			// page origin (visible on web boot before the user logs in).
			if (!sessionManager.isLoggedIn.value) return@launch

			if (albumDao.getAlbumCount() == 0
				|| preferenceManager.lastFullSyncTime <= 0L
			) {
				Logger.i("SyncManager", "Syncing now because we haven't synced before")
				// Web can fall back to an in-memory database (no OPFS, or another
				// tab holds it), so after a reload the albums are gone while
				// lastFullSyncTime (persisted) is still recent — without force the
				// recency gate below would skip the full pull and leave an empty
				// library.
				runSyncCycle(force = true)
			}
		}

		syncJob = scope.launch {
			while (isActive) {
				runSyncCycle()
				delay(15.minutes)
			}
		}
	}

	fun triggerManualSync() {
		scope.launch {
			preferenceManager.lastFullSyncTime = 0
			runSyncCycle()
		}
	}

	fun stopPeriodicSync() {
		syncJob?.cancel()
		syncState.value = SyncState(isSyncing = false)
	}

	fun enqueueAction(actionType: SyncActionType, itemId: String, time: Instant = Clock.System.now()) {
		scope.launch {
			syncDao.enqueue(SyncActionEntity(actionType = actionType, itemId = itemId, time = time))
			if (!syncMutex.isLocked) {
				syncMutex.withLock { processQueue() }
			}
		}
	}

	/**
	 * Queues a change that must wait for an Undo: it is held (not sent) until [release],
	 * or until [hold] has passed, so a change survives the app being killed meanwhile.
	 * [cancel] drops it. Returns the id for those two.
	 */
	suspend fun enqueueHeld(actionType: SyncActionType, itemId: String, hold: Duration = 30.seconds): Int =
		syncDao.enqueue(
			SyncActionEntity(actionType = actionType, itemId = itemId, time = Clock.System.now() + hold)
		).toInt()

	fun release(id: Int) {
		scope.launch {
			syncDao.setTime(id, Clock.System.now())
			if (!syncMutex.isLocked) syncMutex.withLock { processQueue() }
		}
	}

	suspend fun cancel(id: Int) = syncDao.removeAction(id)

	private suspend fun runSyncCycle(force: Boolean = false) {
		syncMutex.withLock {
			// Without a session the SubsonicClient has no auth params and the
			// server answers REQUIRED_PARAMETER_MISSING ('u'); the periodic
			// loop can reach here at boot, before the user logs in.
			if (!sessionManager.isLoggedIn.value) return

			processQueue()

			val currentTime = Clock.System.now()
			if (force
				|| currentTime - Instant.fromEpochMilliseconds(preferenceManager.lastFullSyncTime) > fullSyncThreshold
			) {
				Logger.i("SyncManager", "Starting full library pull...")

				syncState.update {
					it.copy(isSyncing = true)
				}

				val result = repository.syncEverything { progress, message ->
					syncState.update {
						it.copy(isSyncing = true, progress = progress, message = message)
					}
				}

				preferenceManager.lastFullSyncTime = currentTime.toEpochMilliseconds()

				// TODO: error should show up in UI as snack bar or something
				try {
					result.getOrThrow()
					Logger.i("SyncManager", "Full library sync complete.")
				} catch (ex: Exception) {
					Logger.e("SyncManager", "couldn't sync library", ex)
				}

				syncState.update {
					it.copy(isSyncing = false, message = Res.string.info_status_idle)
				}
			}
		}
	}

	private suspend fun processQueue() {
		val actions = syncDao.getPendingActions()
		if (actions.isEmpty()) return

		val now = Clock.System.now()
		for (action in actions) {
			// held for an Undo (see enqueueHeld); scrobbles always carry a past time
			if (action.time > now) continue
			try {
				when (action.actionType) {
					SyncActionType.STAR -> sessionManager.api.star(action.itemId)
					SyncActionType.UNSTAR -> sessionManager.api.unstar(action.itemId)
					SyncActionType.DELETE_PLAYLIST -> sessionManager.api.deletePlaylist(action.itemId)
					SyncActionType.REMOVE_FROM_PLAYLIST -> {
						val (playlistId, index) = action.itemId.split(':').let { it[0] to it[1].toInt() }
						sessionManager.api.updatePlaylist(playlistId, songIndicesToRemove = listOf(index))
					}
					SyncActionType.SCROBBLE -> sessionManager.api.scrobble(
						action.itemId,
						submission = true,
						time = action.time // i'm sorry for you if this defaults to "now"
					)

					SyncActionType.STAR_0 -> sessionManager.api.setRating(action.itemId, 0)
					SyncActionType.STAR_1 -> sessionManager.api.setRating(action.itemId, 1)
					SyncActionType.STAR_2 -> sessionManager.api.setRating(action.itemId, 2)
					SyncActionType.STAR_3 -> sessionManager.api.setRating(action.itemId, 3)
					SyncActionType.STAR_4 -> sessionManager.api.setRating(action.itemId, 4)
					SyncActionType.STAR_5 -> sessionManager.api.setRating(action.itemId, 5)
				}

				syncDao.removeAction(action.id)
				Logger.i(
					"SyncManager",
					"Successfully synced ${action.actionType} for ${action.itemId}"
				)

			} catch (e: Exception) {
				Logger.e("SyncManager", "Network failed. Action left in queue.", e)
				break
			}
		}
	}
}
