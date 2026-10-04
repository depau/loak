package eu.depau.loak.domain.manager

import eu.depau.loak.data.database.dao.AudioFileDao
import eu.depau.loak.data.database.entities.AudioFileEntity
import eu.depau.loak.domain.models.AudioQuality
import eu.depau.loak.domain.models.settings.AudioCacheLimit
import eu.depau.loak.util.IoDispatcher
import eu.depau.loak.util.Logger
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Clock

/** Bytes free on the volume holding [dir], or null where the platform can't tell. */
internal expect fun freeSpace(dir: String): Long?

/** Eviction keeps at least this much space free on the device. */
private const val MIN_FREE_BYTES = 500L * 1024 * 1024

/** Complete files in the store, split into evictable cache and pinned downloads. */
data class AudioStoreUsage(
	val cacheBytes: Long = 0,
	val cacheCount: Int = 0,
	val pinnedBytes: Long = 0,
	val pinnedCount: Int = 0
)

/**
 * Audio files on disk, one per song and [AudioQuality], indexed in [AudioFileDao]. Streamed songs
 * land here as an LRU cache; downloads are the same files, pinned, which eviction never touches.
 * [dir] is null where there is no file system (web): [available] is false and it all no-ops.
 */
@OptIn(ExperimentalAtomicApi::class)
class AudioStore(
	private val dir: String?,
	private val dao: AudioFileDao,
	private val preferenceManager: PreferenceManager
) {
	val available = dir != null

	private val scope = CoroutineScope(SupervisorJob() + IoDispatcher)

	// guards index mutations and file renames/deletes
	private val mutex = Mutex()
	private val writing = mutableSetOf<Pair<String, String>>()
	private val inUse = AtomicReference(emptyMap<String, Int>())

	val usage: Flow<AudioStoreUsage> = if (dir == null) emptyFlow() else
		dao.observeAll().map { entries ->
			val (pinned, cache) = entries.filter { it.complete }.partition { it.pinned }
			AudioStoreUsage(
				cache.sumOf { it.bytes }, cache.size, pinned.sumOf { it.bytes }, pinned.size
			)
		}

	init {
		if (dir != null) scope.launch {
			try {
				sweep(dir)
				trim()
			} catch (e: Exception) {
				Logger.e(TAG, "startup sweep failed", e)
			}
		}
	}

	/** The highest-quality complete file for [songId], to play or share instead of streaming. */
	suspend fun bestComplete(songId: String): AudioFileEntity? {
		if (dir == null) return null
		return dao.getForSong(songId).filter { it.complete }
			.maxByOrNull { AudioQuality.parse(it.quality) }
	}

	fun pathOf(entry: AudioFileEntity): String = Path(dir!!, entry.fileName).toString()

	/** Records a playback from the store, for LRU eviction. */
	suspend fun touch(entry: AudioFileEntity) {
		if (dir != null) dao.touch(entry.songId, entry.quality, now())
	}

	/** Protects [songId]'s files from eviction while it's open (e.g. queued in the player). */
	fun retain(songId: String) = updateInUse { it + (songId to (it[songId] ?: 0) + 1) }

	fun release(songId: String) = updateInUse {
		val n = (it[songId] ?: 1) - 1
		if (n <= 0) it - songId else it + (songId to n)
	}

	/**
	 * Starts writing [songId] at [quality] to a `.partial` file. Returns null when there is nothing
	 * to write: no store, caching disabled (unless [pinned]), the file is already complete (it gets
	 * pinned if asked) or already being written. With [resume], an existing partial file is kept:
	 * request `Range: bytes=<offset>-` and [Writer.truncate] if the server ignores it.
	 */
	suspend fun openWrite(
		songId: String,
		quality: AudioQuality,
		extension: String,
		pinned: Boolean = false,
		expectedBytes: Long? = null,
		resume: Boolean = false
	): Writer? {
		if (dir == null || (!pinned && !preferenceManager.audioCacheEnabled)) return null
		val key = songId to quality.key
		return mutex.withLock {
			val existing = dao.get(songId, quality.key)
			if (existing?.complete == true) {
				if (pinned && !existing.pinned) dao.setPinned(songId, quality.key, true)
				return null
			}
			if (key in writing) return null
			SystemFileSystem.createDirectories(Path(dir))
			val name = existing?.fileName ?: fileName(songId, quality, extension)
			val partial = Path(dir, "$name$PARTIAL")
			if (!resume) SystemFileSystem.delete(partial, mustExist = false)
			val offset = SystemFileSystem.metadataOrNull(partial)?.size ?: 0
			val now = now()
			val entry = AudioFileEntity(
				songId = songId,
				quality = quality.key,
				fileName = name,
				bytes = offset,
				expectedBytes = expectedBytes ?: existing?.expectedBytes,
				complete = false,
				pinned = pinned || existing?.pinned == true,
				lastAccessed = now,
				created = existing?.created ?: now
			)
			dao.upsert(entry)
			writing += key
			retain(songId)
			Writer(entry, partial, offset)
		}
	}

	/**
	 * Marks [songId] as downloaded at [quality] or better. Pins its best complete file, which stays
	 * playable meanwhile, and returns true when that is worse than [quality] (or missing): then
	 * fetch [quality] with `openWrite(pinned = true)`; completing it deletes the worse files.
	 */
	suspend fun pin(songId: String, quality: AudioQuality): Boolean {
		if (dir == null) return false
		val best = mutex.withLock {
			bestComplete(songId)?.also { dao.setPinned(it.songId, it.quality, true) }
		}
		return best == null || AudioQuality.parse(best.quality) < quality
	}

	/** Turns [songId]'s files back into evictable cache. */
	suspend fun unpin(songId: String) {
		if (dir == null) return
		mutex.withLock { dao.unpin(songId) }
		trim()
	}

	/** Deletes every file of [songId], pinned or not. */
	suspend fun remove(songId: String) {
		if (dir == null) return
		mutex.withLock { delete(dir, dao.getForSong(songId)) }
	}

	/** Deletes all unpinned files except those in use. */
	suspend fun clearCache() {
		if (dir == null) return
		mutex.withLock {
			val inUse = inUse.load()
			delete(dir, dao.getAll().filter { !it.pinned && it.songId !in inUse })
		}
	}

	/** Evicts per the cache settings; call after changing them. */
	suspend fun trim() {
		if (dir == null) return
		mutex.withLock {
			val entries = dao.getAll()
			val enabled = preferenceManager.audioCacheEnabled
			val bySize = preferenceManager.audioCacheLimit == AudioCacheLimit.Size
			var maxBytes = if (!enabled) 0 else if (bySize) preferenceManager.audioCacheMaxBytes
			else Long.MAX_VALUE
			val maxCount = if (!enabled) 0 else if (bySize) Int.MAX_VALUE
			else preferenceManager.audioCacheMaxSongs
			freeSpace(dir)?.let { free ->
				val cached = entries.filter { it.complete && !it.pinned }.sumOf { it.bytes }
				maxBytes = minOf(maxBytes, cached + free - MIN_FREE_BYTES)
			}
			delete(dir, evictions(entries, maxBytes, maxCount, inUse.load().keys))
		}
	}

	/** One file being written; use from one thread at a time. */
	inner class Writer internal constructor(
		val entry: AudioFileEntity,
		private val partial: Path,
		offset: Long
	) {
		/** Bytes in the partial file: where a resumed request starts. */
		var offset = offset
			private set
		private var sink: Sink? = SystemFileSystem.sink(partial, append = true).buffered()

		fun write(bytes: ByteArray, start: Int = 0, end: Int = bytes.size) {
			sink!!.write(bytes, start, end)
			offset += end - start
		}

		/** Starts over from zero, e.g. when the server answered a Range request in full. */
		fun truncate() {
			sink?.close()
			sink = SystemFileSystem.sink(partial).buffered()
			offset = 0
		}

		/**
		 * Publishes the file and evicts as needed. Returns null, deleting the file, when the
		 * expected length doesn't match or the entry was removed meanwhile.
		 */
		suspend fun complete(): AudioFileEntity? {
			sink?.close()
			sink = null
			val expected = entry.expectedBytes
			if (expected != null && expected != offset) {
				Logger.w(TAG, "${entry.songId}: got $offset of $expected bytes, dropping")
				abandon()
				return null
			}
			val done = mutex.withLock {
				writing -= entry.songId to entry.quality
				val current = dao.get(entry.songId, entry.quality)
				if (current == null) {
					SystemFileSystem.delete(partial, mustExist = false)
					return@withLock null
				}
				SystemFileSystem.atomicMove(partial, Path(dir!!, entry.fileName))
				current.copy(bytes = offset, complete = true, lastAccessed = now())
					.also { dao.upsert(it) }
			}
			release(entry.songId)
			if (done != null) trim()
			return done
		}

		/** Stops, keeping the partial file to resume later (only pinned ones survive restarts). */
		suspend fun close() {
			sink?.close()
			sink = null
			mutex.withLock { writing -= entry.songId to entry.quality }
			release(entry.songId)
		}

		/** Stops and deletes the partial file, e.g. a transcode of unknown length broke midway. */
		suspend fun abandon() {
			sink?.close()
			sink = null
			mutex.withLock {
				writing -= entry.songId to entry.quality
				SystemFileSystem.delete(partial, mustExist = false)
				dao.get(entry.songId, entry.quality)?.takeIf { !it.complete }
					?.let { dao.delete(listOf(it)) }
			}
			release(entry.songId)
		}
	}

	/**
	 * Drops what a crash or a wiped database left behind: rows without a file, unpinned partials
	 * (nobody resumes them), and files the index doesn't know.
	 */
	private suspend fun sweep(dir: String) = mutex.withLock {
		SystemFileSystem.createDirectories(Path(dir))
		val inUse = inUse.load()
		val names = SystemFileSystem.list(Path(dir)).map { it.name }.toSet()
		val (keep, drop) = dao.getAll().partition { e ->
			e.songId in inUse || if (e.complete) e.fileName in names else e.pinned
		}
		delete(dir, drop)
		val known = keep.flatMap { listOf(it.fileName, it.fileName + PARTIAL) }.toSet()
		(names - known).forEach { SystemFileSystem.delete(Path(dir, it), mustExist = false) }
	}

	private suspend fun delete(dir: String, entries: List<AudioFileEntity>) {
		if (entries.isEmpty()) return
		for (e in entries) {
			SystemFileSystem.delete(Path(dir, e.fileName), mustExist = false)
			SystemFileSystem.delete(Path(dir, e.fileName + PARTIAL), mustExist = false)
		}
		dao.delete(entries)
		Logger.i(TAG, "deleted ${entries.size} audio files")
	}

	private inline fun updateInUse(f: (Map<String, Int>) -> Map<String, Int>) {
		while (true) {
			val old = inUse.load()
			if (inUse.compareAndSet(old, f(old))) return
		}
	}

	private companion object {
		const val TAG = "AudioStore"
		const val PARTIAL = ".partial"

		fun now() = Clock.System.now().toEpochMilliseconds()

		// URL-encoding keeps any song id a single, unique, portable file name
		fun fileName(songId: String, quality: AudioQuality, extension: String) =
			"${songId.encodeURLParameter()}.${quality.key.encodeURLParameter()}." +
				extension.filter { it.isLetterOrDigit() }.ifEmpty { "bin" }
	}
}

/**
 * Entries to delete: lower-quality copies made redundant by a better complete one (an unpinned
 * copy by any, a pinned one only by a pinned one), then the least recently played unpinned ones
 * until the cache fits [maxBytes] and [maxCount]. Songs in [inUse] and partial files are kept.
 */
internal fun evictions(
	entries: List<AudioFileEntity>,
	maxBytes: Long,
	maxCount: Int,
	inUse: Set<String>
): List<AudioFileEntity> {
	val complete = entries.filter { it.complete }
	val redundant = complete.groupBy { it.songId }.flatMap { (songId, copies) ->
		if (songId in inUse) return@flatMap emptyList()
		copies.filter { e ->
			val q = AudioQuality.parse(e.quality)
			copies.any { AudioQuality.parse(it.quality) > q && (it.pinned || !e.pinned) }
		}
	}.toSet()
	val cache = complete.filter { !it.pinned && it !in redundant }
	var bytes = cache.sumOf { it.bytes }
	var count = cache.size
	val evicted = mutableListOf<AudioFileEntity>()
	for (e in cache.filter { it.songId !in inUse }.sortedBy { it.lastAccessed }) {
		if (bytes <= maxBytes && count <= maxCount) break
		evicted += e
		bytes -= e.bytes
		count--
	}
	return redundant.toList() + evicted
}
