package eu.depau.loak.di

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Properties

/**
 * Desktop SQLite driver backed by xerial's `sqlite-jdbc`.
 *
 * `androidx.sqlite:sqlite-bundled-jvm` bundles prebuilt natives only for linux_x64/arm64,
 * osx_arm64, windows_x64 — there is no `windows_arm64` (and `osx_x64` was dropped in 2.7.x),
 * so the desktop app crashed at DB open on Windows on Arm and Intel macOS. xerial ships a
 * native for every desktop arch, so we bridge its JDBC API onto the `androidx.sqlite` driver
 * interfaces Room 3 expects (prepare/step/get*).
 *
 * The connection stays in autocommit mode: Room issues `BEGIN … END TRANSACTION` as plain SQL
 * through prepare()/step(), exactly like `BundledSQLiteDriver`, so JDBC-level transaction
 * nesting would fight it. inTransaction() tracks that SQL so Room's guard sees real state.
 */
internal class JdbcSQLiteDriver : SQLiteDriver {

	override val hasConnectionPool: Boolean = false

	override fun open(fileName: String): SQLiteConnection {
		// busy_timeout keeps concurrent reader connections from immediately failing on a
		// locked writer, mirroring the bundled driver's default busy handler.
		val conn = DriverManager.getConnection(
			"jdbc:sqlite:$fileName",
			Properties().apply { setProperty("busy_timeout", "5000") },
		)
		conn.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
		return JdbcSQLiteConnection(conn)
	}
}

private enum class TransactionOp { BEGIN, END, NONE }

private class JdbcSQLiteConnection(private val conn: Connection) : SQLiteConnection {

	private var closed = false
	// Room drives transactions with raw BEGIN/END/ROLLBACK TRANSACTION SQL (never JDBC's
	// setAutoCommit). xerial's client autoCommit flag does not track that, so we count the
	// transaction SQL it issues to know whether we're inside a transaction.
	private var activeTransactions = 0

	override fun inTransaction(): Boolean {
		checkOpen()
		return activeTransactions > 0
	}

	override fun prepare(sql: String): SQLiteStatement {
		checkOpen()
		// Room issues BEGIN/END/ROLLBACK as plain SQL through prepare()+step(). The count
		// must reflect statements that actually executed (see noteTransaction), so the op is
		// tagged here but applied in step() after a successful execute.
		val op = when {
			sql.trim().startsWith("BEGIN ") -> TransactionOp.BEGIN
			sql.trim() == "END TRANSACTION" || sql.trim() == "ROLLBACK TRANSACTION" -> TransactionOp.END
			else -> TransactionOp.NONE
		}
		// xerial's statement is itself the query executor: execute() runs the SQL, then get*()
		// reads the live ResultSet produced on the same statement object.
		return JdbcSQLiteStatement(conn.prepareStatement(sql), this, op)
	}

	internal fun noteTransaction(op: TransactionOp) {
		when (op) {
			TransactionOp.BEGIN -> activeTransactions++
			TransactionOp.END -> activeTransactions = (activeTransactions - 1).coerceAtLeast(0)
			TransactionOp.NONE -> Unit
		}
	}

	override fun close() {
		if (!closed) {
			closed = true
			conn.close()
		}
	}

	private fun checkOpen() = check(!closed) { "connection is closed" }
}

private class JdbcSQLiteStatement(
	private val stmt: PreparedStatement,
	private val conn: JdbcSQLiteConnection,
	private val transactionOp: TransactionOp,
) : SQLiteStatement {

	private var closed = false
	private var resultSet: ResultSet? = null
	private var executed = false
	// xerial's ResultSetMetaData objects go stale once a statement is executed/reset, so copy
	// column info into plain arrays at prepare and refresh after each execute. Column names
	// never change for a given SQL, so this is safe.
	private var columnCount: Int = runCatching { stmt.metaData.columnCount }.getOrDefault(0)
	private var columnNames: List<String> =
		runCatching { List(columnCount) { i -> stmt.metaData.getColumnName(i + 1) } }.getOrDefault(emptyList())
	private var columnTypes: List<Int> =
		runCatching { List(columnCount) { i -> stmt.metaData.getColumnType(i + 1) } }.getOrDefault(emptyList())

	@get:JvmName("current")
	private val current: ResultSet
		get() = checkNotNull(resultSet) { "no active result set; call step() first" }

	private fun checkOpen() = check(!closed) { "statement is closed" }

	// --- binds ---
	// Room passes sqlite's native convention: bind indices are 1-based (sqlite3_bind_*),
	// which JDBC matches directly — no translation.

	override fun bindBlob(index: Int, value: ByteArray) = checkOpen().also { stmt.setBytes(index, value) }
	override fun bindDouble(index: Int, value: Double) = checkOpen().also { stmt.setDouble(index, value) }
	override fun bindFloat(index: Int, value: Float) = checkOpen().also { stmt.setFloat(index, value) }
	override fun bindLong(index: Int, value: Long) = checkOpen().also { stmt.setLong(index, value) }
	override fun bindInt(index: Int, value: Int) = checkOpen().also { stmt.setInt(index, value) }
	override fun bindBoolean(index: Int, value: Boolean) = checkOpen().also { stmt.setInt(index, if (value) 1 else 0) }
	override fun bindText(index: Int, value: String) = checkOpen().also { stmt.setString(index, value) }
	override fun bindNull(index: Int) = checkOpen().also { stmt.setNull(index, java.sql.Types.NULL) }

	// --- column getters ---
	// getters are 0-based (sqlite3_column_*, Room's columnIndexOf loop returns 0-based);
	// JDBC ResultSet is 1-based, so +1.

	override fun getBlob(index: Int): ByteArray = current.getBytes(index + 1)
	override fun getDouble(index: Int): Double = current.getDouble(index + 1)
	override fun getFloat(index: Int): Float = current.getFloat(index + 1)
	override fun getLong(index: Int): Long = current.getLong(index + 1)
	override fun getInt(index: Int): Int = current.getInt(index + 1)
	override fun getBoolean(index: Int): Boolean = current.getBoolean(index + 1)
	override fun getText(index: Int): String = current.getString(index + 1)
	override fun isNull(index: Int): Boolean = current.getObject(index + 1) == null

	// Room may read column info (count/names/types) before any row is stepped; these come from
	// the plain arrays snapshot at prepare (above).
	override fun getColumnCount(): Int = columnCount
	override fun getColumnName(index: Int): String = if (index < columnNames.size) columnNames[index] else ""

	override fun getColumnNames(): List<String> = columnNames

	override fun getColumnType(index: Int): Int =
		when (columnTypes.getOrNull(index)) {
			java.sql.Types.INTEGER, java.sql.Types.BIGINT, java.sql.Types.SMALLINT,
			java.sql.Types.TINYINT, java.sql.Types.BOOLEAN, java.sql.Types.NUMERIC,
			java.sql.Types.DECIMAL -> SQLITE_DATA_INTEGER
			java.sql.Types.FLOAT, java.sql.Types.DOUBLE, java.sql.Types.REAL -> SQLITE_DATA_FLOAT
			java.sql.Types.VARCHAR, java.sql.Types.CHAR, java.sql.Types.LONGVARCHAR,
			java.sql.Types.CLOB, java.sql.Types.NCHAR, java.sql.Types.NVARCHAR,
			java.sql.Types.LONGNVARCHAR, java.sql.Types.NCLOB, java.sql.Types.DATE,
			java.sql.Types.TIME, java.sql.Types.TIMESTAMP -> SQLITE_DATA_TEXT
			java.sql.Types.BLOB, java.sql.Types.BINARY, java.sql.Types.VARBINARY,
			java.sql.Types.LONGVARBINARY, java.sql.Types.ARRAY -> SQLITE_DATA_BLOB
			else -> SQLITE_DATA_NULL
		}

	// --- execution ---
	//
	// Room's row loops are "prepare once, step() per row": statement.step() == true while a
	// row is available, false once the cursor is exhausted. A JDBC PreparedStatement can't
	// re-execute safely on every step (that would re-run the whole query and always return
	// true → infinite loop), so we execute lazily on the first step() and advance the cached
	// ResultSet on each subsequent one. reset() clears executed so the statement is reusable.

	override fun step(): Boolean {
		checkOpen()
		if (!executed) {
			executed = true
			resultSet?.close()
			if (stmt.execute()) {
				val rs = stmt.resultSet
				resultSet = rs
				refreshColumns(rs)
			} else {
				resultSet = null
			}
			conn.noteTransaction(transactionOp)
			return resultSet?.let { it.next() } ?: false
		}
		return resultSet?.next() ?: false
	}

	override fun reset() = checkOpen().also {
		resultSet?.close()
		resultSet = null
		executed = false
		stmt.clearWarnings()
	}

	private fun refreshColumns(rs: ResultSet) {
		runCatching {
			val n = rs.metaData.columnCount
			columnCount = n
			columnNames = List(n) { i -> rs.metaData.getColumnName(i + 1) }
			columnTypes = List(n) { i -> rs.metaData.getColumnType(i + 1) }
		}
	}

	override fun clearBindings() = checkOpen().also { stmt.clearParameters() }

	override fun close() {
		if (!closed) {
			closed = true
			resultSet?.close()
			stmt.close()
		}
	}
}
