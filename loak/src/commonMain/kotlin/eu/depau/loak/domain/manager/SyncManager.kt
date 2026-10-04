package eu.depau.loak.domain.manager

import eu.depau.loak.util.IoDispatcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
import eu.depau.loak.di.traced
import eu.depau.loak.domain.repositories.DbRepository
import eu.depau.loak.util.Logger
import dev.zt64.subsonic.api.model.ScanStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration
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

	private val pullLock = Mutex()
	private var pull: Deferred<Result<Unit>>? = null

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

		syncJob = scope.launch {
			while (isActive) {
				// A session must exist before we can sync — without one the API
				// client has an empty baseUrl and every request 404s against the
				// page origin (visible on web boot before the user logs in).
				sessionManager.isLoggedIn.first { it }
				// respects OfflineMode; resumes as soon as we're back online
				connectivityManager.isOnline.first { it }
				runSyncCycle()
				delay(CHECK_INTERVAL)
			}
		}
	}

	/** User-initiated: pulls even on metered networks. */
	fun triggerManualSync() {
		scope.launch { pullLibrary() }
	}

	/**
	 * Full library pull. Concurrent callers (periodic check, manual sync, pull-to-refresh)
	 * join the run in progress instead of starting another one.
	 */
	suspend fun pullLibrary(): Result<Unit> {
		if (!connectivityManager.isOnline.value) {
			return Result.failure(IllegalStateException("offline"))
		}
		return pullLock.withLock {
			pull?.takeIf { it.isActive } ?: scope.async { runPull() }.also { pull = it }
		}.await()
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

	private suspend fun runSyncCycle() = traced("sync.cycle", "Sync cycle") { data ->
		syncMutex.withLock { processQueue() }

		val sinceLastPull = Clock.System.now() -
			Instant.fromEpochMilliseconds(preferenceManager.lastFullSyncTime)
		val scan = scanStatus()
		val shouldPull = shouldPullLibrary(
			// Web can fall back to an in-memory database (no OPFS, or another tab
			// holds it), so after a reload the albums are gone while
			// lastFullSyncTime (persisted) is still recent.
			libraryEmpty = albumDao.getAlbumCount() == 0,
			metered = connectivityManager.isCellular.value,
			sinceLastPull = sinceLastPull,
			scanCount = scan?.count,
			scanning = scan?.scanning == true,
			lastScanCount = preferenceManager.lastScanCount
		)
		data["full_pull"] = shouldPull
		if (shouldPull) pullLibrary()
	}

	private suspend fun runPull(): Result<Unit> = syncMutex.withLock {
		// Without a session the SubsonicClient has no auth params and the
		// server answers REQUIRED_PARAMETER_MISSING ('u').
		if (!sessionManager.isLoggedIn.value) return@withLock Result.success(Unit)

		processQueue()

		Logger.i("SyncManager", "Starting full library pull...")
		val startedAt = Clock.System.now()
		val scan = scanStatus()
		syncState.update { it.copy(isSyncing = true) }

		val result = repository.syncEverything { progress, message ->
			syncState.update {
				it.copy(isSyncing = true, progress = progress, message = message)
			}
		}

		// TODO: error should show up in UI as snack bar or something
		result.onSuccess {
			preferenceManager.lastFullSyncTime = startedAt.toEpochMilliseconds()
			preferenceManager.lastScanCount = scan?.count ?: -1
			Logger.i("SyncManager", "Full library sync complete.")
		}.onFailure {
			Logger.e("SyncManager", "couldn't sync library", it)
		}

		syncState.update {
			it.copy(isSyncing = false, message = Res.string.info_status_idle)
		}
		result
	}

	/** null when the server doesn't support getScanStatus (or it failed). */
	private suspend fun scanStatus(): ScanStatus? = try {
		sessionManager.api.getScanStatus()
	} catch (e: Exception) {
		if (e is CancellationException) throw e
		Logger.w("SyncManager", "getScanStatus failed; falling back to time-based sync", e)
		null
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

/** How often the periodic loop flushes the action queue and checks the server for changes. */
private val CHECK_INTERVAL = 1.hours

/** Pull at least this often even when the scan count didn't change (stars, edits, plays). */
private val MAX_PULL_AGE = 24.hours

/** Pull interval for servers without getScanStatus, where we can't detect changes. */
private val BLIND_PULL_AGE = 6.hours

/**
 * Whether the periodic check should run a full library pull.
 * Never on metered networks (except the first sync), nor while the server is scanning.
 * ponytail: change detection is the scan's song count only (the API model has no lastScan),
 * so tag edits wait for [MAX_PULL_AGE].
 */
internal fun shouldPullLibrary(
	libraryEmpty: Boolean,
	metered: Boolean,
	sinceLastPull: Duration,
	scanCount: Int?, // null: getScanStatus unsupported
	scanning: Boolean,
	lastScanCount: Int
): Boolean = when {
	libraryEmpty -> true
	metered || scanning -> false
	scanCount == null -> sinceLastPull > BLIND_PULL_AGE
	else -> scanCount != lastScanCount || sinceLastPull > MAX_PULL_AGE
}
