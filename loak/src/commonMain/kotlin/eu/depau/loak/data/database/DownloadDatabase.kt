package eu.depau.loak.data.database

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.DeleteColumn
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.AutoMigrationSpec
import eu.depau.loak.data.database.dao.DownloadCollectionDao
import eu.depau.loak.data.database.dao.DownloadDao
import eu.depau.loak.data.database.dao.ManualDownloadDao
import eu.depau.loak.data.database.entities.DownloadCollectionEntity
import eu.depau.loak.data.database.entities.DownloadEntity
import eu.depau.loak.data.database.entities.ManualDownloadEntity

@Database(
	version = 5,
	entities = [DownloadEntity::class, DownloadCollectionEntity::class, ManualDownloadEntity::class],
	autoMigrations = [
		AutoMigration(from = 3, to = 4),
		AutoMigration(
			from = 4,
			to = 5,
			spec = DropScheduleColumns::class
		)
	]
)
@ConstructedBy(DownloadDatabaseConstructor::class)
abstract class DownloadDatabase : RoomDatabase() {
	abstract fun downloadDao(): DownloadDao
	abstract fun downloadCollectionDao(): DownloadCollectionDao
	abstract fun manualDownloadDao(): ManualDownloadDao
}

/** v4's per-collection cron schedule, removed when schedules were dropped. */
@DeleteColumn(tableName = "DownloadCollectionEntity", columnName = "scheduleCron")
@DeleteColumn(tableName = "DownloadCollectionEntity", columnName = "scheduleEnabled")
@DeleteColumn(tableName = "DownloadCollectionEntity", columnName = "lastRunAt")
class DropScheduleColumns : AutoMigrationSpec

@Suppress("KotlinNoActualForExpect")
expect object DownloadDatabaseConstructor : RoomDatabaseConstructor<DownloadDatabase> {
	override fun initialize(): DownloadDatabase
}
