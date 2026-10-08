package eu.depau.loak.domain.manager

import eu.depau.loak.di.COVER_ART_FULL
import eu.depau.loak.di.COVER_ART_MEDIUM
import eu.depau.loak.di.COVER_ART_SMALL
import eu.depau.loak.di.CoverArtId
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.DownloadCollectionDao
import eu.depau.loak.data.database.dao.DownloadDao
import eu.depau.loak.data.database.dao.LyricDao
import eu.depau.loak.data.database.dao.ManualDownloadDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.entities.AudioFileEntity
import eu.depau.loak.data.database.entities.DownloadCollectionEntity
import eu.depau.loak.data.database.entities.DownloadCollectionType
import eu.depau.loak.data.database.entities.DownloadEntity
import eu.depau.loak.data.database.entities.DownloadStatus
import eu.depau.loak.data.database.entities.LyricEntity
import eu.depau.loak.data.database.entities.ManualDownloadEntity
import eu.depau.loak.data.database.mappers.toDomainModel
import eu.depau.loak.data.database.mappers.toEntity
import eu.depau.loak.di.PlatformType
import eu.depau.loak.domain.models.AudioQuality
import eu.depau.loak.domain.models.CronSchedule
import eu.depau.loak.domain.models.DomainAlbum
import eu.depau.loak.domain.models.DomainSong
import eu.depau.loak.domain.models.DomainSongCollection
import eu.depau.loak.domain.models.nextRun
import eu.depau.loak.domain.repositories.DbRepository
import eu.depau.loak.domain.repositories.LyricsRepository
import eu.depau.loak.generated.resources.Res
import eu.depau.loak.generated.resources.notice_download_waiting_wifi
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.seconds
import coil3.PlatformContext as CoilPlatformContext

/**
 * Downloads are pinned [AudioStore] entries. Asking for one pins the song's best stored copy and,
 * if that's below the download quality, leaves a pinned empty entry: the queue, which survives
 * restarts. Queued entries are fetched a few at a time while online, and only on an unmetered
 * network unless [overCellular]; failures retry with backoff. Progress lives in memory.
 * On Android a WorkManager job keeps the process around (and restarts it) while any are queued.
 */
class DownloadManager(
	private val coilPlatformContext: CoilPlatformContext,
	private val imageLoader: ImageLoader,
	private val legacyDao: DownloadDao,
	private val collectionDao: DownloadCollectionDao,
	private val manualDao: ManualDownloadDao,
	private val dbRepository: DbRepository,
	private val albumDao: AlbumDao,
	private val songDao: SongDao,
	private val playlistDao: PlaylistDao,
	private val lyricsRepository: LyricsRepository,
	private val lyricDao: LyricDao,
	private val sessionManager: SessionManager,
	private val preferenceManager: PreferenceManager,
	private val connectivityManager: ConnectivityManager,
	private val snackBarManager: SnackBarManager,
	private val store: AudioStore,
	private val fetcher: AudioFetcher,
	private val platformType: PlatformType
) {
	private val scope = CoroutineScope(IoDispatcher + SupervisorJob())

	// launching and cancelling downloads, so a cancelled one isn't relaunched from stale state
	private val queueMutex = Mutex()
	private val jobs = MutableStateFlow(emptyMap<String, Job>())
	private val slots = Semaphore(CONCURRENCY)
	private val progress = MutableStateFlow(emptyMap<String, Float>())

	/** Songs whose download gave up after [MAX_ATTEMPTS], until [retryFailed]. */
	private val failed = MutableStateFlow(emptySet<String>())

	private val pinned: StateFlow<List<AudioFileEntity>> =
		store.pinned.stateIn(scope, SharingStarted.Eagerly, emptyList())

	/** The current server's downloaded collections: albums/artists/playlists pinned, any status. */
	val collections: StateFlow<List<DownloadCollectionEntity>> =
		collectionDao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

	/** Songs the user pinned individually (not via a whole collection), which orphan-unpin keeps. */
	val manualDownloadedIds: StateFlow<Set<String>> =
		manualDao.observeIds().map { it.toSet() }.stateIn(scope, SharingStarted.Eagerly, emptySet())

	/** Downloaded songs pinned for an entire library: never orphan-unpinned either. */
	private val libraryPinned = MutableStateFlow(false)

	/** Whether downloads may use mobile data; else they wait for Wi-Fi (an unmetered network). */
	val overCellular: StateFlow<Boolean>
		field = MutableStateFlow(preferenceManager.downloadOverCellular)

	/** Whether downloads may also use a roaming connection (per [preferenceManager.downloadOverRoaming]). */
	val overRoaming: StateFlow<Boolean>
		field = MutableStateFlow(preferenceManager.downloadOverRoaming)

	/** Whether queued downloads may run now. */
	val canRun: StateFlow<Boolean> = combine(
		connectivityManager.isOnline, connectivityManager.isCellular,
		connectivityManager.isRoaming, overCellular, overRoaming
	) { online, cellular, roaming, overCellular, overRoaming ->
		// a metered/roaming connection is usable only when the user allowed it
		online && ((!cellular && !roaming) || overCellular || (roaming && overRoaming))
	}.stateIn(scope, SharingStarted.Eagerly, false)

	/** Whether any download is queued, failed ones aside. */
	val queued: Flow<Boolean> = combine(pinned, failed) { entries, failed ->
		entries.any { !it.complete && it.songId !in failed }
	}.distinctUntilChanged()

	/** One per downloaded or queued song; [DownloadStatus.DOWNLOADED] once a copy is complete. */
	val allDownloads = combine(pinned, progress, failed) { entries, progress, failed ->
		entries.groupBy { it.songId }.map { (id, copies) ->
			when {
				copies.any { it.complete } -> DownloadEntity(id, DownloadStatus.DOWNLOADED, 1f)
				id in failed -> DownloadEntity(id, DownloadStatus.FAILED)
				else -> DownloadEntity(id, DownloadStatus.DOWNLOADING, progress[id] ?: 0f)
			}
		}.toImmutableList()
	}

	/** Songs being downloaded right now (or between attempts), oldest first. */
	val active: Flow<Set<String>> = jobs.map { it.keys }

	private val complete = pinned.map { entries -> entries.filter { it.complete } }

	val downloadCount = complete.map { entries -> entries.distinctBy { it.songId }.size }
	val downloadSize = complete.map { entries -> entries.sumOf { it.bytes } }

	/** Downloaded songs' best files: song id to path. */
	val downloadedSongs: StateFlow<Map<String, String>> = complete.map { entries ->
		entries.groupBy { it.songId }.mapValues { (_, copies) ->
			store.pathOf(copies.maxBy { AudioQuality.parse(it.quality) })
		}
	}.stateIn(scope, SharingStarted.Eagerly, emptyMap())

	/** Songs queued by [downloadEntireLibrary], for its progress; 0 when not downloading it. */
	private val libraryTotal = MutableStateFlow(0)
	private val queuedCount = combine(pinned, failed) { entries, failed ->
		entries.filter { !it.complete && it.songId !in failed }.distinctBy { it.songId }.size
	}

	val isDownloadingLibrary: StateFlow<Boolean> =
		combine(libraryTotal, queuedCount) { total, queued -> total > 0 && queued > 0 }
			.stateIn(scope, SharingStarted.Eagerly, false)

	val libraryDownloadProgress: StateFlow<Float> =
		combine(libraryTotal, queuedCount) { total, queued ->
			if (total == 0) 0f else 1f - queued.coerceAtMost(total).toFloat() / total
		}.stateIn(scope, SharingStarted.Eagerly, 0f)

	init {
		if (store.available) scope.launch {
			// store entries belong to the logged-in server
			sessionManager.serverKey.first { it.isNotEmpty() }
			try {
				importLegacyDownloads()
			} catch (e: Exception) {
				Logger.e(TAG, "importing old downloads failed", e)
			}
			// download declaration, not cache, drives the Downloads screen and orphan-unpin
			try {
				reconcileOrphans()
			} catch (e: Exception) {
				Logger.e(TAG, "reconciling orphan downloads failed", e)
			}
			canRun.collectLatest { if (it) runQueue() }
		}
		// scheduled playlist re-syncs, mirroring SyncManager's cadence
		startScheduledDownloads()
	}

	/** The downloaded file to play for [songId], if any. */
	fun getDownloadedFilePath(songId: String): String? = downloadedSongs.value[songId]

	fun setOverCellular(allowed: Boolean) {
		preferenceManager.downloadOverCellular = allowed
		overCellular.value = allowed
	}

	fun setOverRoaming(allowed: Boolean) {
		preferenceManager.downloadOverRoaming = allowed
		overRoaming.value = allowed
	}

	fun downloadSong(song: DomainSong) {
		scope.launch {
			manualDao.upsert(ManualDownloadEntity(song.id))
			enqueue(listOf(song))
		}
	}

	suspend fun downloadCollection(collection: DomainSongCollection) {
		downloadCollection(collection, collection.collectionType())
	}

	/**
	 * Pins every song of [collection] and records the collection itself as downloaded, so the
	 * Downloads screen lists it and (for playlists) it can re-sync on [startScheduledDownloads].
	 * [type] is what gets stored; an album or artist's collection id is its own id.
	 */
	suspend fun downloadCollection(collection: DomainSongCollection, type: DownloadCollectionType) {
		if (!store.available) return
		collectionDao.upsert(
			DownloadCollectionEntity(
				collectionId = collection.id,
				type = type,
				// an existing schedule survives a plain re-download
				scheduleCron = collectionDao.getById(collection.id)?.scheduleCron,
				scheduleEnabled = collectionDao.getById(collection.id)?.scheduleEnabled ?: false,
				createdAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
			)
		)
		// a song may already be pinned via another collection or manually; pin() is idempotent
		val ids = collection.songs.map { it.id }
		enqueue(collection.songs.filter { it.id !in pinned.value.map { p -> p.songId }.toSet() })
		scope.launch { reconcileOrphans(keep = ids.toSet()) }
	}

	fun downloadEntireLibrary(songs: List<DomainSong>) {
		if (isDownloadingLibrary.value) return
		scope.launch {
			val toDownload = songs.filter { it.id !in downloadedSongs.value }
			libraryTotal.value = toDownload.size
			libraryPinned.value = toDownload.isNotEmpty()
			enqueue(toDownload)
		}
	}

	fun cancelAllActiveDownloads() {
		libraryTotal.value = 0
		libraryPinned.value = false
		scope.launch {
			cancel(jobs.value.keys) {
				store.dropPending(pinned.value.filter { !it.complete }.map { it.songId }.toSet())
			}
		}
	}

	fun cancelDownload(songId: String) {
		scope.launch { cancel(listOf(songId)) { store.dropPending(listOf(songId)) } }
	}

	fun cancelCollectionDownload(collection: DomainSongCollection) {
		val ids = collection.songs.map { it.id }
		scope.launch { cancel(ids) { store.dropPending(ids) } }
	}

	/** Turns the song back into cache, which eviction frees when over the cache limit. */
	fun deleteDownload(songId: String) {
		scope.launch {
			manualDao.delete(songId)
			cancel(listOf(songId)) { store.unpin(listOf(songId)) }
		}
	}

	/** Forgets the collection (and its schedule); its songs become cache again. */
	fun deleteDownloadedCollection(collection: DomainSongCollection) {
		val ids = collection.songs.map { it.id }
		scope.launch {
			collectionDao.delete(collection.id)
			cancel(ids) { store.unpin(ids) }
			reconcileOrphans(keep = emptySet())
		}
	}

	/** Forgets a subscribed collection row but keeps pins (e.g. schedule no longer wanted). */
	suspend fun removeCollectionRow(collectionId: String) {
		collectionDao.delete(collectionId)
	}

	fun setCollectionSchedule(collectionId: String, cron: String?, enabled: Boolean) {
		scope.launch { setCollectionScheduleSuspend(collectionId, cron, enabled) }
	}

	private suspend fun setCollectionScheduleSuspend(collectionId: String, cron: String?, enabled: Boolean) {
		val rec = collectionDao.getById(collectionId) ?: run {
			// only schedule a collection that was actually downloaded; "save Off" on a fresh
			// playlist must not create a subscription row
			if (enabled) collectionDao.upsert(
				DownloadCollectionEntity(collectionId, DownloadCollectionType.PLAYLIST)
					.copy(scheduleCron = cron, scheduleEnabled = enabled)
			)
			return
		}
		collectionDao.upsert(rec.copy(scheduleCron = cron, scheduleEnabled = enabled))
	}

	/** Re-fetches a pinned collection's current songs and pins changes; recollects orphans. */
	fun kickDownload(collectionId: String) {
		scope.launch {
			refreshCollection(collectionId)
			reconcileOrphans()
		}
	}

	/** Re-sync every subscribed collection (the Settings "download now"). */
	fun kickAll() {
		scope.launch {
			for (rec in collectionDao.getAll()) {
				refreshCollection(rec.collectionId)
				reconcileOrphans()
			}
		}
	}

	/** The whole library is treated as its own "collection" for orphan-unpin. */
	fun setLibraryPinned() {
		libraryPinned.value = true
	}

	fun isLibraryPinned(): Boolean = libraryPinned.value

	suspend fun isDownloaded(songId: String): Boolean = songId in store.downloadedIds()

	fun getCollectionDownloadStatus(songIds: List<String>): Flow<DownloadStatus> {
		return allDownloads.map { downloads ->
			val collectionDownloads = downloads.filter { it.songId in songIds }
			when {
				collectionDownloads.isEmpty() -> DownloadStatus.NOT_DOWNLOADED
				collectionDownloads.any { it.status == DownloadStatus.DOWNLOADING } -> DownloadStatus.DOWNLOADING
				collectionDownloads.any { it.status == DownloadStatus.FAILED } -> DownloadStatus.FAILED
				(collectionDownloads.size == songIds.size &&
					collectionDownloads.all { it.status == DownloadStatus.DOWNLOADED })
					-> DownloadStatus.DOWNLOADED

				else -> DownloadStatus.NOT_DOWNLOADED
			}
		}
	}

	fun clearAllDownloads() {
		libraryTotal.value = 0
		libraryPinned.value = false
		scope.launch {
			cancel(jobs.value.keys) { store.unpin(pinned.value.map { it.songId }.toSet()) }
			collectionDao.clearAll()
			manualDao.clearAll()
			Logger.i(TAG, "cleared all downloads")
		}
	}

	/** Gives failed downloads another round of attempts. */
	fun retryFailed() {
		failed.value = emptySet()
	}

	/** Suspends while downloads can still make progress; true if some failed meanwhile. */
	suspend fun awaitIdle(): Boolean {
		combine(pinned, failed, jobs) { entries, failed, jobs ->
			jobs.isEmpty() && entries.none { !it.complete && it.songId !in failed }
		}.first { it }
		return failed.value.isNotEmpty()
	}

	private suspend fun enqueue(songs: List<DomainSong>) {
		if (!store.available || songs.isEmpty()) return
		val quality = downloadQuality()
		val ids = songs.map { it.id }.toSet()
		failed.update { it - ids }
		var queued = false
		for (song in songs) {
			val extension = audioExtension(quality, song.fileExtension)
			queued = store.pin(song.id, quality, extension) || queued
		}
		if (queued && connectivityManager.isOnline.value && !canRun.value) {
			snackBarManager.notify(Res.string.notice_download_waiting_wifi)
		}
	}

	private suspend fun cancel(songIds: Collection<String>, then: suspend () -> Unit) =
		queueMutex.withLock {
			songIds.mapNotNull { jobs.value[it] }.forEach { it.cancelAndJoin() }
			failed.update { it - songIds.toSet() }
			then()
		}

	/** Runs queued downloads, starting new ones as they're queued, until cancelled. */
	private suspend fun runQueue() = coroutineScope {
		// e.g. back on Wi-Fi: worth trying again
		failed.value = emptySet()
		combine(pinned, failed) { _, _ -> }.collect {
			queueMutex.withLock {
				// fresh from the database: a download just cancelled must not start again
				for (entry in store.pendingDownloads()) {
					val id = entry.songId
					if (id in jobs.value || id in failed.value) continue
					val job = launch(start = CoroutineStart.LAZY) {
						try {
							download(entry)
						} finally {
							jobs.update { it - id }
							progress.update { it - id }
						}
					}
					jobs.update { it + (id to job) }
					job.start()
				}
			}
		}
	}

	private suspend fun download(entry: AudioFileEntity) {
		val quality = AudioQuality.parse(entry.quality)
		cacheExtras(entry.songId)
		repeat(MAX_ATTEMPTS) { attempt ->
			if (attempt > 0) delay(RETRY_DELAY * (1 shl (attempt - 1)))
			if (slots.withPermit { fetch(entry.songId, quality) }) return
		}
		Logger.w(TAG, "giving up on downloading ${entry.songId} for now")
		failed.update { it + entry.songId }
	}

	/** One attempt at fetching [songId] at [quality]; true once it's in the store. */
	private suspend fun fetch(songId: String, quality: AudioQuality): Boolean {
		val fetch = fetcher.fetch(songId, quality, audioExtension(quality, null), download = true) {
			sessionManager.api.getStreamUrl(
				id = songId,
				maxBitRate = quality.kbps,
				format = quality.format?.takeIf { it != "default" },
				// if this is true u get "stream was reset: INTERNAL_ERROR" for some reason
				estimateContentLength = false
			)
		} ?: return store.bestComplete(songId)
			?.let { AudioQuality.parse(it.quality) >= quality } == true
		try {
			val result = fetch.progress.first { p ->
				p.total?.takeIf { it > 0 }?.let { total ->
					val percent = (p.bytes * 100 / total) / 100f
					if (progress.value[songId] != percent) {
						progress.update { it + (songId to percent) }
					}
				}
				p.done || p.failed
			}
			// a fetch of another quality was running (playback): try again for ours
			return result.done && fetch.quality == quality
		} catch (e: CancellationException) {
			if (fetch.download) withContext(NonCancellable) { fetch.cancelAndJoin() }
			throw e
		}
	}

	/** The quality to download at, per the network downloads use and the download settings. */
	private fun downloadQuality(): AudioQuality {
		val cellular = connectivityManager.isCellular.value && overCellular.value
		val prefs = preferenceManager
		if (prefs.isAdvancedDownloadTranscodingActive) {
			return AudioQuality.of(
				if (cellular) prefs.customDownloadFormatCellular
				else prefs.customDownloadFormatWifi,
				if (cellular) prefs.customDownloadMaxBitrateCellular
				else prefs.customDownloadMaxBitrateWifi
			)
		}
		val q = if (cellular) prefs.downloadQualityCellular else prefs.downloadQualityWifi
		return when (platformType) {
			PlatformType.Android -> AudioQuality.of(q.containerAndroid, q.bitrateAndroid)
			PlatformType.Desktop -> AudioQuality.of("mp3", q.bitrateIos)
			else -> AudioQuality.of(q.containerIos, q.bitrateIos)
		}
	}

	/**
	 * Moves files downloaded before the [AudioStore] into it, pinned, and queues downloads that
	 * hadn't finished. Their quality wasn't recorded: a file with the song's own suffix is taken
	 * as the original, anything else as the lowest quality of its format. One-time: imported
	 * records are deleted.
	 */
	private suspend fun importLegacyDownloads() {
		val legacy = legacyDao.getAllDownloadsList()
		if (legacy.isEmpty()) return
		Logger.i(TAG, "importing ${legacy.size} old downloads")
		val quality = downloadQuality()
		for (download in legacy) {
			val song = songDao.getSongById(download.songId)
			val path = download.filePath
			if (download.status == DownloadStatus.DOWNLOADED && path != null &&
				SystemFileSystem.exists(Path(path))
			) {
				val ext = path.substringAfterLast('.', "").lowercase()
				val original = song?.fileExtension.equals(ext, ignoreCase = true)
				val stored = if (original) AudioQuality.Raw else AudioQuality(ext, 0)
				store.import(download.songId, stored, path)
			} else {
				store.pin(download.songId, quality, audioExtension(quality, song?.fileExtension))
			}
			legacyDao.deleteDownload(download.songId)
		}
	}

	/** Cover art and lyrics, to show offline; best effort. */
	private suspend fun cacheExtras(songId: String) {
		val song = songDao.getSongById(songId)?.toDomainModel() ?: return
		cacheCoverArt(song.coverArtId)
		cacheAlbumCoverArt(song.albumId)
		cacheLyrics(song)
	}

	private suspend fun cacheCoverArt(coverId: String?) {
		if (coverId == null) return

		Logger.i(TAG, "caching cover art for $coverId")
		// medium for lists/notification, full for Now Playing; decoded small, only the file matters
		for (size in listOf(COVER_ART_MEDIUM, COVER_ART_FULL)) {
			val imageRequest = ImageRequest.Builder(coilPlatformContext)
				.data(CoverArtId(coverId, size))
				.size(COVER_ART_SMALL)
				.diskCachePolicy(CachePolicy.ENABLED)
				.memoryCachePolicy(CachePolicy.DISABLED)
				.build()
			imageLoader.execute(imageRequest)
		}
	}

	private suspend fun cacheAlbumCoverArt(albumId: String?) {
		if (albumId == null) return

		try {
			val albumCoverId = albumDao.getAlbumById(albumId)?.album?.coverArtId
			if (albumCoverId != null) cacheCoverArt(albumCoverId)
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.e(TAG, "Failed to cache album cover art for album $albumId", e)
		}
	}

	private suspend fun cacheLyrics(song: DomainSong) {
		try {
			val lyricsResult = lyricsRepository.fetchLyrics(song)
			if (lyricsResult != null && lyricsResult.rawContent != null) {
				lyricDao.insertLyrics(
					LyricEntity(
						songId = song.id,
						rawContent = lyricsResult.rawContent,
						providerName = lyricsResult.providerName
					)
				)
			}
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.e(TAG, "Failed to cache lyrics for ${song.id}", e)
		}
	}

	/**
	 * Fetches [collectionId]'s current songs from the server, pins ones that appeared and unpins
	 * ones that left (keeping those still wanted by another downloaded collection or by hand).
	 * Best effort on the fetch; pinning is idempotent, so a repeated call just tops things up.
	 */
	suspend fun refreshCollection(collectionId: String) {
		if (!store.available || !sessionManager.isLoggedIn.value) return
		val current: List<DomainSong> = try {
			val album = runCatching { sessionManager.api.getAlbum(collectionId) }.getOrNull()
			if (album != null) {
				val songs = album.songs.map { it.toEntity() }
				songDao.updateSongsByAlbumId(album.id, songs)
				albumDao.insertAlbum(album.toEntity())
				albumDao.getAlbumById(album.id)?.toDomainModel()?.songs ?: emptyList()
			} else {
				dbRepository.syncPlaylistSongs(collectionId).getOrNull()
				playlistDao.getPlaylistById(collectionId)?.songs.orEmpty().map { it.song.toDomainModel() }
			}
		} catch (e: Exception) {
			if (e is CancellationException) throw e
			Logger.w(TAG, "couldn't refresh collection $collectionId", e)
			return
		}
		// pin new songs, unpin gone ones (collection-safe)
		val ids = current.map { it.id }.toSet()
		enqueue(current.filter { it.id !in pinned.value.map { p -> p.songId }.toSet() })
		reconcileOrphans(keep = ids, exceptCollection = collectionId)
	}

	/**
	 * Songs pinned without any current reason get unpinned: they're not in a downloaded
	 * collection's current song set, not downloaded by hand and not part of a whole-library
	 * download. [keep] are ids forced to stay (the caller is downloading them); [exceptCollection]
	 * skips that collection when computing membership (the refresh already handled it).
	 * Unpinning just flips the pin: the file stays as evictable cache (see [AudioStore.unpin]).
	 */
	suspend fun reconcileOrphans(
		keep: Set<String> = emptySet(),
		exceptCollection: String? = null
	) {
		if (!store.available || sessionManager.serverKey.value.isEmpty()) return
		val own = pinned.value.filter { it.server == sessionManager.serverKey.value }
		if (own.isEmpty()) return
		val wanted = mutableSetOf<String>()
		wanted += manualDao.getAllIds().toSet()
		if (libraryPinned.value) wanted += own.map { it.songId }
		val subscribed = collectionDao.getAll()
		for (rec in subscribed) {
			if (rec.collectionId == exceptCollection) continue
			val members = collectionMembers(rec)
			wanted += members
		}
		wanted += keep
		val orphans = own.map { it.songId }.toSet() - wanted
		if (orphans.isNotEmpty()) {
			Logger.i(TAG, "unpinning ${orphans.size} orphan downloads")
			store.unpin(orphans)
		}
	}

	/** The song ids a downloaded collection currently contains, resolved per its type. */
	private suspend fun collectionMembers(rec: DownloadCollectionEntity): Set<String> = try {
		when (rec.type) {
			DownloadCollectionType.ALBUM -> albumDao.getAlbumById(rec.collectionId)?.songs.orEmpty()
				.map { it.songId }.toSet()
			DownloadCollectionType.ARTIST -> songDao.getSongsByArtistId(rec.collectionId)
				.map { it.songId }.toSet()
			DownloadCollectionType.PLAYLIST -> playlistDao.getPlaylistSongIds(rec.collectionId).toSet()
		}
	} catch (e: Exception) {
		if (e is CancellationException) throw e
		Logger.w(TAG, "couldn't resolve members of ${rec.collectionId}", e)
		emptySet()
	}

	/**
	 * Periodic re-sync of schedules enabled playlists (and albums/artists), mirroring
	 * SyncManager.startPeriodicSync's cadence. Re-pins current songs, drops ones removed.
	 */
	fun startScheduledDownloads() {
		scope.launch {
			while (isActive) {
				sessionManager.isLoggedIn.first { it }
				connectivityManager.isOnline.first { it }
				runScheduledCollections()
				delay(SCHEDULE_POLL)
			}
		}
	}

	private suspend fun runScheduledCollections() {
		val nowMillis = kotlin.time.Clock.System.now().toEpochMilliseconds()
		for (rec in collectionDao.getAll()) {
			// opt-in per collection only. The global default is just the prefill in the schedule
			// sheet; applying it here used to silently re-pin + queue every plain download once a
			// global cron was set (the "playlists I never meant to download" reports).
			val cron = rec.scheduleCron?.takeIf { it.isNotBlank() }?.takeIf { rec.scheduleEnabled }
				?: continue
			val schedule = CronSchedule.parse(cron) ?: continue
			val anchor = Clock.System.now().let {
				Instant.fromEpochMilliseconds(rec.lastRunAt.takeIf { it > 0 } ?: rec.createdAt)
			}
			if (schedule.nextRun(anchor) <= nowMillis) {
				refreshCollection(rec.collectionId)
				collectionDao.upsert(rec.copy(lastRunAt = nowMillis))
			}
		}
	}

	private companion object {
		const val TAG = "DownloadManager"
		const val CONCURRENCY = 3
		const val MAX_ATTEMPTS = 5
		val RETRY_DELAY = 30.seconds
		const val SCHEDULE_POLL = 60L * 60L * 1000L // once an hour, like the library sync
	}
}

/** Which [DownloadCollectionType] an album/artist/playlist collection is stored as. */
fun DomainSongCollection.collectionType(): DownloadCollectionType = when (this) {
	is DomainAlbum -> DownloadCollectionType.ALBUM
	is eu.depau.loak.domain.models.DomainPlaylist -> DownloadCollectionType.PLAYLIST
	else -> DownloadCollectionType.ARTIST
}
