package eu.depau.loak.data.database

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import eu.depau.loak.data.database.dao.AlbumDao
import eu.depau.loak.data.database.dao.ArtistDao
import eu.depau.loak.data.database.dao.DownloadDao
import eu.depau.loak.data.database.dao.GenreDao
import eu.depau.loak.data.database.dao.LyricDao
import eu.depau.loak.data.database.dao.PlaylistDao
import eu.depau.loak.data.database.dao.RadioDao
import eu.depau.loak.data.database.dao.SongDao
import eu.depau.loak.data.database.dao.SyncActionDao
import eu.depau.loak.data.database.entities.AlbumEntity
import eu.depau.loak.data.database.entities.ArtistEntity
import eu.depau.loak.data.database.entities.DownloadEntity
import eu.depau.loak.data.database.entities.GenreEntity
import eu.depau.loak.data.database.entities.LyricEntity
import eu.depau.loak.data.database.entities.PlaylistEntity
import eu.depau.loak.data.database.entities.PlaylistSongCrossRef
import eu.depau.loak.data.database.entities.RadioEntity
import eu.depau.loak.data.database.entities.SongEntity
import eu.depau.loak.data.database.entities.SyncActionEntity

@Database(
	version = 21,
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
		DownloadEntity::class
	]
)
@ColumnTypeConverters(Converters::class)
@ConstructedBy(CacheDatabaseConstructor::class)
abstract class CacheDatabase : RoomDatabase() {
	abstract fun albumDao(): AlbumDao
	abstract fun genreDao(): GenreDao
	abstract fun downloadDao(): DownloadDao
	abstract fun playlistDao(): PlaylistDao
	abstract fun songDao(): SongDao
	abstract fun artistDao(): ArtistDao
	abstract fun radioDao(): RadioDao
	abstract fun lyricDao(): LyricDao
	abstract fun syncActionDao(): SyncActionDao
}

@Suppress("KotlinNoActualForExpect")
expect object CacheDatabaseConstructor : RoomDatabaseConstructor<CacheDatabase> {
	override fun initialize(): CacheDatabase
}
