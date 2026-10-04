package eu.depau.loak.data.database

import androidx.room3.AutoMigration
import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.DeleteTable
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.AutoMigrationSpec
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.GenreDao
import eu.depau.loak.data.database.dao.NetworkStatsDao
import eu.depau.loak.data.database.dao.LyricDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.RadioDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.dao.SyncActionDao
import eu.depau.loak.data.database.entities.AlbumEntity
import eu.depau.loak.data.database.entities.ArtistEntity
import eu.depau.loak.data.database.entities.GenreEntity
import eu.depau.loak.data.database.entities.LyricEntity
import eu.depau.loak.data.database.entities.NetworkStatsEntity
import eu.depau.loak.data.database.entities.PlaylistEntity
import eu.depau.loak.data.database.entities.PlaylistSongCrossRef
import eu.depau.loak.data.database.entities.RadioEntity
import eu.depau.loak.data.database.entities.SongEntity
import eu.depau.loak.data.database.entities.SyncActionEntity
import eu.depau.loak.data.database.entities.SyncRunEntity

@Database(
	version = 23,
	entities = [
		AlbumEntity::class,
		GenreEntity::class,
		PlaylistEntity::class,
		PlaylistSongCrossRef::class,
		SongEntity::class,
		ArtistEntity::class,
		RadioEntity::class,
		LyricEntity::class,
		SyncActionEntity::class,
		NetworkStatsEntity::class,
		SyncRunEntity::class
	],
	autoMigrations = [
		AutoMigration(from = 21, to = 22, spec = CacheDatabase.DropDownloads::class),
		AutoMigration(from = 22, to = 23)
	]
)
@ColumnTypeConverters(Converters::class)
@ConstructedBy(CacheDatabaseConstructor::class)
abstract class CacheDatabase : RoomDatabase() {
	abstract fun albumDao(): AlbumDao
	abstract fun genreDao(): GenreDao
	abstract fun playlistDao(): PlaylistDao
	abstract fun songDao(): SongDao
	abstract fun artistDao(): ArtistDao
	abstract fun radioDao(): RadioDao
	abstract fun lyricDao(): LyricDao
	abstract fun syncActionDao(): SyncActionDao
	abstract fun networkStatsDao(): NetworkStatsDao

	// Unused leftover; downloads live in DownloadDatabase.
	@DeleteTable(tableName = "DownloadEntity")
	class DropDownloads : AutoMigrationSpec
}

@Suppress("KotlinNoActualForExpect")
expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
	override fun initialize(): CacheDatabase
}
