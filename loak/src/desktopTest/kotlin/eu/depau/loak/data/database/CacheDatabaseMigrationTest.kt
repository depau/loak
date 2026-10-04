package eu.depau.loak.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import eu.depau.loak.data.database.entities.AudioFileEntity
import eu.depau.loak.data.database.entities.SyncActionEntity
import eu.depau.loak.data.database.entities.SyncActionType
import eu.depau.loak.data.database.entities.TransferCategory
import eu.depau.loak.domain.manager.hourOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class CacheDatabaseMigrationTest {
	/** Builds a v21 DB from its exported schema, then opens it at the current version. */
	@Test
	fun upgradeFrom21KeepsPendingActions() = runBlocking {
		val dbFile = File.createTempFile("cache", ".db").apply { delete(); deleteOnExit() }
		val schema = Json.parseToJsonElement(
			File("schemas/eu.depau.loak.data.database.CacheDatabase/21.json").readText()
		).jsonObject.getValue("database").jsonObject
		val conn = BundledSQLiteDriver().open(dbFile.path)
		schema.getValue("entities").jsonArray.forEach { e ->
			val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
			val sqls = listOf(e.jsonObject.getValue("createSql")) +
				(e.jsonObject["indices"]?.jsonArray?.map { it.jsonObject.getValue("createSql") }
					?: emptyList())
			sqls.forEach { conn.execSQL(it.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
		}
		schema.getValue("setupQueries").jsonArray.forEach { conn.execSQL(it.jsonPrimitive.content) }
		conn.execSQL("INSERT INTO SyncActionEntity (actionType, itemId) VALUES ('SCROBBLE', 's1')")
		conn.execSQL("PRAGMA user_version = 21")
		conn.close()

		val db = Room.databaseBuilder<CacheDatabase>(dbFile.path)
			.setDriver(BundledSQLiteDriver())
			.migrationPolicy(firstMigratedVersion = 21)
			.build()
		val actions = db.syncActionDao().getPendingActions()

		// v26: playlist edits carry a payload and mark their playlist as pending
		db.syncActionDao().enqueue(
			SyncActionEntity(actionType = SyncActionType.ADD_TO_PLAYLIST, itemId = "p1", payload = "{}")
		)
		val pendingPlaylists = db.syncActionDao().pendingPlaylistIds()

		// tables added by later migrations work: hourly buckets add up, old ones get pruned
		val stats = db.networkStatsDao()
		val hour = hourOf(Instant.parse("2026-10-04T13:37:00Z"))
		assertEquals(Instant.parse("2026-10-04T13:00:00Z").toEpochMilliseconds(), hour)
		stats.add(hour, true, TransferCategory.STREAM, 100, 1)
		stats.add(hour, true, TransferCategory.STREAM, 50, 1)
		stats.add(hour - 1.hours.inWholeMilliseconds, true, TransferCategory.STREAM, 7, 1)
		stats.pruneTransfers(hour)
		val buckets = stats.getSince(0)

		val audio = AudioFileEntity("s1", "raw", "s1.raw.flac", 10, null, true, true, 1, 1)
		db.audioFileDao().upsert(audio)
		val audioFiles = db.audioFileDao().getAll()
		db.close()

		assertEquals(
			listOf(SyncActionType.SCROBBLE to "s1"),
			actions.map { it.actionType to it.itemId }
		)
		assertEquals(listOf<String?>(null), actions.map { it.payload })
		assertEquals(listOf("p1"), pendingPlaylists)
		assertEquals(listOf(150L to 2L), buckets.map { it.bytes to it.requests })
		assertEquals(listOf(audio), audioFiles)
	}
}
