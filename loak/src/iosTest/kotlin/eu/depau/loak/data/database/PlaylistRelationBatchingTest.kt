package eu.depau.loak.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import eu.depau.loak.data.database.entities.PlaylistEntity
import eu.depau.loak.data.database.entities.PlaylistSongCrossRef
import eu.depau.loak.data.database.entities.SongEntity
import eu.depau.loak.domain.models.DomainExplicitStatus
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Playlists with more songs than SQLite takes in one `IN (...)` clause (Room batches at 999 keys).
 *
 * Room's single-valued relation fetch re-puts every batch into the map it is iterating, which on
 * Kotlin/Native invalidates the iterator for *some* key counts only — a playlist of exactly 1000,
 * 1532, 2000 or 5000 songs (all listed below) died with `ConcurrentModificationException` before
 * `PlaylistSong.songs` became a collection; 999, 1200 and 3000 squeaked through. Android runs the
 * same generated DAO code but never crashes, so this test only runs on iOS. Sizes are spread on
 * purpose: the crashing counts follow Kotlin/Native's hash-table growth, so a single size could
 * become harmless after a stdlib bump and stop guarding anything.
 */
class PlaylistRelationBatchingTest {
	private val songCounts = listOf(999, 1000, 1200, 1532, 2000, 5000)

	private fun song(id: String) = SongEntity(
		songId = id,
		title = id,
		artistName = null,
		artistId = "artist",
		albumTitle = null,
		belongsToAlbumId = null,
		parentId = null,
		comment = null,
		trackNumber = null,
		discNumber = null,
		isrc = emptyList(),
		year = null,
		genre = null,
		genres = emptyList(),
		moods = emptyList(),
		duration = 1.seconds,
		bpm = null,
		contributors = emptyList(),
		userRating = null,
		averageRating = null,
		bitRate = null,
		bitDepth = null,
		sampleRate = null,
		audioChannelCount = null,
		replayGain = null,
		fileSize = 0,
		fileExtension = null,
		mimeType = null,
		filePath = null,
		starredAt = null,
		coverArtId = null,
		musicBrainzId = null,
		explicitStatus = DomainExplicitStatus.Unknown,
		artists = emptyList(),
		albumArtists = emptyList(),
		isExternal = false
	)

	@Test
	fun listsPlaylistsBiggerThanTheQueryBatchSize() = runBlocking {
		for (songCount in songCounts) {
			val db = Room.inMemoryDatabaseBuilder<CacheDatabase>()
				.setDriver(BundledSQLiteDriver())
				.build()
			try {
				val ids = (0 until songCount).map { "song-$it" }
				db.songDao().insertSongsIgnoringConflicts(ids.map { song(it) })
				db.playlistDao().insertPlaylist(
					PlaylistEntity(
						playlistId = "playlist",
						name = "Big",
						comment = null,
						owner = null,
						coverArtId = null,
						songCount = songCount,
						duration = songCount.seconds,
						public = null,
						readOnly = null,
						createdAt = Instant.fromEpochSeconds(0),
						modifiedAt = Instant.fromEpochSeconds(0),
						validUntil = null,
						allowedUsers = emptyList()
					)
				)
				db.playlistDao().replacePlaylistSongs(
					"playlist",
					ids.mapIndexed { i, id -> PlaylistSongCrossRef("playlist", id, i) }
				)

				val playlist = db.playlistDao().getAllPlaylistsByName().single()
				assertEquals(
					ids,
					playlist.songs.sortedBy { it.crossRef.position }.map { it.songs.first().songId },
					"playlist of $songCount songs"
				)
			} finally {
				db.close()
			}
		}
	}
}
