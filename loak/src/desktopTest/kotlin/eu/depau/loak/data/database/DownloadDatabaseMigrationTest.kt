package eu.depau.loak.data.database

import androidx.room3.Room
import androidx.sqlite.execSQL
import eu.depau.loak.di.JdbcSQLiteDriver
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DownloadDatabaseMigrationTest {
	/** Builds a v4 DB from its exported schema (still had schedule columns), opens it at v5. */
	@Test
	fun upgradeFrom4DropsScheduleColumns() = runBlocking {
		val dbFile = File.createTempFile("downloads", ".db").apply { delete(); deleteOnExit() }
		val schema = Json.parseToJsonElement(
			File("schemas/eu.depau.loak.data.database.DownloadDatabase/4.json").readText()
		).jsonObject.getValue("database").jsonObject
		val conn = JdbcSQLiteDriver().open(dbFile.path)
		schema.getValue("entities").jsonArray.forEach { e ->
			val table = e.jsonObject.getValue("tableName").jsonPrimitive.content
			val sqls = listOf(e.jsonObject.getValue("createSql")) +
				(e.jsonObject["indices"]?.jsonArray?.map {
					it.jsonObject.getValue("createSql")
				} ?: emptyList())
			sqls.forEach { conn.execSQL(it.jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
		}
		schema.getValue("setupQueries").jsonArray.forEach { conn.execSQL(it.jsonPrimitive.content) }
		// a real v4 row, with the now-removed schedule columns filled in
		conn.execSQL(
			"INSERT INTO DownloadCollectionEntity " +
				"(collectionId, type, scheduleCron, scheduleEnabled, createdAt, lastRunAt) " +
				"VALUES ('p1', 'PLAYLIST', '0 3 * * *', 1, 1, 2)"
		)
		conn.execSQL("PRAGMA user_version = 4")
		conn.close()

		val db = Room.databaseBuilder<DownloadDatabase>(dbFile.path)
			.setDriver(JdbcSQLiteDriver())
			.migrationPolicy(firstMigratedVersion = 3)
			.build()
		val rec = db.downloadCollectionDao().getById("p1")
		db.close()

		// row kept, schedule state dropped cleanly
		assertNotNull(rec)
		assertEquals("PLAYLIST", rec.type.toString())
		assertEquals(1L, rec.createdAt)
	}
}
