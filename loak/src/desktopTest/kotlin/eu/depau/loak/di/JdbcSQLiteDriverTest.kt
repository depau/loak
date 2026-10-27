package eu.depau.loak.di

import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.execSQL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JdbcSQLiteDriverTest {
	@Test
	fun columnTypeIsThatOfTheRowsValue() {
		val connection = JdbcSQLiteDriver().open(":memory:")
		connection.execSQL("CREATE TABLE t (id INTEGER, name TEXT)")
		connection.execSQL("INSERT INTO t VALUES (1, NULL), (2, 'x')")
		connection.prepare("SELECT id, name FROM t ORDER BY id").use { statement ->
			assertTrue(statement.step())
			assertEquals(SQLITE_DATA_INTEGER, statement.getColumnType(0))
			assertEquals(SQLITE_DATA_NULL, statement.getColumnType(1))
			assertTrue(statement.step())
			assertEquals(SQLITE_DATA_TEXT, statement.getColumnType(1))
			assertEquals("x", statement.getText(1))
		}
		connection.close()
	}
}
