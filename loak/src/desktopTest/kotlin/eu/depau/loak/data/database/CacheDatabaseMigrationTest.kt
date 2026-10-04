package eu.depau.loak.data.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import eu.depau.loak.data.database.entities.SyncActionType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

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
		db.close()
		assertEquals(
			listOf(SyncActionType.SCROBBLE to "s1"),
			actions.map { it.actionType to it.itemId }
		)
	}
}
